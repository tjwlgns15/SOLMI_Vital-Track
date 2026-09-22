/**
 * 측정 이력 재생 화면.
 * - PlaybackData: 서버에서 받은 기록을 시간순 타임라인으로 정리만 담당
 * - PlaybackView: 지도/ECG/가속도(x·y·z)/속도 렌더링만 담당
 * - PlaybackPlayer: 재생/일시정지/배속/탐색(seek) 조율만 담당
 */
(function () {
	"use strict";

	// ECG/가속도 둘 다 세션마다 실제 samplingRateHz가 다를 수 있어(가속도는 시뮬레이터 50Hz, 실제 앱
	// 테스트 250Hz로 이미 확인됨), 버퍼를 "샘플 개수" 고정이 아니라 "몇 초를 보여줄지" 기준으로 잡는다.
	// (대시보드 dashboard.js와 동일한 정책 - PlaybackData 생성 시 세션의 실제 samplingRateHz로
	// ecgBufferSize/accelBufferSize를 한 번 계산해서 재생 내내 사용한다.)
	var ECG_WINDOW_SECONDS = 4;
	var ECG_DEFAULT_SAMPLING_HZ = 250; // ECG 기록이 하나도 없어 rate를 알 수 없을 때의 기본값
	var ACCEL_WINDOW_SECONDS = 4;
	var ACCEL_DEFAULT_SAMPLING_HZ = 50; // 가속도 기록이 하나도 없어 rate를 알 수 없을 때의 기본값
	// 대시보드와 동일한 배색을 사용해, 어느 화면에서 보든 x/y/z 색이 항상 같게 유지한다.
	var ACCEL_AXIS_COLORS = {x: "#3987e5", y: "#d95926", z: "#199e70"};

	// y축 고정 범위도 대시보드와 동일하게 유지한다 (auto-scale 대신 채널별 고정값을 사용).
	var ECG_MIN = -1.0, ECG_MAX = 2.0;
	var ACCEL_X_MIN = -1.5, ACCEL_X_MAX = 1.5;
	var ACCEL_Y_MIN = -1.5, ACCEL_Y_MAX = 1.5;
	var ACCEL_Z_MIN = 8.5, ACCEL_Z_MAX = 11.1;
	var TICK_MS = 100;

	function formatTime(ms) {
		var totalSec = Math.floor(ms / 1000);
		var m = Math.floor(totalSec / 60);
		var s = totalSec % 60;
		return (m < 10 ? "0" : "") + m + ":" + (s < 10 ? "0" : "") + s;
	}

	/**
	 * 버퍼를 정규화해 canvas에 선 그래프로 그린다. ECG/가속도 x/y/z 차트가 공통으로 사용한다.
	 * min/max는 버퍼에서 그때그때 계산하지 않고 채널별 고정값을 받는다 (auto-scale 방지).
	 */
	function drawLineChart(canvas, buffer, bufferSize, color, min, max) {
		var ctx = canvas.getContext("2d");
		var w = canvas.width;
		var h = canvas.height;
		ctx.clearRect(0, 0, w, h);
		if (buffer.length < 2) {
			return;
		}
		var range = (max - min) || 1;
		var step = w / (bufferSize - 1);
		var offset = bufferSize - buffer.length;

		ctx.strokeStyle = color;
		ctx.lineWidth = 1.5;
		ctx.beginPath();
		for (var i = 0; i < buffer.length; i++) {
			var x = (offset + i) * step;
			var normalized = (buffer[i] - min) / range;
			var y = h - normalized * (h - 10) - 5;
			if (i === 0) {
				ctx.moveTo(x, y);
			} else {
				ctx.lineTo(x, y);
			}
		}
		ctx.stroke();
	}

	function pluckX(sample) {
		return sample.x;
	}

	function pluckY(sample) {
		return sample.y;
	}

	function pluckZ(sample) {
		return sample.z;
	}

	/** 서버 응답을 재생하기 쉬운 형태로만 가공한다 */
	function PlaybackData(raw) {
		this.session = raw.session;
		this.locations = raw.locations.slice().sort(byOffset);
		this.ecgBatches = raw.ecgBatches.slice().sort(byOffset);
		this.accelerations = raw.accelerations.slice().sort(byOffset);
		this.velocities = raw.velocities.slice().sort(byOffset);

		// ECG/가속도 배치들의 samplingRateHz는 한 세션 안에서 항상 같다고 가정하고(같은 기기가 보낸 기록),
		// 첫 배치의 값으로 버퍼 크기를 한 번만 계산해 재생 내내 사용한다.
		var ecgSamplingRateHz = this.ecgBatches.length > 0
			? this.ecgBatches[0].samplingRateHz
			: ECG_DEFAULT_SAMPLING_HZ;
		this.ecgBufferSize = Math.round((ecgSamplingRateHz || ECG_DEFAULT_SAMPLING_HZ) * ECG_WINDOW_SECONDS);

		var accelSamplingRateHz = this.accelerations.length > 0
			? this.accelerations[0].samplingRateHz
			: ACCEL_DEFAULT_SAMPLING_HZ;
		this.accelBufferSize = Math.round((accelSamplingRateHz || ACCEL_DEFAULT_SAMPLING_HZ) * ACCEL_WINDOW_SECONDS);

		var last = 0;
		[this.locations, this.ecgBatches, this.accelerations, this.velocities].forEach(function (list) {
			if (list.length > 0) {
				last = Math.max(last, list[list.length - 1].offsetMs);
			}
		});
		this.durationMs = Math.max(last, (this.session.durationSeconds || 0) * 1000);
	}

	function byOffset(a, b) {
		return a.offsetMs - b.offsetMs;
	}

	/** 특정 시점(timeMs)까지의 상태(마커 위치, 가속도 x/y/z 버퍼, 속도, ECG 버퍼)를 계산한다 */
	PlaybackData.prototype.stateAt = function (timeMs) {
		var lastLocation = null;
		for (var i = 0; i < this.locations.length; i++) {
			if (this.locations[i].offsetMs > timeMs) {
				break;
			}
			lastLocation = this.locations[i];
		}

		var accelBufferX = [];
		var accelBufferY = [];
		var accelBufferZ = [];
		for (var a = 0; a < this.accelerations.length; a++) {
			if (this.accelerations[a].offsetMs > timeMs) {
				break;
			}
			var samples = this.accelerations[a].samples;
			accelBufferX = accelBufferX.concat(samples.map(pluckX));
			accelBufferY = accelBufferY.concat(samples.map(pluckY));
			accelBufferZ = accelBufferZ.concat(samples.map(pluckZ));
		}
		if (accelBufferX.length > this.accelBufferSize) {
			accelBufferX = accelBufferX.slice(accelBufferX.length - this.accelBufferSize);
			accelBufferY = accelBufferY.slice(accelBufferY.length - this.accelBufferSize);
			accelBufferZ = accelBufferZ.slice(accelBufferZ.length - this.accelBufferSize);
		}

		var lastVelocity = null;
		for (var v = 0; v < this.velocities.length; v++) {
			if (this.velocities[v].offsetMs > timeMs) {
				break;
			}
			lastVelocity = this.velocities[v];
		}

		var ecgBuffer = [];
		for (var e = 0; e < this.ecgBatches.length; e++) {
			if (this.ecgBatches[e].offsetMs > timeMs) {
				break;
			}
			ecgBuffer = ecgBuffer.concat(this.ecgBatches[e].samples);
		}
		if (ecgBuffer.length > this.ecgBufferSize) {
			ecgBuffer = ecgBuffer.slice(ecgBuffer.length - this.ecgBufferSize);
		}

		return {
			location: lastLocation,
			accelBufferX: accelBufferX,
			accelBufferY: accelBufferY,
			accelBufferZ: accelBufferZ,
			accelBufferSize: this.accelBufferSize,
			velocity: lastVelocity,
			ecgBuffer: ecgBuffer,
			ecgBufferSize: this.ecgBufferSize
		};
	};

	/** 지도/ECG/가속도(x·y·z)/속도 화면 렌더링만 담당 */
	var PlaybackView = {
		map: null,
		marker: null,
		path: null,
		pathPoints: [],

		init: function (subjectName) {
			this.map = L.map("map").setView([37.5665, 126.9780], 15);
			L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
				attribution: "&copy; OpenStreetMap contributors",
				maxZoom: 19
			}).addTo(this.map);
			this.subjectName = subjectName;
			this.ecgCanvas = document.getElementById("ecg-canvas");
			this.accelCanvasX = document.getElementById("accel-canvas-x");
			this.accelCanvasY = document.getElementById("accel-canvas-y");
			this.accelCanvasZ = document.getElementById("accel-canvas-z");
		},

		drawFullPath: function (locations) {
			if (locations.length === 0) {
				return;
			}
			var latlngs = locations.map(function (l) {
				return [l.latitude, l.longitude];
			});
			L.polyline(latlngs, {color: "#38bdf8", weight: 2, opacity: 0.4}).addTo(this.map);
			this.map.fitBounds(latlngs, {padding: [30, 30]});
		},

		render: function (state) {
			if (state.location) {
				var pos = [state.location.latitude, state.location.longitude];
				if (!this.marker) {
					this.marker = L.marker(pos).addTo(this.map).bindPopup(this.subjectName);
				} else {
					this.marker.setLatLng(pos);
				}
				document.querySelector(".loc-status").textContent = "재생 중";
			}

			drawLineChart(this.accelCanvasX, state.accelBufferX, state.accelBufferSize, ACCEL_AXIS_COLORS.x, ACCEL_X_MIN, ACCEL_X_MAX);
			drawLineChart(this.accelCanvasY, state.accelBufferY, state.accelBufferSize, ACCEL_AXIS_COLORS.y, ACCEL_Y_MIN, ACCEL_Y_MAX);
			drawLineChart(this.accelCanvasZ, state.accelBufferZ, state.accelBufferSize, ACCEL_AXIS_COLORS.z, ACCEL_Z_MIN, ACCEL_Z_MAX);

			if (state.velocity) {
				document.querySelector(".velocity-value").textContent = state.velocity.speed.toFixed(2) + " m/s";
			}

			document.querySelector(".ecg-status").textContent = state.ecgBuffer.length > 0 ? "재생 중" : "-";
			drawLineChart(this.ecgCanvas, state.ecgBuffer, state.ecgBufferSize, "#38bdf8", ECG_MIN, ECG_MAX);
		}
	};

	/** 재생/일시정지/배속/탐색을 조율한다 */
	var PlaybackPlayer = {
		data: null,
		currentMs: 0,
		playing: false,
		speed: 1,
		timer: null,

		init: function (data) {
			this.data = data;
			this.playBtn = document.getElementById("play-btn");
			this.speedSelect = document.getElementById("speed-select");
			this.seekBar = document.getElementById("seek-bar");
			this.timeLabel = document.getElementById("time-label");

			this.playBtn.addEventListener("click", this._togglePlay.bind(this));
			this.speedSelect.addEventListener("change", this._onSpeedChange.bind(this));
			this.seekBar.addEventListener("input", this._onSeek.bind(this));

			this._renderAt(0);
		},

		_togglePlay: function () {
			if (this.playing) {
				this._pause();
				return;
			}
			if (this.currentMs >= this.data.durationMs) {
				this.currentMs = 0;
			}
			this.playing = true;
			this.playBtn.textContent = "⏸ 일시정지";
			this.lastTick = Date.now();
			this.timer = setInterval(this._tick.bind(this), TICK_MS);
		},

		_pause: function () {
			this.playing = false;
			this.playBtn.textContent = "▶ 재생";
			clearInterval(this.timer);
		},

		_tick: function () {
			var now = Date.now();
			var elapsed = (now - this.lastTick) * this.speed;
			this.lastTick = now;
			this.currentMs = Math.min(this.currentMs + elapsed, this.data.durationMs);
			this._renderAt(this.currentMs);
			if (this.currentMs >= this.data.durationMs) {
				this._pause();
			}
		},

		_onSpeedChange: function () {
			this.speed = Number(this.speedSelect.value);
		},

		_onSeek: function () {
			var fraction = Number(this.seekBar.value) / 1000;
			this.currentMs = fraction * this.data.durationMs;
			this._renderAt(this.currentMs, true);
		},

		_renderAt: function (timeMs, skipSeekBarUpdate) {
			var state = this.data.stateAt(timeMs);
			PlaybackView.render(state);
			this.timeLabel.textContent = formatTime(timeMs) + " / " + formatTime(this.data.durationMs);
			if (!skipSeekBarUpdate) {
				var fraction = this.data.durationMs === 0 ? 0 : timeMs / this.data.durationMs;
				this.seekBar.value = Math.round(fraction * 1000);
			}
		}
	};

	function init() {
		fetch("/api/history/sessions/" + SESSION_ID + "/playback")
			.then(function (res) {
				if (!res.ok) {
					throw new Error("재생 데이터를 불러오지 못했습니다");
				}
				return res.json();
			})
			.then(function (raw) {
				var data = new PlaybackData(raw);
				document.getElementById("subject-title").textContent =
					raw.session.subjectName + " (" + raw.session.subjectTypeLabel + ")";
				document.getElementById("session-time").textContent =
					raw.session.startedAt + " ~ " + (raw.session.endedAt || "-");

				PlaybackView.init(raw.session.subjectName);
				PlaybackView.drawFullPath(data.locations);
				PlaybackPlayer.init(data);
			})
			.catch(function (err) {
				document.getElementById("subject-title").textContent = "오류: " + err.message;
			});
	}

	document.addEventListener("DOMContentLoaded", init);
})();

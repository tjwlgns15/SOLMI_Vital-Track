/**
 * 측정 이력 재생 화면.
 * - PlaybackData: 서버에서 받은 기록을 시간순 타임라인으로 정리만 담당
 * - PlaybackView: 지도/ECG/가속도(x·y·z)/속도 렌더링만 담당 (파형은 대시보드와 같은 스윕 차트 - SweepChart)
 * - PlaybackPlayer: 재생/일시정지/배속/탐색(seek) 조율만 담당
 */
(function () {
	"use strict";

	// ECG/가속도 둘 다 세션마다 실제 samplingRateHz가 다를 수 있어(가속도는 시뮬레이터 50Hz, 실제 앱
	// 테스트 250Hz로 이미 확인됨), 차트를 "샘플 개수" 고정이 아니라 "몇 초를 보여줄지" 기준으로 잡는다.
	// (대시보드 dashboard.js와 동일한 정책 - 세션의 실제 samplingRateHz로 차트를 한 번 만들어 재생 내내 사용한다.)
	// 차트 y축 범위/배색은 대시보드와 공유하도록 SweepChart 프리셋(sweep-chart.js)에 있다.
	var ECG_WINDOW_SECONDS = 10;
	var ECG_DEFAULT_SAMPLING_HZ = 250; // ECG 기록이 하나도 없어 rate를 알 수 없을 때의 기본값
	var ACCEL_WINDOW_SECONDS = 10;
	var ACCEL_DEFAULT_SAMPLING_HZ = 50; // 가속도 기록이 하나도 없어 rate를 알 수 없을 때의 기본값
	// 스윕 차트의 프레임 속도(25fps)에 맞춰 재생 시점을 갱신한다.
	var TICK_MS = 40;

	function formatTime(ms) {
		var totalSec = Math.floor(ms / 1000);
		var m = Math.floor(totalSec / 60);
		var s = totalSec % 60;
		return (m < 10 ? "0" : "") + m + ":" + (s < 10 ? "0" : "") + s;
	}

	/**
	 * 1초 단위 묶음(batch)으로 기록된 파형(ECG/가속도)을 "샘플 단위" 타임라인으로 다룬다.
	 * 묶음 안의 샘플도 samplingRateHz에 맞춰 시간순으로 조금씩 드러나게 해서, 재생 중 파형이
	 * 묶음 단위로 뚝뚝 끊기지 않고 실시간 대시보드처럼 부드럽게 스윕되게 한다.
	 */
	function SampleTimeline(batches, samplingRateHz) {
		this.batches = batches;
		this.samplingRateHz = samplingRateHz;
		// startIndex[k] = k번째 묶음 첫 샘플의 절대 번호 (세션 시작부터 몇 번째 샘플인지)
		this.startIndex = [];
		var total = 0;
		for (var k = 0; k < batches.length; k++) {
			this.startIndex.push(total);
			total += batches[k].samples.length;
		}
	}

	/**
	 * timeMs 시점까지 드러난 샘플 중 최근 maxCount개와, 그 다음 샘플의 절대 번호(endIndex)를 반환한다.
	 * 스윕 차트는 endIndex로 현재 그리는 위치를 정하므로, 탐색(seek)해도 같은 모습으로 그려진다.
	 */
	SampleTimeline.prototype.upTo = function (timeMs, maxCount) {
		var last = -1;
		for (var k = 0; k < this.batches.length; k++) {
			if (this.batches[k].offsetMs > timeMs) {
				break;
			}
			last = k;
		}
		if (last < 0) {
			return {samples: [], endIndex: 0};
		}
		var lastBatch = this.batches[last];
		var revealed = Math.min(lastBatch.samples.length,
				Math.floor((timeMs - lastBatch.offsetMs) * this.samplingRateHz / 1000) + 1);

		var chunks = [lastBatch.samples.slice(0, revealed)];
		var count = revealed;
		for (var b = last - 1; b >= 0 && count < maxCount; b--) {
			chunks.unshift(this.batches[b].samples);
			count += this.batches[b].samples.length;
		}
		var samples = [].concat.apply([], chunks);
		if (samples.length > maxCount) {
			samples = samples.slice(samples.length - maxCount);
		}
		return {samples: samples, endIndex: this.startIndex[last] + revealed};
	};

	/** 서버 응답을 재생하기 쉬운 형태로만 가공한다 */
	function PlaybackData(raw) {
		this.session = raw.session;
		this.locations = raw.locations.slice().sort(byOffset);
		this.ecgBatches = raw.ecgBatches.slice().sort(byOffset);
		this.accelerations = raw.accelerations.slice().sort(byOffset);
		this.velocities = raw.velocities.slice().sort(byOffset);

		// ECG/가속도 배치들의 samplingRateHz는 한 세션 안에서 항상 같다고 가정하고(같은 기기가 보낸 기록),
		// 첫 배치의 값으로 차트 크기를 한 번만 정해 재생 내내 사용한다.
		this.ecgSamplingRateHz = (this.ecgBatches.length > 0 && this.ecgBatches[0].samplingRateHz)
			|| ECG_DEFAULT_SAMPLING_HZ;
		this.ecgWindowSize = Math.round(this.ecgSamplingRateHz * ECG_WINDOW_SECONDS);
		this.ecgTimeline = new SampleTimeline(this.ecgBatches, this.ecgSamplingRateHz);

		this.accelSamplingRateHz = (this.accelerations.length > 0 && this.accelerations[0].samplingRateHz)
			|| ACCEL_DEFAULT_SAMPLING_HZ;
		this.accelWindowSize = Math.round(this.accelSamplingRateHz * ACCEL_WINDOW_SECONDS);
		this.accelTimeline = new SampleTimeline(this.accelerations, this.accelSamplingRateHz);

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

	function latestUpTo(list, timeMs) {
		var latest = null;
		for (var i = 0; i < list.length; i++) {
			if (list[i].offsetMs > timeMs) {
				break;
			}
			latest = list[i];
		}
		return latest;
	}

	/** 특정 시점(timeMs)까지의 상태(마커 위치, 속도, 최근 ECG/가속도 샘플과 절대 샘플 번호)를 계산한다 */
	PlaybackData.prototype.stateAt = function (timeMs) {
		return {
			location: latestUpTo(this.locations, timeMs),
			velocity: latestUpTo(this.velocities, timeMs),
			ecg: this.ecgTimeline.upTo(timeMs, this.ecgWindowSize),
			ecgSamplingRateHz: this.ecgSamplingRateHz,
			accel: this.accelTimeline.upTo(timeMs, this.accelWindowSize)
		};
	};

	/**
	 * 대상별 색상(session.subjectColor, 서버의 SubjectColors가 결정)으로 채운 핀 모양 마커 아이콘.
	 * 대시보드/이력 목록과 같은 색을 써서 어느 화면에서 보든 같은 대상은 같은 색으로 보이게 한다.
	 */
	function subjectMarkerIcon(color) {
		return L.divIcon({
			className: "subject-marker",
			html: '<svg width="24" height="36" viewBox="0 0 24 36">' +
				'<path d="M12 0C5.4 0 0 5.4 0 12c0 9 12 24 12 24s12-15 12-24C24 5.4 18.6 0 12 0z" ' +
				'fill="' + color + '" stroke="rgba(0,0,0,0.45)" stroke-width="1"/>' +
				'<circle cx="12" cy="12" r="4.5" fill="#ffffff"/></svg>',
			iconSize: [24, 36],
			iconAnchor: [12, 36],
			popupAnchor: [0, -32]
		});
	}

	/** 지도/ECG/가속도(x·y·z)/속도 화면 렌더링만 담당 */
	var PlaybackView = {
		map: null,
		marker: null,
		path: null,
		pathPoints: [],

		init: function (subjectName, subjectColor, data) {
			this.map = L.map("map").setView([37.5665, 126.9780], 15);
			// 대시보드 지도와 동일하게 표준 OSM 타일 + CSS 필터(.leaflet-tile-pane)로 어둡게 한다.
			L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
				attribution: "&copy; OpenStreetMap contributors",
				maxZoom: 19
			}).addTo(this.map);
			this.subjectName = subjectName;
			this.subjectColor = subjectColor;
			this.ecgChart = SweepChart.ecg(
					document.getElementById("ecg-chart"), data.ecgSamplingRateHz, ECG_WINDOW_SECONDS);
			this.accelChart = SweepChart.acceleration(
					document.getElementById("accel-chart"), data.accelSamplingRateHz, ACCEL_WINDOW_SECONDS);
		},

		drawFullPath: function (locations) {
			if (locations.length === 0) {
				return;
			}
			var latlngs = locations.map(function (l) {
				return [l.latitude, l.longitude];
			});
			L.polyline(latlngs, {color: this.subjectColor, weight: 2, opacity: 0.5}).addTo(this.map);
			this.map.fitBounds(latlngs, {padding: [30, 30]});
		},

		render: function (state) {
			if (state.location) {
				var pos = [state.location.latitude, state.location.longitude];
				if (!this.marker) {
					this.marker = L.marker(pos, {icon: subjectMarkerIcon(this.subjectColor)}).addTo(this.map).bindPopup(this.subjectName);
				} else {
					this.marker.setLatLng(pos);
				}
				document.querySelector(".loc-status").textContent =
					VitalsFormat.coordinates(state.location.latitude, state.location.longitude);
			}

			this.accelChart.renderUpTo(state.accel.samples.map(SweepChart.toAccelRow), state.accel.endIndex);

			if (state.velocity) {
				document.querySelector(".velocity-value").textContent = state.velocity.speed.toFixed(2) + " km/h";
			}

			// 대시보드와 같은 방식으로, 재생 시점까지의 최근 ECG 버퍼에서 R파 간격으로 심박수를 추정한다.
			var bpm = VitalsFormat.heartRateBpm(state.ecg.samples, state.ecgSamplingRateHz);
			document.querySelector(".ecg-status").textContent = bpm != null ? bpm + " bpm" : "-";
			this.ecgChart.renderUpTo(state.ecg.samples.map(SweepChart.toEcgRow), state.ecg.endIndex);
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
			this.playBtn.textContent = MESSAGES.pause;
			this.lastTick = Date.now();
			this.timer = setInterval(this._tick.bind(this), TICK_MS);
		},

		_pause: function () {
			this.playing = false;
			this.playBtn.textContent = MESSAGES.play;
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
					throw new Error(MESSAGES.loadError);
				}
				return res.json();
			})
			.then(function (raw) {
				var data = new PlaybackData(raw);
				document.getElementById("subject-title").textContent =
					raw.session.subjectName + " (" + raw.session.subjectSpecies + ")";
				document.getElementById("session-time").textContent =
					raw.session.startedAt + " ~ " + (raw.session.endedAt || "-");

				PlaybackView.init(raw.session.subjectName, raw.session.subjectColor, data);
				PlaybackView.drawFullPath(data.locations);
				PlaybackPlayer.init(data);
			})
			.catch(function (err) {
				document.getElementById("subject-title").textContent = MESSAGES.errorPrefix + err.message;
			});
	}

	document.addEventListener("DOMContentLoaded", init);
})();

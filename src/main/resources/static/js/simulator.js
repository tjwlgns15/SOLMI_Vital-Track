/**
 * 스마트폰 앱을 대신하는 시뮬레이터.
 * - SubjectClient: 로그인한 계정의 측정 대상 목록 조회(GET /api/subjects)만 담당
 * - SessionClient: 세션 시작/종료 REST 호출만 담당
 * - VitalSignalGenerator: 가짜 GPS/ECG/가속도/속도 값 생성만 담당
 * - Simulator: 위 셋을 조율하고 WebSocket으로 전송
 */
(function () {
	"use strict";

	var BASE_LAT = 37.5665; // 서울 시청
	var BASE_LNG = 126.9780;
	var ECG_SAMPLING_HZ = 250;
	var ECG_BATCH_SIZE = 250; // 1초마다 250샘플(=250Hz) 전송
	var HEART_RATE_BPM = 72;
	var ACCEL_SAMPLING_HZ = 50;
	var ACCEL_BATCH_SIZE = 50; // ECG와 동일하게 1초마다 50샘플(=50Hz) 묶음 전송
	// "이상행동(뒤척임)" 모드에서 정지<->격한 움직임을 오가는 주기.
	// 서버(ActivityHistory)의 "최근 10분 내 정지<->활동 전환 4회 이상 = 이상행동 의심" 판정을
	// 실제로 눈으로 빨리 확인해볼 수 있도록, 짧은 주기로 전환한다.
	var ABNORMAL_PHASE_MS = 2000;

	function csrfHeaders() {
		var token = document.querySelector('meta[name="_csrf"]').content;
		var header = document.querySelector('meta[name="_csrf_header"]').content;
		var headers = {"Content-Type": "application/json"};
		headers[header] = token;
		return headers;
	}

	/**
	 * 로그인한 계정 소유의 측정 대상 목록 조회만 담당.
	 * 화면 렌더링 시점에 서버가 모델로 내려주는 대신, 실제 앱처럼 로그인 이후
	 * 클라이언트가 API를 호출해 받아온다 (/simulator 페이지 자체는 이미 로그인이 필요하므로
	 * 이 호출 시점에는 인증된 상태임이 보장된다).
	 */
	var SubjectClient = {
		list: function () {
			return fetch("/api/subjects").then(function (res) {
				if (!res.ok) {
					throw new Error("측정 대상 목록을 불러오지 못했습니다");
				}
				return res.json();
			});
		}
	};

	/** 측정 세션 시작/종료 REST 호출만 담당 */
	var SessionClient = {
		start: function (subjectId) {
			return fetch("/api/sessions/subjects/" + subjectId + "/start", {
				method: "POST",
				headers: csrfHeaders()
			}).then(function (res) {
				if (!res.ok) {
					return res.text().then(function (text) {
						throw new Error(text || "측정 시작에 실패했습니다");
					});
				}
				return res.json();
			});
		},
		end: function (sessionId) {
			return fetch("/api/sessions/" + sessionId + "/end", {
				method: "POST",
				headers: csrfHeaders()
			}).then(function (res) {
				if (!res.ok) {
					return res.text().then(function (text) {
						throw new Error(text || "측정 종료에 실패했습니다");
					});
				}
				return res.json();
			});
		}
	};

	/** 가짜 위치/ECG/가속도/속도 값 생성만 담당 */
	function VitalSignalGenerator() {
		this.lat = BASE_LAT;
		this.lng = BASE_LNG;
		this.ecgPhase = 0;
		this.velocity = 1.2 + Math.random() * 0.4; // 도보 속도(m/s) 근방에서 시작
		// 가속도 시뮬레이션 모드: WALK(기본 보행) | REST(정지 상태) | ABNORMAL(이상행동/뒤척임).
		// 위치/ECG/속도는 그대로 두고 가속도 파형만 모드에 따라 다르게 생성한다 - 서버의 활동 분석
		// 기능(measurement.activity)이 가속도만 보기 때문에, 그 기능을 시연/테스트하는 용도다.
		this.accelMode = "WALK";
		this.accelModeStartedAt = Date.now();
	}

	/** 가속도 시뮬레이션 모드를 바꾼다. 측정 중에도 바로 반영된다 (다음 배치부터). */
	VitalSignalGenerator.prototype.setAccelMode = function (mode) {
		this.accelMode = mode;
		// 모드를 바꾼 시점부터 다시 세야 "이상행동" 모드의 정지<->격한 움직임 주기가
		// 매번 정지 상태로 시작해서 일관되게 동작한다.
		this.accelModeStartedAt = Date.now();
	};

	VitalSignalGenerator.prototype.nextLocation = function () {
		// 아주 작은 반경으로 랜덤 워크 (도보 이동을 흉내)
		this.lat += (Math.random() - 0.5) * 0.0006;
		this.lng += (Math.random() - 0.5) * 0.0006;
		return {latitude: this.lat, longitude: this.lng};
	};

	VitalSignalGenerator.prototype.nextEcgBatch = function () {
		var samples = [];
		var beatDurationSec = 60 / HEART_RATE_BPM;
		var phaseStep = (1 / ECG_SAMPLING_HZ) / beatDurationSec;
		for (var i = 0; i < ECG_BATCH_SIZE; i++) {
			samples.push(this._ecgValueAt(this.ecgPhase) + (Math.random() - 0.5) * 0.03);
			this.ecgPhase = (this.ecgPhase + phaseStep) % 1;
		}
		return samples;
	};

	VitalSignalGenerator.prototype._ecgValueAt = function (t) {
		return 0.15 * this._gauss(t, 0.15, 0.02)
			- 0.15 * this._gauss(t, 0.38, 0.008)
			+ 1.0 * this._gauss(t, 0.40, 0.008)
			- 0.25 * this._gauss(t, 0.43, 0.008)
			+ 0.30 * this._gauss(t, 0.65, 0.03);
	};

	VitalSignalGenerator.prototype._gauss = function (t, mu, sigma) {
		var diff = t - mu;
		return Math.exp(-(diff * diff) / (2 * sigma * sigma));
	};

	/**
	 * ECG와 마찬가지로 매 샘플을 개별 전송하지 않고 일정 구간을 모아 전송한다.
	 * accelMode에 따라 서로 다른 흔들림 폭의 배치를 만든다:
	 *   WALK     -> 기존과 동일한 보행 흔들림 (서버 기준 WALKING으로 분류됨)
	 *   REST     -> 흔들림을 거의 없앤 정지 상태 (서버 기준 REST로 분류됨)
	 *   ABNORMAL -> ABNORMAL_PHASE_MS 주기로 정지<->격한 움직임(ACTIVE)을 오간다
	 */
	VitalSignalGenerator.prototype.nextAccelerationBatch = function () {
		if (this.accelMode === "REST") {
			return this._stillAccelerationBatch();
		}
		if (this.accelMode === "ABNORMAL") {
			var elapsed = Date.now() - this.accelModeStartedAt;
			var phase = Math.floor(elapsed / ABNORMAL_PHASE_MS) % 2;
			return phase === 0 ? this._stillAccelerationBatch() : this._agitatedAccelerationBatch();
		}
		return this._walkAccelerationBatch();
	};

	/** 중력가속도(z축) 기준에 보행으로 인한 작은 흔들림(jitter)을 더한 값 (기존과 동일) */
	VitalSignalGenerator.prototype._walkAccelerationBatch = function () {
		var samples = [];
		for (var i = 0; i < ACCEL_BATCH_SIZE; i++) {
			samples.push({
				x: (Math.random() - 0.5) * 0.6,
				y: (Math.random() - 0.5) * 0.6,
				z: 9.8 + (Math.random() - 0.5) * 0.4
			});
		}
		return samples;
	};

	/**
	 * 거의 흔들림이 없는 정지 상태. 서버의 활동 분류 기준(magnitude 표준편차 < 0.05 = REST)에
	 * 여유 있게 들어가도록, 보행(±0.3/±0.2)보다 훨씬 작은 흔들림만 남긴다.
	 */
	VitalSignalGenerator.prototype._stillAccelerationBatch = function () {
		var samples = [];
		for (var i = 0; i < ACCEL_BATCH_SIZE; i++) {
			samples.push({
				x: (Math.random() - 0.5) * 0.02,
				y: (Math.random() - 0.5) * 0.02,
				z: 9.8 + (Math.random() - 0.5) * 0.02
			});
		}
		return samples;
	};

	/**
	 * 뒤척임/급격한 움직임을 흉내내는 격한 흔들림. 서버의 활동 분류 기준
	 * (magnitude 표준편차 >= 0.5 = ACTIVE)을 여유 있게 넘기도록 크게 흔든다.
	 */
	VitalSignalGenerator.prototype._agitatedAccelerationBatch = function () {
		var samples = [];
		for (var i = 0; i < ACCEL_BATCH_SIZE; i++) {
			samples.push({
				x: (Math.random() - 0.5) * 4,
				y: (Math.random() - 0.5) * 4,
				z: 9.8 + (Math.random() - 0.5) * 4
			});
		}
		return samples;
	};

	VitalSignalGenerator.prototype.nextVelocity = function () {
		this.velocity += (Math.random() - 0.5) * 0.15;
		this.velocity = Math.max(0, Math.min(2.5, this.velocity));
		return this.velocity;
	};

	/** 조율: REST로 세션 시작/종료, WebSocket으로 값 전송, 화면 로그 출력 */
	var Simulator = {
		stompClient: null,
		timers: [],
		session: null,
		generator: null,

		init: function () {
			this.startBtn = document.getElementById("start-btn");
			this.stopBtn = document.getElementById("stop-btn");
			this.select = document.getElementById("subject-select");
			this.accelModeSelect = document.getElementById("accel-mode-select");
			this.logEl = document.getElementById("log");
			this.loadingEl = document.getElementById("subject-loading");
			this.emptyEl = document.getElementById("subject-empty");
			this.controlsEl = document.getElementById("subject-controls");

			this.startBtn.addEventListener("click", this._onStart.bind(this));
			this.stopBtn.addEventListener("click", this._onStop.bind(this));
			this.accelModeSelect.addEventListener("change", this._onAccelModeChange.bind(this));
			this._connect();
			this._loadSubjects();
		},

		_loadSubjects: function () {
			var self = this;
			SubjectClient.list().then(function (subjects) {
				self.loadingEl.style.display = "none";
				if (subjects.length === 0) {
					self.emptyEl.style.display = "";
					return;
				}
				subjects.forEach(function (subject) {
					var option = document.createElement("option");
					option.value = subject.id;
					option.textContent = subject.name + " (" + subject.typeLabel + ")";
					self.select.appendChild(option);
				});
				self.controlsEl.style.display = "";
			}).catch(function (err) {
				self.loadingEl.textContent = "오류: " + err.message;
			});
		},

		_connect: function () {
			var self = this;
			var socket = new SockJS("/ws");
			this.stompClient = new StompJs.Client({
				webSocketFactory: function () {
					return socket;
				},
				reconnectDelay: 3000,
				onConnect: function () {
					self._log("서버에 연결되었습니다.");
				}
			});
			this.stompClient.activate();
		},

		_onStart: function () {
			var self = this;
			var subjectId = this.select.value;
			SessionClient.start(subjectId).then(function (session) {
				self.session = session;
				self.generator = new VitalSignalGenerator();
				self.generator.setAccelMode(self.accelModeSelect.value);
				self._log("측정 시작: " + self.select.selectedOptions[0].textContent + " (sessionId=" + session.sessionId + ")");
				self.startBtn.disabled = true;
				self.stopBtn.disabled = false;
				self.select.disabled = true;
				self._startSending(subjectId);
			}).catch(function (err) {
				self._log("오류: " + err.message);
			});
		},

		/** 측정 중에 모드를 바꾸면 바로 반영한다 (측정 시작 전이면 다음 시작 시 적용된다). */
		_onAccelModeChange: function () {
			if (this.generator) {
				this.generator.setAccelMode(this.accelModeSelect.value);
				this._log("가속도 시뮬레이션 모드 변경: " + this.accelModeSelect.selectedOptions[0].textContent);
			}
		},

		_onStop: function () {
			var self = this;
			if (!this.session) {
				return;
			}
			SessionClient.end(this.session.sessionId).then(function () {
				self._log("측정 종료: sessionId=" + self.session.sessionId);
				self._stopSending();
			}).catch(function (err) {
				self._log("오류: " + err.message);
				self._stopSending();
			});
		},

		_startSending: function (subjectId) {
			var self = this;
			this.timers.push(setInterval(function () {
				var loc = self.generator.nextLocation();
				self._publish("/app/subjects/" + subjectId + "/location", {
					subjectId: Number(subjectId),
					latitude: loc.latitude,
					longitude: loc.longitude,
					measuredAt: new Date().toISOString()
				});
			}, 1000));

			this.timers.push(setInterval(function () {
				self._publish("/app/subjects/" + subjectId + "/ecg", {
					subjectId: Number(subjectId),
					samples: self.generator.nextEcgBatch(),
					samplingRateHz: ECG_SAMPLING_HZ,
					measuredAt: new Date().toISOString()
				});
			}, 1000));

			this.timers.push(setInterval(function () {
				self._publish("/app/subjects/" + subjectId + "/acceleration", {
					subjectId: Number(subjectId),
					samples: self.generator.nextAccelerationBatch(),
					samplingRateHz: ACCEL_SAMPLING_HZ,
					measuredAt: new Date().toISOString()
				});
			}, 1000));

			this.timers.push(setInterval(function () {
				self._publish("/app/subjects/" + subjectId + "/velocity", {
					subjectId: Number(subjectId),
					speed: self.generator.nextVelocity(),
					measuredAt: new Date().toISOString()
				});
			}, 1000));
		},

		_stopSending: function () {
			this.timers.forEach(clearInterval);
			this.timers = [];
			this.session = null;
			this.startBtn.disabled = false;
			this.stopBtn.disabled = true;
			this.select.disabled = false;
		},

		_publish: function (destination, payload) {
			if (this.stompClient && this.stompClient.connected) {
				this.stompClient.publish({destination: destination, body: JSON.stringify(payload)});
			}
		},

		_log: function (text) {
			var time = new Date().toLocaleTimeString();
			this.logEl.textContent += "[" + time + "] " + text + "\n";
			this.logEl.scrollTop = this.logEl.scrollHeight;
		}
	};

	document.addEventListener("DOMContentLoaded", function () {
		Simulator.init();
	});
})();

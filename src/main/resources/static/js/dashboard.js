/**
 * 실시간 모니터링 대시보드.
 * - 지도(Leaflet): 활성 세션이 있는 대상의 위치 마커를 실시간 갱신
 * - 신호 패널: 대상별 카드에 최근 ECG 파형, 가속도 x/y/z 파형(각각 별도 차트), 속도를 실시간 갱신
 * - 접속해 있는 동안 새로 측정이 시작된 대상은 카드가 나타나고, 종료된 대상은 카드가 사라진다
 *   (세션 생명주기 알림을 /topic/members/{memberId}/sessions/started·ended로 구독)
 *
 * 책임을 셋으로 나눠 SRP를 지킨다: MapView(지도), SubjectCard(카드 UI), Dashboard(웹소켓 연결/조율)
 */
(function () {
	"use strict";

	var DEFAULT_CENTER = [37.5665, 126.9780]; // 서울 시청 기준 기본 좌표
	var STALE_MS = 6000; // 이 시간 동안 데이터가 없으면 오프라인으로 표시
	// ECG/가속도 둘 다 실제로 들어오는 samplingRateHz가 환경마다 다를 수 있어(가속도는 시뮬레이터 50Hz,
	// 실제 앱 테스트 250Hz로 이미 확인됨), 버퍼를 "샘플 개수" 고정이 아니라 "몇 초를 보여줄지" 기준으로
	// 잡고, 실제 samplingRateHz를 받을 때마다 버퍼 크기를 그때그때 계산한다 (아래 onEcg/onAcceleration).
	// 이렇게 하면 rate가 얼마로 들어오든 차트가 항상 같은 시간 길이를 보여준다.
	var ECG_WINDOW_SECONDS = 4;
	var ECG_DEFAULT_SAMPLING_HZ = 250; // samplingRateHz가 없을 때(비정상 메시지 등)의 안전한 기본값
	var ACCEL_WINDOW_SECONDS = 4;
	var ACCEL_DEFAULT_SAMPLING_HZ = 50; // samplingRateHz가 없을 때(비정상 메시지 등)의 안전한 기본값
	// x/y/z를 시각적으로 항상 같은 색으로 구분하기 위한 고정 배색(카테고리컬 색상은 순서를 바꾸지 않는다).
	var ACCEL_AXIS_COLORS = {x: "#3987e5", y: "#d95926", z: "#199e70"};

	// y축을 버퍼의 순간 min/max로 auto-scale하지 않고 채널별 고정 범위로 그린다.
	// (auto-scale은 미세한 노이즈도 큰 변화처럼 보이게 만들고, 채널마다 스케일이 달라 비교가 어려움)
	// 시뮬레이터가 실제로 생성하는 값 범위에 여유를 둔 값:
	//   ECG: 대략 -0.28~1.02 (R파 피크 ~1.0) -> -1.0~2.0
	//   가속도 X/Y: 정확히 -0.3~0.3 (보행 흔들림) -> -1.5~1.5
	//   가속도 Z: 9.6~10.0 (중력 9.8 근방) -> 8.5~11.1
	// 참고한 모니터 UI처럼 ECG 파형은 임상 모니터에서 흔히 쓰는 초록색으로 그린다.
	var ECG_COLOR = "#22c55e";
	var ECG_MIN = -1.0, ECG_MAX = 2.0;
	// x/y/z를 하나의 차트에 겹쳐 그리므로 셋이 같은 축척(min/max)을 공유해야 흔들림 크기를 그대로
	// 비교할 수 있다. z축만 중력(약 9.8) 성분이 실려 있어 그대로는 축이 다르므로, 그리기 직전에
	// ACCEL_Z_BASELINE만큼 빼서 x/y와 같은 "0 근방 흔들림" 값으로 맞춘 뒤 같은 범위로 정규화한다.
	var ACCEL_MIN = -2, ACCEL_MAX = 2;
	var ACCEL_Z_BASELINE = 9.8;
	// 세 선이 완전히 겹치지 않도록 세로로 살짝 어긋나게(offset) 그린다 (색상 구분은 그대로 유지).
	var ACCEL_LANE_OFFSET_PX = 13;

	/** 지도 렌더링만 담당 */
	var MapView = {
		map: null,
		markers: {},

		init: function () {
			this.map = L.map("map").setView(DEFAULT_CENTER, 12);
			L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
				attribution: "&copy; OpenStreetMap contributors",
				maxZoom: 19
			}).addTo(this.map);
		},

		upsertMarker: function (subjectId, name, lat, lng) {
			var marker = this.markers[subjectId];
			if (!marker) {
				marker = L.marker([lat, lng]).addTo(this.map).bindPopup(name);
				this.markers[subjectId] = marker;
			} else {
				marker.setLatLng([lat, lng]);
			}
			marker.getPopup().setContent(name);
		},

		removeMarker: function (subjectId) {
			var marker = this.markers[subjectId];
			if (marker) {
				this.map.removeLayer(marker);
				delete this.markers[subjectId];
			}
		},

		/** 해당 대상의 현재 마커 위치로 지도를 이동시킨다. 아직 위치 정보가 없으면 아무 일도 하지 않는다. */
		focusOn: function (subjectId) {
			var marker = this.markers[subjectId];
			if (!marker) {
				return;
			}
			this.map.setView(marker.getLatLng(), Math.max(this.map.getZoom(), 15), {animate: true});
			marker.openPopup();
		}
	};

	/**
	 * 버퍼(배열)에 새 값을 이어붙이고 최대 길이를 넘으면 오래된 값부터 잘라낸다.
	 * ECG 파형과 가속도 x/y/z 파형이 동일한 "최근 N개만 유지" 규칙을 쓰므로 공용 함수로 뺐다.
	 */
	function appendAndTrim(buffer, values, maxSize) {
		var merged = buffer.concat(values);
		if (merged.length > maxSize) {
			merged = merged.slice(merged.length - maxSize);
		}
		return merged;
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

	/**
	 * 가속도 x/y/z를 한 캔버스에 겹쳐 그린다. 세 축이 같은 min/max(ACCEL_MIN~ACCEL_MAX)를
	 * 공유해서 흔들림 크기를 그대로 비교할 수 있게 하되, 완전히 겹치지 않도록 축마다 픽셀
	 * 단위로 살짝 어긋나게(offset) 그린다. z축은 중력 성분(ACCEL_Z_BASELINE)을 먼저 빼서
	 * x/y와 같은 "0 근방 흔들림" 값으로 맞춘 뒤 그린다.
	 */
	function drawAccelChart(canvas, bufferX, bufferY, bufferZ, bufferSize) {
		var ctx = canvas.getContext("2d");
		ctx.clearRect(0, 0, canvas.width, canvas.height);
		drawAccelLine(ctx, canvas, bufferX, bufferSize, ACCEL_AXIS_COLORS.x, -ACCEL_LANE_OFFSET_PX, 0);
		drawAccelLine(ctx, canvas, bufferY, bufferSize, ACCEL_AXIS_COLORS.y, 0, 0);
		drawAccelLine(ctx, canvas, bufferZ, bufferSize, ACCEL_AXIS_COLORS.z, ACCEL_LANE_OFFSET_PX, ACCEL_Z_BASELINE);
	}

	function drawAccelLine(ctx, canvas, buffer, bufferSize, color, pixelOffset, valueBaseline) {
		if (buffer.length < 2) {
			return;
		}
		var w = canvas.width;
		var h = canvas.height;
		var range = (ACCEL_MAX - ACCEL_MIN) || 1;
		var step = w / (bufferSize - 1);
		var offset = bufferSize - buffer.length;

		ctx.strokeStyle = color;
		ctx.lineWidth = 1.5;
		ctx.beginPath();
		for (var i = 0; i < buffer.length; i++) {
			var x = (offset + i) * step;
			var normalized = ((buffer[i] - valueBaseline) - ACCEL_MIN) / range;
			var y = h - normalized * (h - 10) - 5 + pixelOffset;
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

	/** 대상 1건의 카드 UI(상태/ECG 파형/가속도 x·y·z 파형/속도) 렌더링만 담당 */
	function SubjectCard(subject) {
		this.subject = subject;
		this.ecgBuffer = [];
		this.accelBufferX = [];
		this.accelBufferY = [];
		this.accelBufferZ = [];
		this.lastVelocity = null;
		this.lastMessageAt = 0;
		this.element = this._render();
		this.canvas = this.element.querySelector(".ecg-canvas");
		this.accelCanvas = this.element.querySelector(".accel-canvas");
		// toast로 뜨는 휴식/이상행동 알림은 다음 알림이 올 때까지 계속 떠 있으므로, 클릭하면 직접
		// 닫을 수 있게 한다. stopPropagation을 안 하면 카드 전체의 클릭(카드 선택/해제)까지 같이
		// 발생해버리므로 막는다.
		var alertEl = this.element.querySelector(".activity-alert");
		if (alertEl) {
			alertEl.addEventListener("click", function (event) {
				event.stopPropagation();
				alertEl.style.display = "none";
			});
		}
	}

	SubjectCard.prototype._render = function () {
		var badgeClass = this.subject.type === "HUMAN" ? "badge-human" : "badge-animal";
		// 활동 수준(정지/보행/활발한 움직임)·이상행동 경고는 동물(ANIMAL) 대상에만 서버가 분석해
		// 보내주므로, 사람 대상 카드에는 아예 해당 UI를 만들지 않는다 (서버 쪽 게이팅과 대칭).
		var isAnimal = this.subject.type === "ANIMAL";
		var card = document.createElement("div");
		card.className = "subject-card";
		card.id = "subject-card-" + this.subject.id;
		card.innerHTML =
			// 카드 높이가 고정(패널의 1/3)이라 차트에 최대한 공간을 몰아주려고, 상태/이름/뱃지와
			// ECG·속도·위치·활동 수치를 별도의 두 박스로 나누지 않고 subject-card-header 한 줄에
			// 합친다 (이름 쪽은 줄지 않게 flex-shrink:0, 수치 칩들은 남는 공간에서 알아서 줄바꿈).
			'<div class="subject-card-header">' +
			'  <div class="subject-identity"><span class="status-dot offline"></span><span class="name"></span> ' +
			'  <span class="badge ' + badgeClass + '"></span></div>' +
			'  <div class="vitals-row">' +
			'    <div>' + MESSAGES.vitalsEcg + ' <div class="value ecg-status">' + MESSAGES.ecgWaiting + '</div></div>' +
			'    <div>' + MESSAGES.vitalsVelocity + ' <div class="value velocity-value">-</div></div>' +
			'    <div>' + MESSAGES.vitalsLocation + ' <div class="value loc-status">-</div></div>' +
			(isAnimal ? '    <div>' + MESSAGES.vitalsActivity + ' <div class="value activity-status">-</div></div>' : '') +
			'  </div>' +
			'</div>' +
			// 휴식/이상행동 알림은 더 이상 카드 높이를 차지하지 않는다 - 카드 우측 상단에 떠 있는
			// toast로 표시된다 (subject-card가 position:relative라 이 안에서만 절대 위치로 뜬다).
			(isAnimal ? '<div class="activity-alert" style="display:none;"></div>' : '') +
			// 캔버스 내부 해상도(width/height 속성)는 CSS가 카드 크기에 맞춰 늘려도(격자 모드에서
			// 대상이 1~2명이면 카드가 꽤 커진다) 너무 흐려지지 않도록 실제 표시 크기보다 넉넉하게 잡는다.
			'<canvas class="ecg-canvas" width="600" height="180"></canvas>' +
			'<div class="accel-panel">' +
			'  <canvas class="accel-canvas" width="600" height="140"></canvas>' +
			// X/Y/Z 범례는 더 이상 캔버스 위 자기 줄을 차지하지 않고, 캔버스 좌측 하단 안쪽에
			// 겹쳐서(overlay) 떠 있다 - 그만큼 캔버스가 세로로 더 커진다.
			'  <div class="accel-legend">' +
			'    <span class="accel-axis-label accel-axis-x">X</span>' +
			'    <span class="accel-axis-label accel-axis-y">Y</span>' +
			'    <span class="accel-axis-label accel-axis-z">Z</span>' +
			'  </div>' +
			'</div>';
		card.querySelector(".name").textContent = this.subject.name;
		card.querySelector(".badge").textContent = this.subject.typeLabel;
		return card;
	};

	SubjectCard.prototype.select = function () {
		this.element.classList.add("selected");
	};

	SubjectCard.prototype.deselect = function () {
		this.element.classList.remove("selected");
	};

	SubjectCard.prototype.markLive = function () {
		this.lastMessageAt = Date.now();
		var dot = this.element.querySelector(".status-dot");
		dot.classList.remove("offline");
		dot.classList.add("live");
	};

	SubjectCard.prototype.markOfflineIfStale = function () {
		if (this.lastMessageAt && Date.now() - this.lastMessageAt > STALE_MS) {
			var dot = this.element.querySelector(".status-dot");
			dot.classList.remove("live");
			dot.classList.add("offline");
			this.element.querySelector(".ecg-status").textContent = MESSAGES.ecgNoSignal;
		}
	};

	SubjectCard.prototype.onEcg = function (samples, samplingRateHz) {
		this.markLive();
		this.element.querySelector(".ecg-status").textContent = MESSAGES.receiving;
		var bufferSize = Math.round((samplingRateHz || ECG_DEFAULT_SAMPLING_HZ) * ECG_WINDOW_SECONDS);
		this.ecgBuffer = appendAndTrim(this.ecgBuffer, samples, bufferSize);
		drawLineChart(this.canvas, this.ecgBuffer, bufferSize, ECG_COLOR, ECG_MIN, ECG_MAX);
	};

	/**
	 * 가속도는 크기(magnitude) 하나로 뭉치지 않고 x/y/z 축을 유지하되, 한 캔버스에 겹쳐 그린다
	 * (drawAccelChart - 색상으로 구분하고 세 선을 살짝 어긋나게 그려 구분한다).
	 * 버퍼 크기는 고정 상수가 아니라 이번에 들어온 samplingRateHz 기준으로 매번 계산한다
	 * (ACCEL_WINDOW_SECONDS초 분량 = samplingRateHz * ACCEL_WINDOW_SECONDS).
	 */
	SubjectCard.prototype.onAcceleration = function (samples, samplingRateHz) {
		if (!samples || samples.length === 0) {
			return;
		}
		this.markLive();
		var bufferSize = Math.round((samplingRateHz || ACCEL_DEFAULT_SAMPLING_HZ) * ACCEL_WINDOW_SECONDS);
		this.accelBufferX = appendAndTrim(this.accelBufferX, samples.map(pluckX), bufferSize);
		this.accelBufferY = appendAndTrim(this.accelBufferY, samples.map(pluckY), bufferSize);
		this.accelBufferZ = appendAndTrim(this.accelBufferZ, samples.map(pluckZ), bufferSize);
		drawAccelChart(this.accelCanvas, this.accelBufferX, this.accelBufferY, this.accelBufferZ, bufferSize);
	};

	SubjectCard.prototype.onVelocity = function (speed) {
		this.markLive();
		this.lastVelocity = speed;
		this.element.querySelector(".velocity-value").textContent = speed.toFixed(2) + " km/h";
	};

	/** 동물 대상 카드에만 있는 활동 수준 배지를 갱신한다 (사람 카드는 해당 엘리먼트가 없어 아무 일도 안 함). */
	SubjectCard.prototype.onActivity = function (status) {
		var el = this.element.querySelector(".activity-status");
		if (!el) {
			return;
		}
		el.textContent = status.label;
		el.className = "value activity-status activity-" + status.level.toLowerCase();
	};

	/**
	 * 휴식(장시간 정지)/이상행동 의심 알림 배너를 갱신한다. 다음 알림이 오거나(레벨 무관하게
	 * 덮어씀) 카드가 제거될 때까지 화면에 남아있다 - 별도의 확인/닫기 UI는 아직 없다.
	 */
	SubjectCard.prototype.onAlert = function (alert) {
		var el = this.element.querySelector(".activity-alert");
		if (!el) {
			return;
		}
		el.textContent = alert.message;
		el.className = "activity-alert activity-alert-" + alert.type.toLowerCase();
		el.style.display = "";
	};

	SubjectCard.prototype.onLocation = function () {
		this.markLive();
		this.element.querySelector(".loc-status").textContent = MESSAGES.receiving;
	};

	/**
	 * 웹소켓 연결/구독 조율 + 위 두 컴포넌트를 연결.
	 * 대상별 데이터 구독(subscriptions)과 세션 시작/종료 알림 구독을 함께 관리해서,
	 * 접속 중에 측정이 새로 시작/종료되면 카드를 그때그때 추가/제거한다.
	 */
	var Dashboard = {
		cards: {},
		subscriptions: {}, // subjectId -> [Subscription, ...] (data topic 구독들, 종료 시 해제용)
		stompClient: null,
		selectedSubjectId: null,

		init: function () {
			MapView.init();
			SUBJECTS.forEach(function (subject) {
				this._addCard(subject);
			}, this);

			// 접속 시점에 측정 중인 대상이 하나도 없어도, 이후에 새로 시작될 수 있으므로
			// 항상 연결하고 항상 stale 체크를 돌린다.
			this._connect();
			setInterval(this._checkStale.bind(this), 2000);
		},

		/** 카드를 만들어 패널에 추가하고 관리 대상(this.cards)에 등록한다. 이미 있으면 아무 일도 하지 않는다. */
		_addCard: function (subject) {
			if (this.cards[subject.id]) {
				return;
			}
			var card = new SubjectCard(subject);
			this.cards[subject.id] = card;
			card.element.addEventListener("click", this.selectSubject.bind(this, subject.id));
			document.getElementById("signal-panel").appendChild(card.element);
			this._hideEmptyState();
			if (this.stompClient && this.stompClient.connected) {
				this.subscriptions[subject.id] = this._subscribeSubjectTopics(subject);
			}
		},

		/** 카드를 패널/지도/구독에서 모두 제거한다. */
		_removeCard: function (subjectId) {
			var card = this.cards[subjectId];
			if (!card) {
				return;
			}
			(this.subscriptions[subjectId] || []).forEach(function (sub) {
				sub.unsubscribe();
			});
			delete this.subscriptions[subjectId];
			if (this.selectedSubjectId === subjectId) {
				this.clearSelection();
			}
			MapView.removeMarker(subjectId);
			card.element.remove();
			delete this.cards[subjectId];
			this._showEmptyStateIfNoneLeft();
		},

		_hideEmptyState: function () {
			var empty = document.getElementById("empty-state-no-active");
			if (empty) {
				empty.style.display = "none";
			}
		},

		_showEmptyStateIfNoneLeft: function () {
			if (Object.keys(this.cards).length > 0) {
				return;
			}
			var empty = document.getElementById("empty-state-no-active");
			if (empty) {
				empty.style.display = "";
			}
		},

		/**
		 * 카드를 선택 상태로 표시하고, 이미 위치 정보가 있으면 바로 지도를 그 위치로 이동시킨다.
		 * 이미 선택되어 있는 카드를 다시 클릭하면 선택을 해제한다 (더 이상 그 대상을 따라 지도가 움직이지 않음).
		 */
		selectSubject: function (subjectId) {
			if (this.selectedSubjectId === subjectId) {
				this.clearSelection();
				return;
			}
			this.clearSelection();
			this.selectedSubjectId = subjectId;
			this.cards[subjectId].select();
			MapView.focusOn(subjectId);
		},

		/** 선택을 해제한다. 지도는 그 자리에 그대로 두고, 더 이상 위치 갱신을 따라가지 않는다. */
		clearSelection: function () {
			if (this.selectedSubjectId != null && this.cards[this.selectedSubjectId]) {
				this.cards[this.selectedSubjectId].deselect();
			}
			this.selectedSubjectId = null;
		},

		_connect: function () {
			var socket = new SockJS("/ws");
			this.stompClient = new StompJs.Client({
				webSocketFactory: function () {
					return socket;
				},
				reconnectDelay: 3000
			});
			this.stompClient.onConnect = this._onConnected.bind(this);
			this.stompClient.activate();
		},

		_onConnected: function () {
			var self = this;
			Object.keys(this.cards).forEach(function (id) {
				self.subscriptions[id] = self._subscribeSubjectTopics(self.cards[id].subject);
			});
			this._subscribeLifecycleEvents();
		},

		/** 접속 중 새로 시작/종료되는 세션을 알기 위해 계정 단위 알림 토픽을 구독한다. */
		_subscribeLifecycleEvents: function () {
			var self = this;
			this.stompClient.subscribe("/topic/members/" + MEMBER_ID + "/sessions/started", function (frame) {
				var subject = JSON.parse(frame.body);
				self._addCard(subject);
			});
			this.stompClient.subscribe("/topic/members/" + MEMBER_ID + "/sessions/ended", function (frame) {
				var subjectId = JSON.parse(frame.body);
				self._removeCard(subjectId);
			});
		},

		/** 대상 1건의 location/ecg/acceleration/velocity 토픽을 구독하고, 해제할 수 있도록 구독 목록을 반환한다. */
		_subscribeSubjectTopics: function (subject) {
			var self = this;
			var id = subject.id;
			var subscriptions = [
				this.stompClient.subscribe("/topic/subjects/" + id + "/location", function (frame) {
					var msg = JSON.parse(frame.body);
					self.cards[id].onLocation();
					MapView.upsertMarker(id, subject.name, msg.latitude, msg.longitude);
					if (self.selectedSubjectId === id) {
						MapView.focusOn(id);
					}
				}),
				this.stompClient.subscribe("/topic/subjects/" + id + "/ecg", function (frame) {
					var msg = JSON.parse(frame.body);
					self.cards[id].onEcg(msg.samples, msg.samplingRateHz);
				}),
				this.stompClient.subscribe("/topic/subjects/" + id + "/acceleration", function (frame) {
					var msg = JSON.parse(frame.body);
					self.cards[id].onAcceleration(msg.samples, msg.samplingRateHz);
				}),
				this.stompClient.subscribe("/topic/subjects/" + id + "/velocity", function (frame) {
					var msg = JSON.parse(frame.body);
					self.cards[id].onVelocity(msg.speed);
				})
			];
			// 활동 분석(정지/보행/활발한 움직임, 휴식/이상행동 경고)은 서버가 동물(ANIMAL) 대상에만
			// 발행하므로, 사람 대상은 애초에 구독하지 않는다 (해당 카드에는 표시할 엘리먼트도 없음).
			if (subject.type === "ANIMAL") {
				subscriptions.push(
					this.stompClient.subscribe("/topic/subjects/" + id + "/activity", function (frame) {
						self.cards[id].onActivity(JSON.parse(frame.body));
					}),
					this.stompClient.subscribe("/topic/subjects/" + id + "/alerts", function (frame) {
						self.cards[id].onAlert(JSON.parse(frame.body));
					})
				);
			}
			return subscriptions;
		},

		_checkStale: function () {
			Object.keys(this.cards).forEach(function (id) {
				this.cards[id].markOfflineIfStale();
			}, this);
		}
	};

	document.addEventListener("DOMContentLoaded", function () {
		Dashboard.init();
	});
})();

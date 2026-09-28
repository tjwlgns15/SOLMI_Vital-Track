/**
 * 실시간 모니터링 대시보드.
 * - 지도(Leaflet): 활성 세션이 있는 대상의 위치 마커를 실시간 갱신
 * - 신호 패널: 대상별 카드에 최근 ECG 파형, 가속도 x/y/z 파형, 속도를 실시간 갱신
 *   (파형은 환자 모니터처럼 제자리에서 스윕되며 그려진다 - SweepChart)
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
	// 실제 앱 테스트 250Hz로 이미 확인됨), 차트는 "샘플 개수" 고정이 아니라 "몇 초를 보여줄지" 기준으로
	// 잡고, 실제 samplingRateHz가 달라지면 차트 버퍼를 그 속도에 맞춰 다시 만든다 (SweepChart.ensureSamplingRate).
	// 차트 y축 범위/배색은 재생 화면과 공유하도록 SweepChart 프리셋(sweep-chart.js)에 있다.
	var ECG_WINDOW_SECONDS = 10;
	var ECG_DEFAULT_SAMPLING_HZ = 250; // samplingRateHz가 없을 때(비정상 메시지 등)의 안전한 기본값
	var ACCEL_WINDOW_SECONDS = 10;
	var ACCEL_DEFAULT_SAMPLING_HZ = 50; // samplingRateHz가 없을 때(비정상 메시지 등)의 안전한 기본값

	/**
	 * 대상별 색상(subject.color, 서버의 SubjectColors가 결정)으로 채운 핀 모양 마커 아이콘.
	 * 카드의 품종 뱃지와 같은 색을 써서 지도 위 마커가 어느 카드의 대상인지 바로 구분되게 한다.
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

	/** 지도 렌더링만 담당 */
	var MapView = {
		map: null,
		markers: {},

		init: function () {
			this.map = L.map("map").setView(DEFAULT_CENTER, 12);
			// 표준 OSM 타일 - 다크 톤 전용 타일(CARTO 등)은 API 키가 필요해져서 키 관리
			// 없이 영구적으로 안정적인 이 방식을 쓴다. 어둡게 보이는 건 CSS 필터(.map-panel의
			// .leaflet-tile-pane)가 담당하고, 마커/컨트롤은 그 필터 대상에서 제외해 그대로 보인다.
			L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
				attribution: "&copy; OpenStreetMap contributors",
				maxZoom: 19
			}).addTo(this.map);
		},

		upsertMarker: function (subjectId, name, color, lat, lng) {
			var marker = this.markers[subjectId];
			if (!marker) {
				marker = L.marker([lat, lng], {icon: subjectMarkerIcon(color)}).addTo(this.map).bindPopup(name);
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
	 * 심박수 계산용 최근 ECG 버퍼를 "최근 N개만 유지"하는 데 쓴다.
	 */
	function appendAndTrim(buffer, values, maxSize) {
		var merged = buffer.concat(values);
		if (merged.length > maxSize) {
			merged = merged.slice(merged.length - maxSize);
		}
		return merged;
	}

	/** 대상 1건의 카드 UI(상태/ECG 파형/가속도 x·y·z 파형/속도) 렌더링만 담당 */
	function SubjectCard(subject) {
		this.subject = subject;
		this.ecgBuffer = [];
		this.lastVelocity = null;
		this.lastMessageAt = 0;
		this.element = this._render();
		this.ecgChart = null;
		this.accelChart = null;
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
		var card = document.createElement("div");
		card.className = "subject-card";
		card.id = "subject-card-" + this.subject.id;
		card.innerHTML =
			// 카드 높이가 고정(패널의 1/3)이라 차트에 최대한 공간을 몰아주려고, 상태/이름/뱃지와
			// ECG·속도·위치·활동 수치를 별도의 두 박스로 나누지 않고 subject-card-header 한 줄에
			// 합친다 (이름 쪽은 줄지 않게 flex-shrink:0, 수치 칩들은 남는 공간에서 알아서 줄바꿈).
			'<div class="subject-card-header">' +
			'  <div class="subject-identity"><span class="status-dot offline"></span><span class="name"></span> ' +
			'  <span class="badge badge-animal"></span></div>' +
			'  <div class="vitals-row">' +
			'    <div>' + MESSAGES.vitalsEcg + ' <div class="value ecg-status">' + MESSAGES.ecgWaiting + '</div></div>' +
			'    <div>' + MESSAGES.vitalsVelocity + ' <div class="value velocity-value">-</div></div>' +
			'    <div>' + MESSAGES.vitalsLocation + ' <div class="value loc-status">-</div></div>' +
			'    <div>' + MESSAGES.vitalsActivity + ' <div class="value activity-status">-</div></div>' +
			'  </div>' +
			'</div>' +
			// 휴식/이상행동 알림은 더 이상 카드 높이를 차지하지 않는다 - 카드 우측 상단에 떠 있는
			// toast로 표시된다 (subject-card가 position:relative라 이 안에서만 절대 위치로 뜬다).
			'<div class="activity-alert" style="display:none;"></div>' +
			// 차트(ECharts)는 컨테이너 크기를 알아야 그릴 수 있으므로, 카드가 패널에 붙은 뒤 mountCharts에서 만든다.
			'<div class="ecg-chart"></div>' +
			'<div class="accel-panel">' +
			'  <div class="accel-chart"></div>' +
			// X/Y/Z 범례는 차트 위 자기 줄을 차지하지 않고, 차트 좌측 하단 안쪽에
			// 겹쳐서(overlay) 떠 있다 - 그만큼 차트가 세로로 더 커진다.
			'  <div class="accel-legend">' +
			'    <span class="accel-axis-label accel-axis-x">X</span>' +
			'    <span class="accel-axis-label accel-axis-y">Y</span>' +
			'    <span class="accel-axis-label accel-axis-z">Z</span>' +
			'  </div>' +
			'</div>';
		card.querySelector(".name").textContent = this.subject.name;
		var badge = card.querySelector(".badge");
		badge.textContent = this.subject.species;
		badge.style.setProperty("--subject-color", this.subject.color);
		return card;
	};

	/** 카드가 DOM에 붙은 뒤(크기가 정해진 뒤) 호출해 ECG/가속도 스윕 차트를 만든다. */
	SubjectCard.prototype.mountCharts = function () {
		this.ecgChart = SweepChart.ecg(
				this.element.querySelector(".ecg-chart"), ECG_DEFAULT_SAMPLING_HZ, ECG_WINDOW_SECONDS);
		this.accelChart = SweepChart.acceleration(
				this.element.querySelector(".accel-chart"), ACCEL_DEFAULT_SAMPLING_HZ, ACCEL_WINDOW_SECONDS);
	};

	/** 차트의 프레임 루프 등록/리사이즈 감시를 해제한다. 카드를 제거할 때 반드시 호출한다. */
	SubjectCard.prototype.dispose = function () {
		if (this.ecgChart) {
			this.ecgChart.dispose();
		}
		if (this.accelChart) {
			this.accelChart.dispose();
		}
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
		var rate = samplingRateHz || ECG_DEFAULT_SAMPLING_HZ;
		var bufferSize = Math.round(rate * ECG_WINDOW_SECONDS);
		this.ecgBuffer = appendAndTrim(this.ecgBuffer, samples, bufferSize);
		// 심박수는 최근 ECG_WINDOW_SECONDS초 버퍼의 R파 간격으로 추정한다. 버퍼가 아직 짧아
		// 피크가 2개 미만이면(측정 시작 직후) 계산 가능해질 때까지 "대기 중"으로 둔다.
		var bpm = VitalsFormat.heartRateBpm(this.ecgBuffer, rate);
		this.element.querySelector(".ecg-status").textContent = bpm != null ? bpm + " bpm" : MESSAGES.ecgWaiting;
		// 1초 묶음을 바로 그리지 않고 큐에 넣으면, 차트가 25fps로 나눠 그리며 스윕한다.
		this.ecgChart.ensureSamplingRate(rate);
		this.ecgChart.enqueue(samples.map(SweepChart.toEcgRow));
	};

	/**
	 * 가속도는 크기(magnitude) 하나로 뭉치지 않고 x/y/z 축을 유지하되, 한 차트에 겹쳐 그린다
	 * (색상으로 구분하고 세 선을 살짝 어긋나게 그린다 - SweepChart.acceleration).
	 */
	SubjectCard.prototype.onAcceleration = function (samples, samplingRateHz) {
		if (!samples || samples.length === 0) {
			return;
		}
		this.markLive();
		this.accelChart.ensureSamplingRate(samplingRateHz || ACCEL_DEFAULT_SAMPLING_HZ);
		this.accelChart.enqueue(samples.map(SweepChart.toAccelRow));
	};

	SubjectCard.prototype.onVelocity = function (speed) {
		this.markLive();
		this.lastVelocity = speed;
		this.element.querySelector(".velocity-value").textContent = speed.toFixed(2) + " km/h";
	};

	/** 활동 수준 배지를 갱신한다. */
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

	SubjectCard.prototype.onLocation = function (latitude, longitude) {
		this.markLive();
		this.element.querySelector(".loc-status").textContent = VitalsFormat.coordinates(latitude, longitude);
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
			card.mountCharts();
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
			card.dispose();
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
					self.cards[id].onLocation(msg.latitude, msg.longitude);
					MapView.upsertMarker(id, subject.name, subject.color, msg.latitude, msg.longitude);
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
				}),
				this.stompClient.subscribe("/topic/subjects/" + id + "/activity", function (frame) {
					self.cards[id].onActivity(JSON.parse(frame.body));
				}),
				this.stompClient.subscribe("/topic/subjects/" + id + "/alerts", function (frame) {
					self.cards[id].onAlert(JSON.parse(frame.body));
				})
			];
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

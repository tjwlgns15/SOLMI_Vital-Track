/**
 * 일일 활동량 리포트 화면. 대상/날짜를 고르면 /api/activity-report 를 호출해
 * 그 결과(정지/보행/활발한 움직임 비율, 알림 횟수, 세션 수)를 그려주는 것만 담당한다.
 */
(function () {
	"use strict";

	function formatDuration(totalSeconds) {
		var totalMinutes = Math.round(totalSeconds / 60);
		var hours = Math.floor(totalMinutes / 60);
		var minutes = totalMinutes % 60;
		if (hours > 0) {
			return hours + "시간 " + minutes + "분";
		}
		return minutes + "분";
	}

	var ReportView = {
		init: function () {
			this.subjectSelect = document.getElementById("subject-select");
			this.dateInput = document.getElementById("date-input");
			this.loadBtn = document.getElementById("load-btn");
			this.loadingEl = document.getElementById("report-loading");
			this.errorEl = document.getElementById("report-error");
			this.resultEl = document.getElementById("report-result");
			this.emptyEl = document.getElementById("report-empty");
			this.bodyEl = document.getElementById("report-body");

			if (!this.subjectSelect) {
				// 등록된 동물 측정 대상이 없어 컨트롤 자체가 렌더링되지 않은 경우
				return;
			}

			this.loadBtn.addEventListener("click", this.load.bind(this));
			this.subjectSelect.addEventListener("change", this.load.bind(this));
			this.dateInput.addEventListener("change", this.load.bind(this));

			this.load();
		},

		load: function () {
			var self = this;
			var subjectId = this.subjectSelect.value;
			var date = this.dateInput.value;
			if (!subjectId || !date) {
				return;
			}

			this.loadingEl.style.display = "";
			this.errorEl.style.display = "none";
			this.resultEl.style.display = "none";

			fetch("/api/activity-report?subjectId=" + encodeURIComponent(subjectId) + "&date=" + encodeURIComponent(date))
				.then(function (res) {
					if (!res.ok) {
						return res.text().then(function (text) {
							throw new Error(text || "리포트를 불러오지 못했습니다");
						});
					}
					return res.json();
				})
				.then(function (report) {
					self.render(report);
				})
				.catch(function (err) {
					self.loadingEl.style.display = "none";
					self.errorEl.textContent = err.message;
					self.errorEl.style.display = "";
				});
		},

		render: function (report) {
			this.loadingEl.style.display = "none";
			this.resultEl.style.display = "";
			this.emptyEl.style.display = report.hasData ? "none" : "";

			document.getElementById("report-title").textContent = report.subjectName + " · " + report.date;
			document.getElementById("report-session-count").textContent =
				"그 날짜와 겹치는 측정 세션 " + report.sessionCount + "개";

			var total = report.restSeconds + report.walkingSeconds + report.activeSeconds;
			var restPct = total > 0 ? (report.restSeconds / total) * 100 : 0;
			var walkingPct = total > 0 ? (report.walkingSeconds / total) * 100 : 0;
			var activePct = total > 0 ? (report.activeSeconds / total) * 100 : 0;

			document.getElementById("bar-rest").style.width = restPct + "%";
			document.getElementById("bar-walking").style.width = walkingPct + "%";
			document.getElementById("bar-active").style.width = activePct + "%";

			document.getElementById("legend-rest").textContent = formatDuration(report.restSeconds);
			document.getElementById("legend-walking").textContent = formatDuration(report.walkingSeconds);
			document.getElementById("legend-active").textContent = formatDuration(report.activeSeconds);

			document.getElementById("stat-rest-alerts").textContent = report.sustainedRestAlertCount + "회";
			document.getElementById("stat-abnormal-alerts").textContent = report.abnormalAlertCount + "회";
			document.getElementById("stat-session-count").textContent = report.sessionCount + "개";
		}
	};

	document.addEventListener("DOMContentLoaded", function () {
		ReportView.init();
	});
})();

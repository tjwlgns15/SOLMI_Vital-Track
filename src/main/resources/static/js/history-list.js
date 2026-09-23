/**
 * 측정 이력 목록 페이지 전용 스크립트. 책임은 단 하나 - 검색창 입력값으로
 * 이미 렌더링된 표의 행(대상 이름 기준)을 보이거나 숨기는 것뿐이다(SRP).
 * 데이터가 이미 서버 렌더링 시점에 전부 내려와 있으므로(페이지네이션 없음)
 * 재요청 없이 클라이언트에서 즉시 필터링한다.
 */
(function () {
	"use strict";

	var HistorySearch = {
		init: function () {
			var input = document.getElementById("historySearchInput");
			var tableBody = document.getElementById("historyTableBody");
			if (!input || !tableBody) {
				return;
			}

			this.input = input;
			this.rows = Array.prototype.slice.call(tableBody.querySelectorAll("tr"));
			this.emptyState = document.getElementById("historySearchEmptyState");

			input.addEventListener("input", this.applyFilter.bind(this));
		},

		applyFilter: function () {
			var keyword = this.input.value.trim().toLowerCase();
			var visibleCount = 0;

			this.rows.forEach(function (row) {
				var subjectCell = row.cells.length > 0 ? row.cells[0] : null;
				var subjectName = subjectCell ? subjectCell.textContent.trim().toLowerCase() : "";
				var matches = keyword === "" || subjectName.indexOf(keyword) !== -1;

				row.style.display = matches ? "" : "none";
				if (matches) {
					visibleCount += 1;
				}
			});

			if (this.emptyState) {
				this.emptyState.style.display = (keyword !== "" && visibleCount === 0) ? "flex" : "none";
			}
		}
	};

	document.addEventListener("DOMContentLoaded", function () {
		HistorySearch.init();
	});
})();

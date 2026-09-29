/**
 * 대시보드/재생 화면이 공통으로 쓰는 활력징후 표시 형식.
 * - coordinates: 위도/경도를 화면 표시용 문자열로 만든다 - 예: (37.5665, 126.9780)
 * (심박수는 기기가 ECG 메시지의 heartRate로 보내므로 화면에서 계산하지 않는다.)
 */
var VitalsFormat = (function () {
	"use strict";

	var COORDINATE_DECIMALS = 4; // 소수점 4자리 ≈ 10m 정밀도

	function coordinates(latitude, longitude) {
		return "(" + latitude.toFixed(COORDINATE_DECIMALS) + ", " + longitude.toFixed(COORDINATE_DECIMALS) + ")";
	}

	return {
		coordinates: coordinates
	};
})();

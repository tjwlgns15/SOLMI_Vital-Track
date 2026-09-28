/**
 * 대시보드/재생 화면이 공통으로 쓰는 활력징후 수치 계산·표시 형식.
 * - heartRateBpm: ECG 파형에서 R파 피크를 찾아 심박수(bpm)를 추정한다
 *   (ECG 메시지에는 심박수 필드가 없고 파형 샘플만 오므로, 화면이 이미 들고 있는 최근 버퍼로 계산한다)
 * - coordinates: 위도/경도를 화면 표시용 문자열로 만든다 - 예: (37.5665, 126.9780)
 */
var VitalsFormat = (function () {
	"use strict";

	// 피크 판정 임계값: 버퍼 최소~최대 범위 중 이 비율 이상이어야 R파로 본다
	// (P파/T파는 R파 진폭의 30% 안팎이라 이 선을 넘지 못한다).
	var R_PEAK_THRESHOLD_RATIO = 0.6;
	// 한 박동 안에서 노이즈로 생기는 이중 피크를 하나로 묶기 위한 최소 간격(= 최대 240bpm).
	var REFRACTORY_SECONDS = 0.25;
	// 이보다 진폭이 작으면 파형이 아니라 노이즈/무신호로 보고 계산하지 않는다.
	var MIN_AMPLITUDE = 0.2;
	var MIN_WINDOW_SECONDS = 2;
	var COORDINATE_DECIMALS = 4; // 소수점 4자리 ≈ 10m 정밀도

	/**
	 * 버퍼 안의 R파 피크 간격 평균으로 심박수를 추정한다.
	 * 피크가 2개 미만이거나 데이터가 부족하면 null을 반환한다.
	 */
	function heartRateBpm(samples, samplingRateHz) {
		if (!samples || !samplingRateHz || samples.length < samplingRateHz * MIN_WINDOW_SECONDS) {
			return null;
		}
		var min = Infinity, max = -Infinity;
		for (var i = 0; i < samples.length; i++) {
			if (samples[i] < min) min = samples[i];
			if (samples[i] > max) max = samples[i];
		}
		if (max - min < MIN_AMPLITUDE) {
			return null;
		}
		var threshold = min + (max - min) * R_PEAK_THRESHOLD_RATIO;
		var refractory = Math.round(REFRACTORY_SECONDS * samplingRateHz);

		var peaks = [];
		for (var j = 1; j < samples.length - 1; j++) {
			var v = samples[j];
			if (v < threshold || v < samples[j - 1] || v <= samples[j + 1]) {
				continue;
			}
			var last = peaks.length - 1;
			if (last >= 0 && j - peaks[last] < refractory) {
				if (v > samples[peaks[last]]) {
					peaks[last] = j;
				}
			} else {
				peaks.push(j);
			}
		}
		if (peaks.length < 2) {
			return null;
		}
		var avgIntervalSamples = (peaks[peaks.length - 1] - peaks[0]) / (peaks.length - 1);
		return Math.round(60 * samplingRateHz / avgIntervalSamples);
	}

	function coordinates(latitude, longitude) {
		return "(" + latitude.toFixed(COORDINATE_DECIMALS) + ", " + longitude.toFixed(COORDINATE_DECIMALS) + ")";
	}

	return {
		heartRateBpm: heartRateBpm,
		coordinates: coordinates
	};
})();

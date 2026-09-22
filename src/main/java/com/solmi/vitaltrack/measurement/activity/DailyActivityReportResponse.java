package com.solmi.vitaltrack.measurement.activity;

import java.time.LocalDate;

/**
 * 측정 대상 1건의 하루(00:00~24:00, 서버 기준시) 활동량 리포트.
 * 실시간 판정 결과를 별도 테이블에 저장해두지 않고, 원본 가속도 기록으로부터 매번 다시
 * 계산한 값을 담는다 (계산 방식은 {@link DailyActivityReportService} 참고).
 */
public record DailyActivityReportResponse(
		Long subjectId,
		String subjectName,
		LocalDate date,
		long restSeconds,
		long walkingSeconds,
		long activeSeconds,
		int sustainedRestAlertCount,
		int abnormalAlertCount,
		int sessionCount,
		boolean hasData
) {
}

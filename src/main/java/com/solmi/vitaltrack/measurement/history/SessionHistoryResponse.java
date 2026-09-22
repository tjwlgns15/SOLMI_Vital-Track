package com.solmi.vitaltrack.measurement.history;

import com.solmi.vitaltrack.measurement.MeasurementSession;
import com.solmi.vitaltrack.measurement.SessionEndReason;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public record SessionHistoryResponse(
		Long sessionId,
		Long subjectId,
		String subjectName,
		String subjectTypeLabel,
		LocalDateTime startedAt,
		LocalDateTime endedAt,
		long durationSeconds,
		SessionEndReason endReason
) {
	public static SessionHistoryResponse from(MeasurementSession session) {
		LocalDateTime endedAt = session.getEndedAt();
		long duration = endedAt == null ? 0 : ChronoUnit.SECONDS.between(session.getStartedAt(), endedAt);
		return new SessionHistoryResponse(
				session.getId(),
				session.getSubject().getId(),
				session.getSubject().getName(),
				session.getSubject().getType().getLabel(),
				session.getStartedAt(),
				endedAt,
				duration,
				session.getEndReason()
		);
	}
}

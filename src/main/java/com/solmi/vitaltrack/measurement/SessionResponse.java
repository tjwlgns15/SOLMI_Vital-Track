package com.solmi.vitaltrack.measurement;

import java.time.LocalDateTime;

public record SessionResponse(
		Long sessionId,
		Long subjectId,
		String subjectName,
		String subjectTypeLabel,
		SessionStatus status,
		LocalDateTime startedAt,
		SessionEndReason endReason
) {
	public static SessionResponse from(MeasurementSession session) {
		return new SessionResponse(
				session.getId(),
				session.getSubject().getId(),
				session.getSubject().getName(),
				session.getSubject().getType().getLabel(),
				session.getStatus(),
				session.getStartedAt(),
				session.getEndReason()
		);
	}
}

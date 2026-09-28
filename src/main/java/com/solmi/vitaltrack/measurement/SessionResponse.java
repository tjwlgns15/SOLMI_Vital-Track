package com.solmi.vitaltrack.measurement;

import java.time.LocalDateTime;

public record SessionResponse(
		Long sessionId,
		Long subjectId,
		String subjectName,
		String subjectSpecies,
		SessionStatus status,
		LocalDateTime startedAt,
		SessionEndReason endReason
) {
	public static SessionResponse from(MeasurementSession session) {
		return new SessionResponse(
				session.getId(),
				session.getSubject().getId(),
				session.getSubject().getName(),
				session.getSubject().getSpecies(),
				session.getStatus(),
				session.getStartedAt(),
				session.getEndReason()
		);
	}
}

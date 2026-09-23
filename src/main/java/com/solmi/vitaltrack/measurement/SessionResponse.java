package com.solmi.vitaltrack.measurement;

import com.solmi.vitaltrack.subject.SubjectTypeLabels;
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
	public static SessionResponse from(MeasurementSession session, SubjectTypeLabels subjectTypeLabels) {
		return new SessionResponse(
				session.getId(),
				session.getSubject().getId(),
				session.getSubject().getName(),
				subjectTypeLabels.labelOf(session.getSubject().getType()),
				session.getStatus(),
				session.getStartedAt(),
				session.getEndReason()
		);
	}
}

package com.solmi.vitaltrack.subject;

/**
 * 뷰/시뮬레이터/대시보드에 노출하는 측정 대상 표현.
 * 엔티티를 직접 노출하지 않고 필요한 필드만 전달한다.
 */
public record SubjectResponse(
		Long id,
		String name,
		SubjectType type,
		String typeLabel,
		String species
) {
	public static SubjectResponse from(Subject subject, SubjectTypeLabels typeLabels) {
		return new SubjectResponse(
				subject.getId(),
				subject.getName(),
				subject.getType(),
				typeLabels.labelOf(subject.getType()),
				subject.getSpecies()
		);
	}
}

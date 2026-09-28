package com.solmi.vitaltrack.subject;

/**
 * 뷰/시뮬레이터/대시보드에 노출하는 측정 대상 표현.
 * 엔티티를 직접 노출하지 않고 필요한 필드만 전달한다.
 * color는 대상별 뱃지/지도 마커에 공통으로 쓰는 표시 색상이다 (SubjectColors).
 */
public record SubjectResponse(
		Long id,
		String name,
		String species,
		String color
) {
	public static SubjectResponse from(Subject subject) {
		return new SubjectResponse(
				subject.getId(),
				subject.getName(),
				subject.getSpecies(),
				SubjectColors.of(subject.getId())
		);
	}
}

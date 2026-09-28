package com.solmi.vitaltrack.subject;

/**
 * 측정 대상마다 화면(뱃지/지도 마커)에서 구분되는 색상을 정하는 책임만 진다.
 * 색상을 DB에 저장하지 않고 id로부터 결정적으로 계산하므로, 어느 화면에서 조회하든
 * 같은 대상은 항상 같은 색으로 보인다.
 *
 * <p>id에 황금각(약 137.5도)을 곱해 색상환(hue)을 도는 방식이라, 연속으로 등록된
 * 대상끼리도 색상이 서로 멀리 떨어진다.</p>
 */
public final class SubjectColors {

	private static final double GOLDEN_ANGLE_DEGREES = 137.508;

	private SubjectColors() {
	}

	public static String of(Long subjectId) {
		long hue = Math.round((subjectId * GOLDEN_ANGLE_DEGREES) % 360);
		return "hsl(" + hue + ", 70%, 60%)";
	}
}

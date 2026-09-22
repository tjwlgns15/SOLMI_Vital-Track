package com.solmi.vitaltrack.measurement.realtime;

import com.solmi.vitaltrack.measurement.activity.AbnormalActivityDetectedEvent;
import com.solmi.vitaltrack.measurement.activity.RestDetectedEvent;

/**
 * /topic/subjects/{id}/alerts로 보내는 알림 메시지의 공통 형태. 서로 다른 활동 분석
 * 이벤트(RestDetectedEvent, AbnormalActivityDetectedEvent)를 같은 토픽으로 보내되,
 * type으로 구분할 수 있게 한다.
 */
public record ActivityAlert(String type, String message) {

	public static ActivityAlert rest(RestDetectedEvent event) {
		long minutes = event.restDuration().toMinutes();
		return new ActivityAlert("REST", minutes + "분째 거의 움직임이 없습니다 (휴식 중으로 추정)");
	}

	public static ActivityAlert abnormal(AbnormalActivityDetectedEvent event) {
		return new ActivityAlert("ABNORMAL",
				"최근 10분 내 정지↔활동 전환이 " + event.transitionsInWindow() + "회 반복되었습니다 (이상행동 의심 - 확인이 필요합니다)");
	}
}

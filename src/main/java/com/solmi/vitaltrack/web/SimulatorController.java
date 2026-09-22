package com.solmi.vitaltrack.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 실제 스마트폰 앱을 대신하는 웹 기반 시뮬레이터 화면.
 * 대상 선택 → 측정 시작 시 가짜 GPS/ECG/가속도/속도 데이터를 생성해 WebSocket으로 전송한다.
 * 측정 대상 목록은 화면 렌더링 시점에 모델로 내려주지 않고, 실제 앱과 동일하게
 * 클라이언트(simulator.js)가 로그인 이후 /api/subjects를 호출해 받아온다.
 */
@Controller
public class SimulatorController {

	@GetMapping("/simulator")
	public String simulator() {
		return "simulator/simulator";
	}
}

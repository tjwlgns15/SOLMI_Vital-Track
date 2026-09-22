package com.solmi.vitaltrack.web;

import com.solmi.vitaltrack.measurement.history.MeasurementHistoryService;
import com.solmi.vitaltrack.member.MemberPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 측정 이력 목록/재생 화면만 렌더링한다. 실제 이력 데이터는 브라우저가
 * /api/history/** 를 통해 가져온다 (대시보드와 동일한 패턴).
 */
@Controller
@RequiredArgsConstructor
public class HistoryController {

	private final MeasurementHistoryService historyService;

	@GetMapping("/history")
	public String list(@AuthenticationPrincipal MemberPrincipal principal, Model model) {
		model.addAttribute("sessions", historyService.findEndedSessions(principal.getMemberId()));
		return "history/list";
	}

	@GetMapping("/history/{sessionId}")
	public String playback(@PathVariable Long sessionId, Model model) {
		model.addAttribute("sessionId", sessionId);
		return "history/playback";
	}
}

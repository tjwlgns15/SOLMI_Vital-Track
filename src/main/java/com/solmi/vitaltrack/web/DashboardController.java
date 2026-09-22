package com.solmi.vitaltrack.web;

import com.solmi.vitaltrack.measurement.MeasurementSessionService;
import com.solmi.vitaltrack.member.MemberPrincipal;
import com.solmi.vitaltrack.subject.SubjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 실시간 모니터링 대시보드 화면만 렌더링한다.
 * 실제 실시간 데이터(및 세션 시작/종료 알림)는 브라우저가 WebSocket으로 직접 구독한다.
 */
@Controller
@RequiredArgsConstructor
public class DashboardController {

	private final SubjectService subjectService;
	private final MeasurementSessionService measurementSessionService;

	@GetMapping("/")
	public String root() {
		return "redirect:/dashboard";
	}

	@GetMapping("/dashboard")
	public String dashboard(@AuthenticationPrincipal MemberPrincipal principal, Model model) {
		// 대시보드는 "지금 측정 중인" 대상만 보여준다. 등록만 해두고 측정 중이 아닌 대상까지
		// 표시하면 실시간 카드가 계속 오프라인 상태로 쌓여 화면이 지저분해지기 때문이다.
		// (접속 이후 새로 시작/종료되는 세션은 dashboard.js가 WebSocket으로 실시간 반영한다.)
		var activeSubjects = measurementSessionService.findSubjectsWithActiveSession(principal.getMemberId());
		model.addAttribute("subjects", activeSubjects);
		// 안내 문구를 "등록된 대상이 아예 없는 경우"와 "등록은 했지만 지금은 측정 중이 아닌 경우"로
		// 구분해서 보여주기 위해, 등록 대상 존재 여부를 별도로 함께 전달한다.
		boolean hasRegisteredSubjects = !subjectService.findMySubjects(principal.getMemberId()).isEmpty();
		model.addAttribute("hasRegisteredSubjects", hasRegisteredSubjects);
		model.addAttribute("memberId", principal.getMemberId());
		model.addAttribute("memberName", principal.getName());
		return "dashboard/dashboard";
	}
}

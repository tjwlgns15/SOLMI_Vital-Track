package com.solmi.vitaltrack.web;

import com.solmi.vitaltrack.member.MemberPrincipal;
import com.solmi.vitaltrack.subject.SubjectService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 일일 활동량 리포트 화면만 렌더링한다. 실제 리포트 데이터는 브라우저가
 * /api/activity-report 를 통해 가져온다 (대시보드/이력과 동일한 패턴).
 */
@Controller
@RequiredArgsConstructor
public class ActivityReportController {

	private final SubjectService subjectService;

	@GetMapping("/report")
	public String report(@AuthenticationPrincipal MemberPrincipal principal, Model model) {
		model.addAttribute("subjects", subjectService.findMySubjects(principal.getMemberId()));
		model.addAttribute("today", LocalDate.now());
		return "activity/report";
	}
}

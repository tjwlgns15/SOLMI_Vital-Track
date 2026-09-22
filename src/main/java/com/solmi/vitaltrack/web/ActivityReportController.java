package com.solmi.vitaltrack.web;

import com.solmi.vitaltrack.member.MemberPrincipal;
import com.solmi.vitaltrack.subject.SubjectService;
import com.solmi.vitaltrack.subject.SubjectType;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 일일 활동량 리포트 화면만 렌더링한다. 실제 리포트 데이터는 브라우저가
 * /api/activity-report 를 통해 가져온다 (대시보드/이력과 동일한 패턴).
 * 활동량 분석 자체가 동물(ANIMAL) 대상에만 의미가 있으므로, 대상 선택지도 동물로만 좁혀서 내려준다.
 */
@Controller
@RequiredArgsConstructor
public class ActivityReportController {

	private final SubjectService subjectService;

	@GetMapping("/report")
	public String report(@AuthenticationPrincipal MemberPrincipal principal, Model model) {
		var animalSubjects = subjectService.findMySubjects(principal.getMemberId()).stream()
				.filter(subject -> subject.type() == SubjectType.ANIMAL)
				.toList();
		model.addAttribute("subjects", animalSubjects);
		model.addAttribute("today", LocalDate.now());
		return "activity/report";
	}
}

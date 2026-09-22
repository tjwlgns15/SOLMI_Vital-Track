package com.solmi.vitaltrack.subject;

import com.solmi.vitaltrack.member.MemberPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * 측정 대상 등록/목록 화면. 실시간 데이터/세션에는 관여하지 않는다 (SRP).
 */
@Controller
@RequiredArgsConstructor
public class SubjectController {

	private final SubjectService subjectService;

	@GetMapping("/subjects")
	public String list(@AuthenticationPrincipal MemberPrincipal principal, Model model) {
		model.addAttribute("subjects", subjectService.findMySubjects(principal.getMemberId()));
		if (!model.containsAttribute("registerRequest")) {
			model.addAttribute("registerRequest", new SubjectRegisterRequest("", null, ""));
		}
		model.addAttribute("subjectTypes", SubjectType.values());
		return "subject/list";
	}

	@PostMapping("/subjects")
	public String register(@AuthenticationPrincipal MemberPrincipal principal,
			@Valid @ModelAttribute("registerRequest") SubjectRegisterRequest request,
			BindingResult bindingResult, Model model) {
		if (bindingResult.hasErrors()) {
			model.addAttribute("subjects", subjectService.findMySubjects(principal.getMemberId()));
			model.addAttribute("subjectTypes", SubjectType.values());
			return "subject/list";
		}
		subjectService.register(principal.getMemberId(), request);
		return "redirect:/subjects";
	}
}

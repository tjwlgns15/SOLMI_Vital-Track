package com.solmi.vitaltrack.member;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * 로그인 화면과 회원가입만 담당한다. 로그인 처리 자체는 Spring Security(SecurityConfig)가 맡는다.
 */
@Controller
@RequiredArgsConstructor
public class AuthController {

	private final MemberService memberService;
	private final MessageSource messageSource;

	@GetMapping("/login")
	public String loginPage() {
		return "auth/login";
	}

	@GetMapping("/signup")
	public String signupPage(Model model) {
		if (!model.containsAttribute("signupRequest")) {
			model.addAttribute("signupRequest", new SignupRequest("", "", ""));
		}
		return "auth/signup";
	}

	@PostMapping("/signup")
	public String signup(@Valid @ModelAttribute("signupRequest") SignupRequest request,
			BindingResult bindingResult, Model model) {
		if (bindingResult.hasErrors()) {
			return "auth/signup";
		}
		try {
			memberService.signUp(request);
		} catch (DuplicateLoginIdException e) {
			String message = messageSource.getMessage(
					"signup.error.duplicateLoginId", new Object[] {e.getLoginId()}, LocaleContextHolder.getLocale());
			model.addAttribute("errorMessage", message);
			return "auth/signup";
		}
		return "redirect:/login?signup=success";
	}
}

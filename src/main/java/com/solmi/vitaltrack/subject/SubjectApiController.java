package com.solmi.vitaltrack.subject;

import com.solmi.vitaltrack.member.MemberPrincipal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 측정 대상 조회 API. 시뮬레이터(향후 실제 스마트폰 앱)가 로그인한 계정에
 * 등록된 측정 대상 목록을 화면 렌더링이 아니라 JSON으로 받아갈 수 있게 한다.
 * 등록/수정 등 화면 중심 흐름은 기존 SubjectController(웹 페이지)가 그대로 담당한다.
 */
@RestController
@RequestMapping("/api/subjects")
@RequiredArgsConstructor
public class SubjectApiController {

	private final SubjectService subjectService;

	@GetMapping
	public List<SubjectResponse> mySubjects(@AuthenticationPrincipal MemberPrincipal principal) {
		return subjectService.findMySubjects(principal.getMemberId());
	}

	@ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
	public ResponseEntity<String> handleBadRequest(RuntimeException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
	}
}

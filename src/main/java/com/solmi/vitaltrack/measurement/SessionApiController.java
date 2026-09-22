package com.solmi.vitaltrack.measurement;

import com.solmi.vitaltrack.member.MemberPrincipal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionApiController {

	private final MeasurementSessionService sessionService;

	@PostMapping("/subjects/{subjectId}/start")
	public SessionResponse start(@PathVariable Long subjectId, @AuthenticationPrincipal MemberPrincipal principal) {
		return sessionService.start(subjectId, principal.getMemberId());
	}

	@PostMapping("/{sessionId}/end")
	public SessionResponse end(@PathVariable Long sessionId, @AuthenticationPrincipal MemberPrincipal principal) {
		return sessionService.end(sessionId, principal.getMemberId());
	}

	@GetMapping("/active")
	public List<SessionResponse> activeSessions(@AuthenticationPrincipal MemberPrincipal principal) {
		return sessionService.findActiveSessions(principal.getMemberId());
	}

	@ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
	public ResponseEntity<String> handleBadRequest(RuntimeException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
	}
}

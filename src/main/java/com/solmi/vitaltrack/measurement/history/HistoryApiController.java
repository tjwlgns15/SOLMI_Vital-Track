package com.solmi.vitaltrack.measurement.history;

import com.solmi.vitaltrack.member.MemberPrincipal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class HistoryApiController {

	private final MeasurementHistoryService historyService;

	@GetMapping("/sessions")
	public List<SessionHistoryResponse> endedSessions(@AuthenticationPrincipal MemberPrincipal principal) {
		return historyService.findEndedSessions(principal.getMemberId());
	}

	@GetMapping("/sessions/{sessionId}/playback")
	public PlaybackResponse playback(@PathVariable Long sessionId, @AuthenticationPrincipal MemberPrincipal principal) {
		return historyService.getPlayback(sessionId, principal.getMemberId());
	}

	@ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
	public ResponseEntity<String> handleBadRequest(RuntimeException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
	}
}

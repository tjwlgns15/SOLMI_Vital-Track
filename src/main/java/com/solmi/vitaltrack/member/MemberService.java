package com.solmi.vitaltrack.member;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

	private final MemberRepository memberRepository;
	private final PasswordEncoder passwordEncoder;

	@Transactional
	public Long signUp(SignupRequest request) {
		if (memberRepository.existsByLoginId(request.loginId())) {
			throw new IllegalArgumentException("이미 사용 중인 아이디입니다: " + request.loginId());
		}
		String encodedPassword = passwordEncoder.encode(request.password());
		Member member = Member.register(request.loginId(), encodedPassword, request.name());
		return memberRepository.save(member).getId();
	}
}

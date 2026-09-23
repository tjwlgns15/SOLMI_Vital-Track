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
			throw new DuplicateLoginIdException(request.loginId());
		}
		String encodedPassword = passwordEncoder.encode(request.password());
		Member member = Member.register(request.loginId(), encodedPassword, request.name());
		return memberRepository.save(member).getId();
	}

	/**
	 * 언어 전환 드롭다운으로 고른 언어를 회원 정보에 남긴다. 어떤 언어 태그가 유효한지는
	 * 이 서비스의 관심사가 아니므로(그건 SupportedLocales의 규칙) 호출자가 이미 정규화한
	 * 값을 그대로 저장한다.
	 */
	@Transactional
	public void changePreferredLanguage(Long memberId, String languageTag) {
		Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 계정입니다: " + memberId));
		member.changePreferredLanguage(languageTag);
	}
}

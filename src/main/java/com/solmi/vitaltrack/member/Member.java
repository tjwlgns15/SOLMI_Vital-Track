package com.solmi.vitaltrack.member;

import com.solmi.vitaltrack.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 계정(측정 대상의 보호자/관리자). 로그인 식별자와 암호화된 비밀번호를 가진다.
 * 생성 이후 필드는 setter가 아닌 도메인 의미가 명확한 메서드로만 변경한다.
 */
@Getter
@Entity
@Table(name = "members")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 50)
	private String loginId;

	@Column(nullable = false)
	private String password;

	@Column(nullable = false, length = 50)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private MemberRole role;

	/** 회원이 언어 전환 드롭다운으로 마지막으로 고른 언어("ko"/"en"). 한 번도 안 바꿨으면 null - 이땐 기본값(한국어)으로 취급한다. */
	@Column(length = 10)
	private String preferredLanguage;

	private Member(String loginId, String encodedPassword, String name, MemberRole role) {
		this.loginId = loginId;
		this.password = encodedPassword;
		this.name = name;
		this.role = role;
	}

	/**
	 * 회원 가입 팩토리 메서드. 비밀번호는 이미 인코딩된 값을 전달받아야 한다
	 * (인코딩 책임은 MemberService/PasswordEncoder가 진다 - SRP).
	 */
	public static Member register(String loginId, String encodedPassword, String name) {
		return new Member(loginId, encodedPassword, name, MemberRole.USER);
	}

	public boolean isOwnerOf(Long memberId) {
		return this.id.equals(memberId);
	}

	/** 실시간(WebSocket) 알림처럼 브라우저 쿠키에 접근할 수 없는 경로에서도 쓸 수 있도록 언어 선택을 남긴다. */
	public void changePreferredLanguage(String languageTag) {
		this.preferredLanguage = languageTag;
	}
}

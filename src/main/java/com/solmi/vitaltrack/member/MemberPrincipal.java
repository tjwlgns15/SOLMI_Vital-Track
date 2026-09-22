package com.solmi.vitaltrack.member;

import java.util.Collection;
import java.util.List;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Spring Security가 사용하는 인증 주체. Member 엔티티를 감싸서
 * 보안 프레임워크가 필요로 하는 표현(UserDetails)만 노출한다 (SRP: Member는 도메인,
 * 이 클래스는 인증 표현을 담당).
 */
@Getter
public class MemberPrincipal implements UserDetails {

	private final Long memberId;
	private final String loginId;
	private final String password;
	private final String name;
	private final MemberRole role;

	public MemberPrincipal(Member member) {
		this.memberId = member.getId();
		this.loginId = member.getLoginId();
		this.password = member.getPassword();
		this.name = member.getName();
		this.role = member.getRole();
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
	}

	@Override
	public String getUsername() {
		return loginId;
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return true;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	@Override
	public boolean isEnabled() {
		return true;
	}
}

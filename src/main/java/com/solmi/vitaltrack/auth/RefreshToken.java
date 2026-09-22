package com.solmi.vitaltrack.auth;

import com.solmi.vitaltrack.common.BaseTimeEntity;
import com.solmi.vitaltrack.member.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 앱 클라이언트에게 발급한 리프레시 토큰 1건. 원문 토큰은 저장하지 않고 해시만 저장한다
 * (비밀번호와 동일한 이유 - DB가 유출되어도 토큰 자체를 복원할 수 없도록).
 * 1회용 로테이션(재발급 시 즉시 폐기)을 전제로 하며, revoked 플래그로 재사용을 막는다.
 */
@Entity
@Table(name = "refresh_token")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Getter
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	@Getter
	private Member member;

	@Column(nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(nullable = false, columnDefinition = "DATETIME(3)")
	private LocalDateTime expiresAt;

	@Column(nullable = false)
	private boolean revoked;

	private RefreshToken(Member member, String tokenHash, LocalDateTime expiresAt) {
		this.member = member;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
		this.revoked = false;
	}

	public static RefreshToken issue(Member member, String tokenHash, LocalDateTime expiresAt) {
		return new RefreshToken(member, tokenHash, expiresAt);
	}

	public void revoke() {
		this.revoked = true;
	}

	public boolean isUsable(LocalDateTime now) {
		return !revoked && expiresAt.isAfter(now);
	}
}

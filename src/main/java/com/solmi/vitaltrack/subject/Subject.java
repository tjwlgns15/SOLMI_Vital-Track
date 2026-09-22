package com.solmi.vitaltrack.subject;

import com.solmi.vitaltrack.common.BaseTimeEntity;
import com.solmi.vitaltrack.member.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 측정 대상(사람 또는 동물). 반드시 소유자(Member)에 귀속된다.
 * 대상의 등록/소유 검증 책임을 엔티티 스스로가 지도록 하여 서비스 레이어가
 * 도메인 규칙을 흩어놓지 않게 한다 (SRP).
 */
@Getter
@Entity
@Table(name = "subject")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Subject extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id", nullable = false)
	private Member owner;

	@Column(nullable = false, length = 50)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SubjectType type;

	@Column(length = 50)
	private String species;

	private Subject(Member owner, String name, SubjectType type, String species) {
		this.owner = owner;
		this.name = name;
		this.type = type;
		this.species = species;
	}

	public static Subject register(Member owner, String name, SubjectType type, String species) {
		if (owner == null) {
			throw new IllegalArgumentException("측정 대상은 보호자(계정)에 귀속되어야 합니다");
		}
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("측정 대상 이름은 필수입니다");
		}
		return new Subject(owner, name, type, species);
	}

	public boolean isOwnedBy(Long memberId) {
		return this.owner.getId().equals(memberId);
	}
}

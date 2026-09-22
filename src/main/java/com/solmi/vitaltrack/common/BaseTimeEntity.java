package com.solmi.vitaltrack.common;

import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 생성/수정 시각을 자동으로 관리하는 공통 상위 엔티티.
 * 각 엔티티가 반복적으로 createdAt/updatedAt 필드와 setter를 직접 구현하지 않도록
 * Auditing 어노테이션으로 처리한다 (setter 없이 프레임워크가 값을 채워준다).
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity {

	@CreatedDate
	private LocalDateTime createdAt;

	@LastModifiedDate
	private LocalDateTime updatedAt;
}

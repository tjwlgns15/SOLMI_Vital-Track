package com.solmi.vitaltrack.measurement;

import com.solmi.vitaltrack.member.Member;
import com.solmi.vitaltrack.subject.Subject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeasurementSessionRepository extends JpaRepository<MeasurementSession, Long> {

	Optional<MeasurementSession> findBySubjectAndStatus(Subject subject, SessionStatus status);

	List<MeasurementSession> findByStatus(SessionStatus status);

	@Query("select s from MeasurementSession s "
			+ "join fetch s.subject subj "
			+ "where subj.owner = :owner and s.status = :status "
			+ "order by s.startedAt desc")
	List<MeasurementSession> findByOwnerAndStatus(@Param("owner") Member owner, @Param("status") SessionStatus status);

	/**
	 * 어떤 측정 대상의, 주어진 구간과 하나라도 겹치는 세션을 모두 찾는다 (일일 활동량 리포트용).
	 * 아직 종료되지 않은(endedAt == null) 세션은 "지금까지도 계속 이어지는 중"으로 보고
	 * 겹침 판정에 포함시킨다.
	 */
	@Query("select s from MeasurementSession s "
			+ "where s.subject = :subject and s.startedAt < :end "
			+ "and (s.endedAt is null or s.endedAt > :start) "
			+ "order by s.startedAt asc")
	List<MeasurementSession> findBySubjectOverlapping(
			@Param("subject") Subject subject, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}

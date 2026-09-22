package com.solmi.vitaltrack.measurement;

import com.solmi.vitaltrack.member.Member;
import com.solmi.vitaltrack.subject.Subject;
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
}

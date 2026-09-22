package com.solmi.vitaltrack.subject;

import com.solmi.vitaltrack.member.Member;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubjectRepository extends JpaRepository<Subject, Long> {

	List<Subject> findByOwnerOrderByCreatedAtDesc(Member owner);

	Optional<Subject> findByIdAndOwner(Long id, Member owner);
}

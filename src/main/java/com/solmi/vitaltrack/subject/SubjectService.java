package com.solmi.vitaltrack.subject;

import com.solmi.vitaltrack.member.Member;
import com.solmi.vitaltrack.member.MemberRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubjectService {

	private final SubjectRepository subjectRepository;
	private final MemberRepository memberRepository;

	@Transactional
	public Long register(Long memberId, SubjectRegisterRequest request) {
		Member owner = getMember(memberId);
		Subject subject = Subject.register(owner, request.name(), request.type(), request.species());
		return subjectRepository.save(subject).getId();
	}

	public List<SubjectResponse> findMySubjects(Long memberId) {
		Member owner = getMember(memberId);
		return subjectRepository.findByOwnerOrderByCreatedAtDesc(owner).stream()
				.map(SubjectResponse::from)
				.toList();
	}

	public Subject getOwnedSubject(Long subjectId, Long memberId) {
		Subject subject = subjectRepository.findById(subjectId)
				.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 측정 대상입니다: " + subjectId));
		if (!subject.isOwnedBy(memberId)) {
			throw new IllegalStateException("본인이 등록한 측정 대상만 사용할 수 있습니다");
		}
		return subject;
	}

	private Member getMember(Long memberId) {
		return memberRepository.findById(memberId)
				.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 계정입니다: " + memberId));
	}
}

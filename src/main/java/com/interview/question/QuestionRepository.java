package com.interview.question;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findByInterviewIdOrderByOrderNoAscIdAsc(Long interviewId);

    /** 채용 전체의 준비된 질문(주어진 출처, 숨기지 않은 것) 집계: [전체 수, 물은 수] 한 행. */
    @Query("select count(q), sum(case when q.asked = true then 1 else 0 end) from Question q "
            + "where q.hidden = false and q.source in :sources and q.interviewId in (select i.id from Interview i where i.recruitmentId = :rid)")
    List<Object[]> summarizePreparedOfRecruitment(@Param("rid") Long recruitmentId,
            @Param("sources") java.util.Collection<QuestionSource> sources);

    /** 위 집계를 출처별로 나눈 것: [출처, 전체 수, 물은 수] 가 출처마다 한 행. 질문이 없는 출처는 행이 없다. */
    @Query("select q.source, count(q), sum(case when q.asked = true then 1 else 0 end) from Question q "
            + "where q.hidden = false and q.source in :sources and q.interviewId in (select i.id from Interview i where i.recruitmentId = :rid) "
            + "group by q.source")
    List<Object[]> summarizePreparedBySourceOfRecruitment(@Param("rid") Long recruitmentId,
            @Param("sources") java.util.Collection<QuestionSource> sources);

    /** 물은 준비 질문 가운데 전사에서 자동으로 인식된(발화의 matchedQuestionId 로 걸린) 것의 수. */
    @Query("select count(q) from Question q where q.hidden = false and q.asked = true and q.source in :sources "
            + "and q.interviewId in (select i.id from Interview i where i.recruitmentId = :rid) "
            + "and q.id in (select u.matchedQuestionId from Utterance u where u.roomId in (select r.id from Room r where r.interviewId in (select i.id from Interview i where i.recruitmentId = :rid)))")
    long countAutoMatchedOfRecruitment(@Param("rid") Long recruitmentId,
            @Param("sources") java.util.Collection<QuestionSource> sources);
}

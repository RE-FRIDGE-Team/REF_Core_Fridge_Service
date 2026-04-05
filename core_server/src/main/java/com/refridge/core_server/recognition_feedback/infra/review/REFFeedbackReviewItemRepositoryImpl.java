package com.refridge.core_server.recognition_feedback.infra.review;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.refridge.core_server.recognition_feedback.domain.review.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.refridge.core_server.recognition_feedback.domain.review.QREFFeedbackReviewItem.rEFFeedbackReviewItem;

/**
 * {@link REFFeedbackReviewItemRepositoryCustom}의 QueryDSL 구현체입니다.
 *
 * <h3>정렬 기준</h3>
 * <p>
 * 모든 목록 조회에서 {@code occurrenceCount} 내림차순을 우선 정렬로 사용합니다.
 * 동일한 유형과 대상값에 대해 여러 피드백이 누적될수록 횟수가 증가하므로,
 * 빈도가 높을수록 실제 문제일 가능성이 높아 관리자가 먼저 처리해야 합니다.
 * 동점이면 {@code createdAt} 오름차순(오래된 순)으로 처리합니다.
 * </p>
 */
@Repository
@RequiredArgsConstructor
public class REFFeedbackReviewItemRepositoryImpl implements REFFeedbackReviewItemRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    /**
     * 상태와 유형으로 필터링한 검수 항목을 발생 횟수 내림차순으로 페이징 조회합니다.
     * <p>
     * 실행 쿼리: 2개 (content SELECT + count SELECT)
     * null 파라미터는 해당 조건을 생략합니다 (전체 조회).
     */
    @Override
    public Page<REFFeedbackReviewItem> findByStatusAndType(
            REFReviewStatus status, REFReviewType type, Pageable pageable) {

        BooleanBuilder predicate = new BooleanBuilder();
        if (status != null) predicate.and(rEFFeedbackReviewItem.status.eq(status));
        if (type != null)   predicate.and(rEFFeedbackReviewItem.reviewType.eq(type));

        List<REFFeedbackReviewItem> content = queryFactory
                .selectFrom(rEFFeedbackReviewItem)
                .where(predicate)
                .orderBy(
                        rEFFeedbackReviewItem.occurrenceCount.desc(),
                        rEFFeedbackReviewItem.timeMetaData.createdAt.asc()
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(rEFFeedbackReviewItem.count())
                .from(rEFFeedbackReviewItem)
                .where(predicate)
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    /**
     * ML 학습 대기({@link REFReviewStatus#ML_TRAINING_PENDING}) 항목을 조회합니다.
     * <p>
     * 실행 쿼리: 1개
     * Spring Batch CSV 내보내기 잡의 Reader에서 사용됩니다.
     */
    @Override
    public List<REFFeedbackReviewItem> findMlTrainingPendingItems(int limit) {
        return queryFactory
                .selectFrom(rEFFeedbackReviewItem)
                .where(rEFFeedbackReviewItem.status.eq(REFReviewStatus.ML_TRAINING_PENDING))
                .orderBy(rEFFeedbackReviewItem.timeMetaData.createdAt.asc())
                .limit(limit)
                .fetch();
    }

    /**
     * 터미널 상태(APPROVED/REJECTED) 항목의 ID 목록을 조회합니다.
     * <p>
     * 실행 쿼리: 1개
     * Spring Batch 일괄 삭제 Step의 Reader에서 사용됩니다.
     * 생성일 오름차순으로 오래된 항목부터 삭제 대상으로 선정합니다.
     */
    @Override
    public List<Long> findTerminalItemIdsForCleanup(int limit) {
        return queryFactory
                .select(rEFFeedbackReviewItem.id)
                .from(rEFFeedbackReviewItem)
                .where(rEFFeedbackReviewItem.status.in(
                        REFReviewStatus.APPROVED, REFReviewStatus.REJECTED))
                .orderBy(rEFFeedbackReviewItem.timeMetaData.createdAt.asc())
                .limit(limit)
                .fetch();
    }

    /**
     * 특정 상태의 항목 수를 조회합니다.
     * <p>
     * 실행 쿼리: 1개 (COUNT)
     */
    @Override
    public long countByStatus(REFReviewStatus status) {
        Long result = queryFactory
                .select(rEFFeedbackReviewItem.count())
                .from(rEFFeedbackReviewItem)
                .where(rEFFeedbackReviewItem.status.eq(status))
                .fetchOne();
        return result != null ? result : 0L;
    }
}

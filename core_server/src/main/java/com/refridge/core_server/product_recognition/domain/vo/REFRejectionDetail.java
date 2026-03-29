package com.refridge.core_server.product_recognition.domain.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 비식재료 반려 시 매칭된 키워드를 저장하는 Embedded VO입니다.
 * <p>
 * [신규 추가] 기존에는 REJECTED 상태만 저장하고 "왜 반려되었는지"가 소실되었습니다.
 * 이 VO를 통해 어떤 비식재료 키워드에 매칭되어 반려되었는지 영구 보관합니다.
 * <p>
 * 피드백에서 "비식재료로 반려되었지만 실제로는 식재료인 경우"를 처리할 때
 * 이 키워드를 기반으로 비식재료 사전 수정 후보를 식별합니다.
 */
@Getter
@Embeddable
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class REFRejectionDetail {

    /** 비식재료 사전에서 매칭된 키워드 (e.g. "샴푸", "세제") */
    @Column(name = "rejection_matched_keyword")
    private String matchedKeyword;

    public static REFRejectionDetail of(String matchedKeyword) {
        return new REFRejectionDetail(matchedKeyword);
    }

    public static REFRejectionDetail empty() {
        return new REFRejectionDetail(null);
    }

    public boolean hasKeyword() {
        return matchedKeyword != null && !matchedKeyword.isBlank();
    }
}
package com.refridge.core_server.grocery_category.domain.vo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum REFCategoryColorTag {
    SOFT_RED("#E8A0A0"),       // 부드러운 레드
    WARM_ORANGE("#E8C4A0"),   // 따뜻한 오렌지
    MUTED_YELLOW("#E2D8A0"),  // 차분한 옐로우
    SAGE_GREEN("#A0C8A0"),    // 세이지 그린
    SKY_BLUE("#A0C4E0"),      // 스카이 블루
    SOFT_INDIGO("#A8A8D8"),   // 부드러운 인디고
    LAVENDER("#C8A8D8"),      // 라벤더
    ROSE_PINK("#D8A8C0");     // 로즈 핑크

    private final String hexCode;

    public static REFCategoryColorTag fromMajorCategoryId(Long majorCategoryId) {
        int index = (int) ((majorCategoryId - 1) % REFCategoryColorTag.values().length);
        return REFCategoryColorTag.values()[index];
    }
}

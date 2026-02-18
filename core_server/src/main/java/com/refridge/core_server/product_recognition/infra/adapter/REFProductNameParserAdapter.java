package com.refridge.core_server.product_recognition.infra.adapter;

import com.refridge.core_server.product_recognition.domain.port.REFBrandMatcher;
import com.refridge.core_server.product_recognition.domain.port.REFProductNameParser;
import com.refridge.core_server.product_recognition.domain.vo.REFParsedProductName;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 제품명 파싱 구현체.<pre></pre>
 * 파싱 순서:
 * <pre>
 * 1. 브랜드명 추출 (Aho-Corasick 사전 매칭)
 * 2. 용량 추출 (정규식)
 * 3. 수량 추출 (정규식)
 * 4. 불필요 요소 제거 (가격, 할인, 배송, 평점 등)
 * 5. 정제된 제품명 생성
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class REFProductNameParserAdapter implements REFProductNameParser {

    private final REFBrandMatcher brandMatcher;

    // 용량 패턴
    private static final Pattern VOLUME_PATTERN = Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)(g|kg|ml|l|L|리터)(?!\\s*\\([^)]*\\))(?![a-zA-Z가-힣])",
            Pattern.CASE_INSENSITIVE
    );

    // 수량 패턴
    private static final Pattern QUANTITY_PATTERN = Pattern.compile(
            "(?:\\d+~)?(\\d+)\\s*(?:개입?|입|팩|봉|미|구|포|세트|박스|p|매|정|본|마리)(?!\\s*[+x×X])"
    );

    // 제거할 패턴들
    private static final Pattern[] NOISE_PATTERNS = {
            // 대괄호 패턴
            Pattern.compile("^\\s*\\[[^\\]]+\\]\\s*"),  // 맨 앞
            Pattern.compile(",?\\s*\\[[^\\]]+\\]\\s*"),  // 중간/끝

            // 이중 괄호
            Pattern.compile("\\(\\([^)]+\\)\\)"),

            // 원산지
            Pattern.compile("\\([가-힣A-Z]{2,10}\\s*산?\\)"),

            // 수량/한정/증정
            Pattern.compile("\\(한정수량\\)"),
            Pattern.compile("\\+\\s*[^,]+증정"),

            // 일분 단위 (건강기능식품)
            Pattern.compile("\\(\\d+일분\\)"),

            // 종+택1 패턴
            Pattern.compile("\\d+종\\s*\\(택\\d+\\)"),

            // 도수
            Pattern.compile("\\d+도(?=\\d{3}|\\s|$|,)"),

            // "17가지 재료" 패턴 제거됨 - 제품 특징이므로 유지

            // 시즌/보관 정보 (괄호 안)
            Pattern.compile("\\([^)]*(?:하우스|햇|후숙|필요|익혀|손질)[^)]*\\)"),

            // 과일 등급 (괄호 안)
            Pattern.compile("\\(로얄과\\)"),

            // 가격 정보
            Pattern.compile("\\d+%\\d{1,3}(?:,\\d{3})*원"),
            Pattern.compile("할인\\d+%\\d{1,3}(?:,\\d{3})*원"),
            Pattern.compile("\\d{1,3}(?:,\\d{3})*원"),
            Pattern.compile("\\((?:100g|10g|100ml|10ml|1개|1세트|1구|1정|1본)당\\s*\\d{1,3}(?:,\\d{3})*원\\)"),

            // 할인 정보
            Pattern.compile("(?:할인|쿠폰할인|쿠폰)\\s*\\d+%"),
            Pattern.compile("^\\d+%(?=\\d{1,3}(?:,\\d{3})*원)"),

            // 배송 정보
            Pattern.compile("내일\\([^)]+\\)\\s*(?:새벽\\s*)?도착\\s*보장"),
            Pattern.compile("\\d{1,2}/\\d{1,2}\\([^)]+\\)\\s*도착\\s*예정"),
            Pattern.compile("\\(예약일\\s*수령\\)"),

            // 평점/리뷰
            Pattern.compile("[0-9.]+\\s*\\(\\d+\\)"),

            // 적립금
            Pattern.compile("최대\\s*\\d{1,3}(?:,\\d{3})*원\\s*적립"),
            Pattern.compile("\\d{1,3}(?:,\\d{3})*원\\s*와우\\s*가입\\s*쿠폰"),

            // 최저가
            Pattern.compile("최저\\d{1,3}(?:,\\d{3})*원"),

            // 상품 상태
            Pattern.compile("새\\s*상품(?:,\\s*반품(?:-미개봉)?)?\\s*\\(\\d+\\)"),

            // 등급 (1+등급 포함)
            Pattern.compile(",\\s*(?:[0-9A-Z+]+)?(?:특등급|상등급|1등급|2등급|등급|1\\+등급)(?=\\s*$|,|\\s+\\d)"),
            Pattern.compile("\\((?:[가-힣A-Z]+)?등급\\)"),

            // 상태 표현 (괄호 안)
            Pattern.compile("\\((?:냉동|냉장|분쇄안함|홀빈|원두|등급\\s*:[^)]+|생물)\\)"),

            // 품종/크기 정보
            Pattern.compile("\\(품종:[^)]+\\)"),
            Pattern.compile("\\((?:대과|중과|소과|특대과|대|중|소)(?:,\\s*\\d+~\\d+입)?\\)"),
            Pattern.compile("\\d+(?:[.,]\\d+)?(?:g|kg|ml|l|L|리터)\\s*\\((?:대|중|소)(?:과)?\\)"),
            Pattern.compile("\\d+(?:[.,]\\d+)?(?:g|kg|ml|l|L|리터)\\s*\\(\\d+~\\d+\\)"),

            // 택1
            Pattern.compile("\\(택\\d+\\)"),

            // 인증 문구
            Pattern.compile("(?:식약청|유기가공식품|HACCP|ISO|GAP|해썹|동물복지|무항생제|무농약)\\s*인증"),

            // 당도/산도
            Pattern.compile("\\d+(?:\\.\\d+)?\\s*brix\\s*이상"),
            Pattern.compile("\\d+(?:\\.\\d+)?\\s*brix"),
            Pattern.compile("산도\\s*\\d+(?:\\.\\d+)?"),

            // 조리시간
            Pattern.compile("\\d+분(?:\\s*\\d+초)?"),

            // 영양성분
            Pattern.compile("\\d+(?:[.,]\\d+)?\\s*(?:mg|mcg|g|kcal|IU)(?![a-zA-Z가-힣])"),

            // 개수 범위
            Pattern.compile("\\d+~(?=\\d+(?:개입?|입|팩|봉|미|구|포|세트|박스|p|매|정|본|마리))"),

            // x 곱셈 표현
            Pattern.compile("\\d+(?:[.,]\\d+)?(?:g|kg|ml|l|L|리터)?\\s*[xX×]\\s*\\d+\\s*(?:개입?|입|p|팩|ea)?"),

            // + 추가 구성
            Pattern.compile("\\+\\s*[^,]+\\d+(?:[.,]\\d+)?(?:g|kg|ml|l|L|리터)\\s*[xX×]\\s*\\d+\\s*(?:개입?|입|p|ea)?")
    };

    @Override
    public REFParsedProductName parse(String rawProductName) {
        if (rawProductName == null || rawProductName.isBlank()) {
            return REFParsedProductName.builder().originalText(rawProductName).build();
        }

        String working = rawProductName.trim();

        // 1. 브랜드명 추출 및 제거
        String brandName = extractBrand(working);
        if (brandName != null) {
            working = working.replace(brandName, "").trim();
        }

        // 2. 용량 추출 (노이즈 제거 전에 먼저 추출)
        VolumeInfo volumeInfo = extractVolume(working);

        // 3. 수량 추출 (노이즈 제거 전에 먼저 추출)
        Integer quantity = extractQuantity(working);

        // 4. 불필요 요소 제거
        String refined = removeNoisePatterns(working);

        // 5. 용량/수량 정보 제거 (정제된 제품명에서는 제외)
        refined = removeVolumeAndQuantity(refined);

        // 6. 최종 정제
        refined = finalCleanup(refined);

        log.debug("파싱 완료 - 원본: '{}' → 정제: '{}', 브랜드: '{}', 용량: '{}', 수량: {}",
                rawProductName, refined, brandName, volumeInfo.volumeText, quantity);

        return REFParsedProductName.builder()
                .originalText(rawProductName)
                .refinedText(refined)
                .brandName(brandName)
                .quantity(quantity)
                .volume(volumeInfo.volumeText)
                .volumeUnit(volumeInfo.unit)
                .build();
    }

    private String extractBrand(String text) {
        return brandMatcher.findMatch(text).orElse(null);
    }

    private VolumeInfo extractVolume(String text) {
        Matcher matcher = VOLUME_PATTERN.matcher(text);

        if (matcher.find()) {
            String value = matcher.group(1).replace(",", ".");
            String unit = matcher.group(2).toLowerCase();
            if ("리터".equals(unit)) {
                unit = "l";
            }
            return new VolumeInfo(value + unit, unit);
        }
        return new VolumeInfo(null, null);
    }

    private Integer extractQuantity(String text) {
        Matcher matcher = QUANTITY_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                log.warn("수량 파싱 실패: {}", matcher.group(1));
            }
        }
        return null;
    }

    private String removeNoisePatterns(String text) {
        String result = text;
        for (Pattern pattern : NOISE_PATTERNS) {
            result = pattern.matcher(result).replaceAll("");
        }
        return result;
    }

    private String removeVolumeAndQuantity(String text) {
        String result = text;
        result = VOLUME_PATTERN.matcher(result).replaceAll("");
        result = QUANTITY_PATTERN.matcher(result).replaceAll("");
        return result;
    }

    private String finalCleanup(String text) {
        return text
                .replaceAll(",+", ",")
                .replaceAll("\\s*,\\s*", " ")
                .replaceAll("\\s+", " ")
                .replaceAll("\\(\\s*\\)", "")
                .replaceAll("\\[\\s*\\]", "")
                .trim()
                .replaceAll("^[,\\s]+|[,\\s]+$", "")
                .trim();
    }

    private record VolumeInfo(String volumeText, String unit) {
    }
}
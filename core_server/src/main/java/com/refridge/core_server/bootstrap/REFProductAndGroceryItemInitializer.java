package com.refridge.core_server.bootstrap;


import com.refridge.core_server.bootstrap.dto.REFCsvProductRowDto;
import com.refridge.core_server.groceryItem.application.REFGroceryItemLifeCycleService;
import com.refridge.core_server.groceryItem.application.dto.result.REFGroceryItemUpsertResult;
import com.refridge.core_server.groceryItem.domain.vo.REFGroceryItemClassification;
import com.refridge.core_server.grocery_category.domain.vo.REFInventoryItemType;
import com.refridge.core_server.product.application.REFProductLifeCycleService;
import com.refridge.core_server.product_recognition.domain.port.REFProductNameParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.input.BOMInputStream;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Order(3)
@Component
@RequiredArgsConstructor
public class REFProductAndGroceryItemInitializer implements ApplicationRunner {

    private static final String CSV_PATH = "init/grocery_items.csv";

    private final REFGroceryItemLifeCycleService groceryItemLifeCycleService;

    private final REFProductLifeCycleService productLifeCycleService;

    private final REFProductNameParser productNameParser;

    @Override
    public void run(ApplicationArguments args) {
        List<REFCsvProductRowDto> rows = loadCsv();
        if (rows.isEmpty()) {
            log.warn("[CSV 초기화] 로드된 데이터 없음: {}", CSV_PATH);
            return;
        }

        int created = 0, skipped = 0, failed = 0;

        for (REFCsvProductRowDto row : rows) {
            try {
                boolean isCreated = processRow(row);
                created += isCreated ? 1 : 0;
                skipped += isCreated ? 0 : 1;
            } catch (Exception e) {
                // 한 행 실패가 전체를 막지 않도록 — 멱등성으로 재실행 가능
                log.error("[CSV 초기화] 행 처리 실패: {} / 사유: {}", row.originalProductName(), e.getMessage());
                failed++;
            }
        }

        log.info("[CSV 초기화] 완료 — 전체: {}행, 신규: {}, 스킵: {}, 실패: {}",
                rows.size(), created, skipped, failed);
    }

    /**
     * 한 행을 처리합니다. 트랜잭션은 각 서비스 내부에서 관리합니다.
     *
     * @return true=신규 Product 생성, false=이미 존재하여 SKIP
     */
    private boolean processRow(REFCsvProductRowDto row) {
        // GroceryItem 컨텍스트 트랜잭션
        REFInventoryItemType itemType = REFInventoryItemType.from(row.inventoryItemType());
        REFGroceryItemClassification classification = REFGroceryItemClassification.fromMinorCategoryNameAndInventoryTypeCode(row.subCategory(), itemType);
        REFGroceryItemUpsertResult groceryResult = groceryItemLifeCycleService.upsert(
                row.groceryItemName(),
                row.majorCategory(),
                row.subCategory(),
                classification
        );

        // Product 컨텍스트 트랜잭션
        productLifeCycleService.upsertProduct(
                row.originalProductName(),
                row.brandName(),
                groceryResult.groceryItemId(),
                groceryResult.majorCategoryId(),
                groceryResult.minorCategoryId()
        );

        return groceryResult.created();
    }

    private List<REFCsvProductRowDto> loadCsv() {
        ClassPathResource resource = new ClassPathResource(CSV_PATH);

        if (!resource.exists()) {
            log.warn("[CSV 로더] 파일 없음: {}", CSV_PATH);
            return List.of();
        }

        List<REFCsvProductRowDto> rows = new ArrayList<>();

        try (Reader reader = new InputStreamReader(BOMInputStream.builder()
                .setInputStream(resource.getInputStream())
                .get(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT
                     .builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .get()
                     .parse(reader)) {

            for (CSVRecord record : parser) {
                rows.add(new REFCsvProductRowDto(
                        productNameParser.parse(record.get("product_name")).refinedText(),
                        record.get("category_large"),
                        record.get("category_medium"),
                        record.get("item_type"),
                        record.get("grocery_item_name"),
                        record.get("brand_name")
                ));
            }

        } catch (IOException e) {
            log.error("[CSV 로더] 파싱 실패", e);
        }

        return rows;
    }

}

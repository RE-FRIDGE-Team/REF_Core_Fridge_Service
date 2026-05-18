package com.refridge.core_server.groceryItem.presentation;

import com.refridge.core_server.groceryItem.application.REFGroceryItemMetadataService;
import com.refridge.core_server.groceryItem.application.dto.query.REFGroceryItemMetadataQuery;
import com.refridge.core_server.groceryItem.application.dto.result.REFGroceryItemMetadataResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * fridge_server 등 외부 마이크로서비스에 노출되는 GroceryItem REST API입니다.
 *
 * <h3>엔드포인트</h3>
 * <ul>
 *   <li>{@code GET /grocery-items/{id}/metadata} — 사용량 환산 메타데이터 조회</li>
 *   <li>{@code PUT /grocery-items/{id}/metadata} — 메타데이터 일괄 갱신 (관리자용)</li>
 *   <li>{@code POST /grocery-items/{id}/metadata/apply-default} — 카테고리 디폴트 프로필 적용 (관리자용)</li>
 * </ul>
 *
 * <h3>fridge_server 측 매핑</h3>
 * <p>
 * {@code HttpGroceryItemAdapter}가 응답 본문을
 * {@code CoreServerGroceryItemResponse}로 역직렬화하여 사용합니다.
 * </p>
 *
 * @author 이승훈
 * @since 2026. 5. 7. (확장: 2026. 5. 9.)
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/grocery-items")
public class REFGroceryItemController {

    private final REFGroceryItemMetadataService metadataService;

    @GetMapping("/{id}/metadata")
    public ResponseEntity<REFGroceryItemMetadataResponse> getMetadata(@PathVariable Long id) {
        log.debug("[GroceryItem 메타데이터 조회] id={}", id);

        REFGroceryItemMetadataResult result = metadataService.getMetadata(
                REFGroceryItemMetadataQuery.builder().groceryItemId(id).build()
        );

        return ResponseEntity.ok(REFGroceryItemMetadataResponse.from(result));
    }

    @PutMapping("/{id}/metadata")
    public ResponseEntity<Void> updateMetadata(
            @PathVariable Long id,
            @Valid @RequestBody REFGroceryItemMetadataUpdateRequest request) {

        log.info("[GroceryItem 메타데이터 갱신 요청] id={}, request={}", id, request);
        metadataService.updateMetadata(request.toCommand(id));
        return ResponseEntity.noContent().build();
    }

    /**
     * 단건 GroceryItem에 카테고리 디폴트 프로필을 적용합니다.
     * <p>
     * 운영 중 새로 생성된 GroceryItem에 디폴트를 채우거나,
     * 부트스트랩 후 새로 추가된 식재료에 사용합니다.
     * </p>
     *
     * @param id       대상 GroceryItem ID
     * @param override true면 기존 값 강제 덮어쓰기, false(기본)면 기존 값 보존
     */
    @PostMapping("/{id}/metadata/apply-default")
    public ResponseEntity<Void> applyDefaultProfile(
            @PathVariable Long id,
            @RequestParam(name = "override", defaultValue = "false") boolean override) {

        log.info("[GroceryItem 디폴트 프로필 적용 요청] id={}, override={}", id, override);
        boolean applied = metadataService.applyDefaultProfile(id, override);

        return applied
                ? ResponseEntity.noContent().build()
                : ResponseEntity.unprocessableEntity().build();
    }
}
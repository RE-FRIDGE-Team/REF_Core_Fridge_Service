package com.refridge.core_server.recognition_feedback.infra.adapter;

import com.refridge.core_server.groceryItem.domain.REFGroceryItemRepository;
import com.refridge.core_server.recognition_feedback.domain.port.REFGroceryItemExistencePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link REFGroceryItemExistencePort}의 구현체입니다.
 *
 * <p>
 * GroceryItem Context의 {@code REFGroceryItemRepository}를 직접 사용합니다.
 * 이미 {@code REFGroceryItemQueryAdapter}가 같은 Repository를 사용하는 선례가 있으므로
 * 동일한 패턴을 따릅니다.
 * </p>
 *
 * @author 이승훈
 * @since 2026. 4. 6.
 */
@Component
@RequiredArgsConstructor
public class REFGroceryItemExistenceAdapter implements REFGroceryItemExistencePort {

    private final REFGroceryItemRepository groceryItemRepository;

    @Override
    public boolean existsByName(String groceryItemName) {
        if (groceryItemName == null || groceryItemName.isBlank()) return false;
        return groceryItemRepository.findByGroceryItemName(groceryItemName).isPresent();
    }
}
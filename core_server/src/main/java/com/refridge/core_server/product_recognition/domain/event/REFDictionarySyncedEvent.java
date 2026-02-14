package com.refridge.core_server.product_recognition.domain.event;

import com.refridge.core_server.product_recognition.domain.vo.REFRecognitionDictionaryType;

/**
 * 식재료 사전이 동기화된 이벤트
 * - 사전이 업데이트되면 이 이벤트가 발행되고, 이를 구독하는 컴포넌트들이 사전 변경에 대응하여 필요한 작업을 수행할 수 있도록 함
 * - 예: Batch로 GroceryItem이 Pending -> Active로 변경 => REFRecognitionDictionary가 갱신 => REFDictionarySyncedEvent 발행 => 이를 구독하는 컴포넌트들이 사전 변경에 대응하여 필요한 작업 수행
 */
public record REFDictionarySyncedEvent(
        REFRecognitionDictionaryType dictionaryType
) {
}

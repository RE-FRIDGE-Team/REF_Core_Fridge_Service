package com.refridge.core_server.product_recognition.infra.sync;

import com.refridge.core_server.product_recognition.domain.event.REFDictionarySyncedEvent;
import com.refridge.core_server.product_recognition.infra.adapter.REFAhoCorasickExclusionWordMatcher;
import com.refridge.core_server.product_recognition.infra.adapter.REFAhoCorasickGroceryItemDictionaryMatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class REFDictionarySyncEventHandler {

    private final REFTrieMatcherRegistry trieMatcherRegistry;

    @EventListener
    public void handle(REFDictionarySyncedEvent event) {
        trieMatcherRegistry.getMatcher(event.dictionaryType()).rebuild();
        log.info("{} Trie 재빌드 이벤트 처리 완료", event.dictionaryType().getKorCode());
    }
}

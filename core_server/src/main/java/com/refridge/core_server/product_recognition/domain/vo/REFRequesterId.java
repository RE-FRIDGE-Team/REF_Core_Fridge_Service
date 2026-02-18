package com.refridge.core_server.product_recognition.domain.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Embeddable
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class REFRequesterId {

    @Getter
    @Column(name = "requester_id", nullable = false)
    private UUID id;

    public static REFRequesterId of(String idValue) {
        return new REFRequesterId(UUID.fromString(idValue));
    }
}

package com.refridge.core_server.groceryItem.domain.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

@Getter
@Embeddable
@EqualsAndHashCode
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class REFRepresentativeImage {

    @Column(name = "image_url")
    private String presignedUrl;

    public static REFRepresentativeImage of(String presignedUrl) {
        return new REFRepresentativeImage(presignedUrl);
    }
}

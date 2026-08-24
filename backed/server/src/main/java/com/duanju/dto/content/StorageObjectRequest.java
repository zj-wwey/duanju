package com.duanju.dto.content;

import jakarta.validation.constraints.NotBlank;

public record StorageObjectRequest(
        String storageProvider,
        @NotBlank String url
) {
}

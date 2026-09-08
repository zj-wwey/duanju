package com.duanju.dto.content;

import com.duanju.service.DramaService;
import com.duanju.util.MapUtil;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record EpisodeRequest(
        @NotNull Long dramaId,
        @NotNull Integer episodeNo,
        @NotBlank String title,
        String description,
        String coverUrl,
        String videoUrl,
        String cloudflareUid,
        Integer pricePoints,
        Integer durationSeconds,
        Boolean isFree,
        String accessType,
        Integer sortOrder,
        String storageProvider,
        Integer status,
        String coverObjectKey
) {
    public Map<String, Object> toMap(Long id) {
        String normalizedAccessType = DramaService.normalizeAccessType(accessType, isFree);
        boolean free = "FREE".equals(normalizedAccessType);
        return MapUtil.map(
                "id", id, "dramaId", dramaId, "episodeNo", episodeNo, "title", title,
                "description", description, "coverUrl", coverUrl, "videoUrl", videoUrl,
                "cloudflareUid", cloudflareUid,
                "pricePoints", free ? 0 : (pricePoints == null ? 10 : pricePoints),
                "durationSeconds", durationSeconds == null ? 0 : durationSeconds,
                "isFree", free ? 1 : 0, "accessType", normalizedAccessType,
                "sortOrder", sortOrder == null ? episodeNo : sortOrder,
                "storageProvider", storageProvider == null ? "oss" : storageProvider,
                "status", status == null ? 1 : status,
                "coverObjectKey", coverObjectKey
        );
    }
}

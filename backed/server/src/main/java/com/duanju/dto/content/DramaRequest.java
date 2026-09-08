package com.duanju.dto.content;

import com.duanju.service.DramaService;
import com.duanju.util.MapUtil;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

public record DramaRequest(
        @NotBlank String title,
        String description,
        String coverUrl,
        String horizontalCoverUrl,
        String verticalCoverUrl,
        String tags,
        Integer freeEpisodeCount,
        Integer totalEpisodes,
        Integer episodePricePoints,
        Integer wholePricePoints,
        String contentType,
        String background,
        String theme,
        String setting,
        String audience,
        LocalDate publishDate,
        LocalDateTime onlineTime,
        Integer hotScore,
        Boolean recommended,
        Integer status,
        Integer sortOrder,
        String coverObjectKey,
        String horizontalCoverObjectKey,
        String verticalCoverObjectKey
) {
    public Map<String, Object> toMap(Long id) {
        return MapUtil.map(
                "id", id, "title", title, "description", description,
                "horizontalCoverUrl", horizontalCoverUrl, "verticalCoverUrl", verticalCoverUrl, "tags", tags,
                "coverUrl", coverUrl, "freeEpisodeCount", freeEpisodeCount == null ? 0 : freeEpisodeCount,
                "totalEpisodes", totalEpisodes == null ? 0 : totalEpisodes,
                "episodePricePoints", episodePricePoints == null ? 10 : episodePricePoints,
                "wholePricePoints", wholePricePoints == null ? 0 : wholePricePoints,
                "contentType", DramaService.normalizeContentType(contentType),
                "background", DramaService.defaultText(background, "modern"),
                "theme", DramaService.defaultText(theme, "romance"),
                "setting", DramaService.defaultText(setting, "ordinary"),
                "audience", DramaService.defaultText(audience, "female"),
                "publishDate", publishDate == null ? LocalDate.now() : publishDate,
                "onlineTime", onlineTime == null ? LocalDateTime.now() : onlineTime,
                "hotScore", hotScore == null ? 0 : hotScore,
                "recommended", Boolean.TRUE.equals(recommended) ? 1 : 0,
                "status", status == null ? 1 : status,
                "sortOrder", sortOrder == null ? 0 : sortOrder,
                "coverObjectKey", coverObjectKey,
                "horizontalCoverObjectKey", horizontalCoverObjectKey,
                "verticalCoverObjectKey", verticalCoverObjectKey
        );
    }
}

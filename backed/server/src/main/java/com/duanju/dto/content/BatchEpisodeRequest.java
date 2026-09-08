package com.duanju.dto.content;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record BatchEpisodeRequest(
        @NotNull Long dramaId,
        @NotNull Integer startEpisodeNo,
        @Valid List<EpisodePayload> episodes
) {
    public record EpisodePayload(
            Integer episodeNo,
            String title,
            String description,
            String coverUrl,
            String videoUrl,
            Integer pricePoints,
            Integer durationSeconds,
            String accessType,
            Integer sortOrder,
            String storageProvider,
            Integer status,
            String coverObjectKey
    ) {
    }
}

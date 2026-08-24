package com.duanju.dto.user;

import jakarta.validation.constraints.NotNull;

public record HistoryRequest(
        @NotNull Long dramaId,
        @NotNull Long episodeId,
        Integer progressSeconds
) {
}

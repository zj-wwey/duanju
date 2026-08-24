package com.duanju.dto.user;

import jakarta.validation.constraints.NotNull;

public record UnlockRequest(
        @NotNull Long episodeId
) {
}

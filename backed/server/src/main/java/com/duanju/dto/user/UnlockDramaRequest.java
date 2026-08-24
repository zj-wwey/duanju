package com.duanju.dto.user;

import jakarta.validation.constraints.NotNull;

public record UnlockDramaRequest(
        @NotNull Long dramaId
) {
}

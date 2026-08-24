package com.duanju.dto.user;

import jakarta.validation.constraints.NotNull;

public record SettingsUpdateRequest(
        @NotNull Boolean notice,
        @NotNull Boolean autoNext
) {
}

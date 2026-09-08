package com.duanju.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CommentCreateRequest(
        @NotNull Long dramaId,
        Long episodeId,
        @NotBlank @Size(max = 500) String content
) {
}

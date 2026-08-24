package com.duanju.dto.content;

import com.duanju.service.DramaService;
import com.duanju.util.MapUtil;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record CategoryFilterRequest(
        @NotBlank String groupKey,
        @NotBlank String groupLabelKey,
        Integer groupSortOrder,
        @NotBlank String optionKey,
        @NotBlank String optionLabelKey,
        Integer optionSortOrder,
        Integer status
) {
    public Map<String, Object> toMap(Long id) {
        return MapUtil.map(
                "id", id,
                "groupKey", DramaService.normalizedKey(groupKey),
                "groupLabelKey", groupLabelKey.trim(),
                "groupSortOrder", groupSortOrder == null ? 0 : groupSortOrder,
                "optionKey", DramaService.normalizedKey(optionKey),
                "optionLabelKey", optionLabelKey.trim(),
                "optionSortOrder", optionSortOrder == null ? 0 : optionSortOrder,
                "status", status == null ? 1 : status
        );
    }
}

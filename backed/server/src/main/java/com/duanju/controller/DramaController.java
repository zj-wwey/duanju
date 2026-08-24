package com.duanju.controller;

import com.duanju.common.R;
import com.duanju.service.DramaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DramaController {

    private final DramaService dramaService;

    public DramaController(DramaService dramaService) {
        this.dramaService = dramaService;
    }

    @GetMapping("/category-filters")
    public R<List<Map<String, Object>>> categoryFilters(
            @RequestHeader(value = "X-Locale", required = false) String locale) {
        return R.ok(dramaService.getCategoryFilters(locale));
    }

    @GetMapping("/dramas")
    public R<List<Map<String, Object>>> dramas(@RequestParam(required = false) String contentType,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) String background,
                                               @RequestParam(required = false) String theme,
                                               @RequestParam(required = false) String setting,
                                               @RequestParam(required = false) String audience,
                                               @RequestParam(required = false) String time,
                                               @RequestParam(required = false) String sort,
                                               @RequestHeader(value = "X-Locale", required = false) String locale) {
        return R.ok(dramaService.getDramas(contentType, keyword, background, theme, setting, audience, time, sort, locale));
    }

    @GetMapping("/dramas/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id,
                                         @RequestHeader(value = "X-Locale", required = false) String locale) {
        Map<String, Object> detail = dramaService.getDramaDetail(id, locale);
        if (detail == null || detail.isEmpty()) {
            return R.fail("drama not found");
        }
        return R.ok(detail);
    }
}

package com.duanju.controller;

import com.duanju.common.R;
import com.duanju.service.entity.PointProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class PointProductController {

    private final PointProductService pointProductService;

    public PointProductController(PointProductService pointProductService) {
        this.pointProductService = pointProductService;
    }

    @GetMapping("/point-products")
    public R<List<Map<String, Object>>> products(
            @RequestHeader(value = "X-Locale", required = false) String locale) {
        return R.ok(pointProductService.listWithLocale(locale));
    }
}

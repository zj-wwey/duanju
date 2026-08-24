package com.duanju.controller;

import com.duanju.common.R;
import com.duanju.entity.CurrencyRate;
import com.duanju.security.RequiresPermission;
import com.duanju.service.entity.CurrencyRateService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/currency-rates")
@RequiresPermission("system:manage")
public class CurrencyRateController {

    private final CurrencyRateService currencyRateService;

    public CurrencyRateController(CurrencyRateService currencyRateService) {
        this.currencyRateService = currencyRateService;
    }

    @GetMapping("/list")
    public R<List<CurrencyRate>> list() {
        List<CurrencyRate> rates = currencyRateService.listEnabled();
        return R.ok(rates);
    }

    @GetMapping("/{code}")
    public R<CurrencyRate> getByCode(@PathVariable String code) {
        CurrencyRate rate = currencyRateService.getByCode(code);
        if (rate == null) {
            return R.fail("currency rate not found");
        }
        return R.ok(rate);
    }

    @GetMapping("/locale/{locale}")
    public R<CurrencyRate> getByLocale(@PathVariable String locale) {
        CurrencyRate rate = currencyRateService.getByLocale(locale);
        if (rate == null) {
            return R.fail("currency rate not found");
        }
        return R.ok(rate);
    }

    @GetMapping("/map")
    public R<Map<String, CurrencyRate>> getMap() {
        return R.ok(currencyRateService.getCurrencyMap());
    }

    @PostMapping
    public R<CurrencyRate> create(@RequestBody CurrencyRate rate) {
        currencyRateService.save(rate);
        currencyRateService.refreshCache();
        return R.ok(rate);
    }

    @PutMapping("/{id}")
    public R<CurrencyRate> update(@PathVariable Long id, @RequestBody CurrencyRate rate) {
        rate.setId(id);
        currencyRateService.updateById(rate);
        currencyRateService.refreshCache();
        return R.ok(rate);
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        currencyRateService.removeById(id);
        currencyRateService.refreshCache();
        return R.ok();
    }

    @PostMapping("/refresh")
    public R<Void> refreshCache() {
        currencyRateService.refreshCache();
        return R.ok();
    }
}
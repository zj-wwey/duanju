package com.duanju.service.entity.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.duanju.entity.CurrencyRate;
import com.duanju.mapper.entity.CurrencyRateMapper;
import com.duanju.service.entity.CurrencyRateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CurrencyRateServiceImpl extends ServiceImpl<CurrencyRateMapper, CurrencyRate>
    implements CurrencyRateService {

    private final Map<String, CurrencyRate> cacheByCode = new ConcurrentHashMap<>();
    private final Map<String, CurrencyRate> cacheByLocale = new ConcurrentHashMap<>();

    private static final Map<String, String> LOCALE_TO_CURRENCY = new HashMap<>();
    static {
        LOCALE_TO_CURRENCY.put("en", "USD");
        LOCALE_TO_CURRENCY.put("en-US", "USD");
        LOCALE_TO_CURRENCY.put("en-GB", "USD");
        LOCALE_TO_CURRENCY.put("zh-Hans", "USD");
        LOCALE_TO_CURRENCY.put("zh-CN", "USD");
        LOCALE_TO_CURRENCY.put("zh-Hant", "USD");
        LOCALE_TO_CURRENCY.put("zh-TW", "USD");
        LOCALE_TO_CURRENCY.put("ja", "USD");
        LOCALE_TO_CURRENCY.put("ja-JP", "USD");
        LOCALE_TO_CURRENCY.put("ko", "USD");
        LOCALE_TO_CURRENCY.put("ko-KR", "USD");
        LOCALE_TO_CURRENCY.put("th", "USD");
        LOCALE_TO_CURRENCY.put("th-TH", "USD");
        LOCALE_TO_CURRENCY.put("vi", "USD");
        LOCALE_TO_CURRENCY.put("vi-VN", "USD");
        LOCALE_TO_CURRENCY.put("id", "USD");
        LOCALE_TO_CURRENCY.put("id-ID", "USD");
        LOCALE_TO_CURRENCY.put("ms", "USD");
        LOCALE_TO_CURRENCY.put("ms-MY", "USD");
        LOCALE_TO_CURRENCY.put("es", "USD");
        LOCALE_TO_CURRENCY.put("es-ES", "USD");
        LOCALE_TO_CURRENCY.put("fr", "USD");
        LOCALE_TO_CURRENCY.put("fr-FR", "USD");
        LOCALE_TO_CURRENCY.put("de", "USD");
        LOCALE_TO_CURRENCY.put("de-DE", "USD");
        LOCALE_TO_CURRENCY.put("it", "USD");
        LOCALE_TO_CURRENCY.put("it-IT", "USD");
        LOCALE_TO_CURRENCY.put("pt", "USD");
        LOCALE_TO_CURRENCY.put("pt-BR", "USD");
        LOCALE_TO_CURRENCY.put("ru", "USD");
        LOCALE_TO_CURRENCY.put("ru-RU", "USD");
        LOCALE_TO_CURRENCY.put("tr", "USD");
        LOCALE_TO_CURRENCY.put("tr-TR", "USD");
        LOCALE_TO_CURRENCY.put("ar", "USD");
        LOCALE_TO_CURRENCY.put("ar-SA", "USD");
    }

    @Override
    public CurrencyRate getByLocale(String locale) {
        if (locale == null) return getDefault();

        String currencyCode = LOCALE_TO_CURRENCY.get(locale);
        if (currencyCode != null) {
            CurrencyRate resolved = getByCode(currencyCode);
            if (resolved != null) {
                cacheByLocale.put(locale, resolved);
                return resolved;
            }
        }

        CurrencyRate cached = cacheByLocale.get(locale);
        if (cached != null) return cached;

        CurrencyRate rate = lambdaQuery()
            .eq(CurrencyRate::getLocale, locale)
            .eq(CurrencyRate::getEnabled, 1)
            .one();
        if (rate != null) {
            cacheByLocale.put(locale, rate);
            cacheByCode.put(rate.getCurrencyCode(), rate);
            return rate;
        }
        return getDefault();
    }

    @Override
    public CurrencyRate getByCode(String currencyCode) {
        if (currencyCode == null) return getDefault();
        CurrencyRate cached = cacheByCode.get(currencyCode);
        if (cached != null) return cached;

        CurrencyRate rate = lambdaQuery()
            .eq(CurrencyRate::getCurrencyCode, currencyCode)
            .eq(CurrencyRate::getEnabled, 1)
            .one();
        if (rate != null) {
            cacheByCode.put(currencyCode, rate);
            cacheByLocale.put(rate.getLocale(), rate);
        }
        return rate != null ? rate : getDefault();
    }

    @Override
    public List<CurrencyRate> listEnabled() {
        return lambdaQuery()
            .eq(CurrencyRate::getEnabled, 1)
            .orderByAsc(CurrencyRate::getSortOrder)
            .list();
    }

    @Override
    public int convertFromUsdCents(int usdCents, String targetCurrency) {
        if (targetCurrency == null || targetCurrency.equalsIgnoreCase("USD")) {
            return usdCents;
        }

        CurrencyRate rate = getByCode(targetCurrency);
        if (rate == null) return usdCents;

        BigDecimal usdAmount = BigDecimal.valueOf(usdCents).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        BigDecimal localAmount = usdAmount.multiply(rate.getRateToUsd());

        int decimals = rate.getDecimals() != null ? rate.getDecimals() : 2;
        BigDecimal scaled = localAmount.multiply(BigDecimal.valueOf(Math.pow(10, decimals)));
        return scaled.setScale(0, RoundingMode.HALF_UP).intValue();
    }

    @Override
    public String getCurrencyCodeByLocale(String locale) {
        CurrencyRate rate = getByLocale(locale);
        return rate != null ? rate.getCurrencyCode() : "USD";
    }

    @Override
    public Map<String, CurrencyRate> getCurrencyMap() {
        List<CurrencyRate> rates = listEnabled();
        Map<String, CurrencyRate> map = new HashMap<>();
        for (CurrencyRate rate : rates) {
            map.put(rate.getCurrencyCode(), rate);
            map.put(rate.getLocale(), rate);
        }
        return map;
    }

    private CurrencyRate getDefault() {
        return getByCode("USD");
    }

    @Override
    @Transactional
    public void refreshCache() {
        cacheByCode.clear();
        cacheByLocale.clear();
        listEnabled().forEach(rate -> {
            cacheByCode.put(rate.getCurrencyCode(), rate);
            cacheByLocale.put(rate.getLocale(), rate);
        });
    }
}
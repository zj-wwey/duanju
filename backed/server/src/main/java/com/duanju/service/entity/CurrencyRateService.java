package com.duanju.service.entity;

import com.baomidou.mybatisplus.extension.service.IService;
import com.duanju.entity.CurrencyRate;

import java.util.List;
import java.util.Map;

public interface CurrencyRateService extends IService<CurrencyRate> {

    CurrencyRate getByLocale(String locale);

    CurrencyRate getByCode(String currencyCode);

    List<CurrencyRate> listEnabled();

    int convertFromUsdCents(int usdCents, String targetCurrency);

    String getCurrencyCodeByLocale(String locale);

    Map<String, CurrencyRate> getCurrencyMap();

    void refreshCache();
}
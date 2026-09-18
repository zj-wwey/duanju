package com.duanju.service.entity.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.duanju.entity.CurrencyRate;
import com.duanju.entity.PointProduct;
import com.duanju.mapper.entity.PointProductMapper;
import com.duanju.service.entity.CurrencyRateService;
import com.duanju.service.entity.PointProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PointProductServiceImpl extends ServiceImpl<PointProductMapper, PointProduct> implements PointProductService {

    @Autowired
    private CurrencyRateService currencyRateService;

    @Override
    public List<Map<String, Object>> listWithLocale(String locale) {
        CurrencyRate rate = currencyRateService.getByLocale(locale);
        String targetCurrency = rate != null ? rate.getCurrencyCode() : "USD";

        List<PointProduct> products = lambdaQuery()
            .eq(PointProduct::getStatus, 1)
            .orderByAsc(PointProduct::getSortOrder)
            .list();

        List<Map<String, Object>> result = new ArrayList<>();
        for (PointProduct product : products) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", product.getId());
            item.put("name", product.getName());
            item.put("points", product.getPoints());
            item.put("bonusPoints", product.getBonusPoints());
            item.put("priceCents", product.getPriceCents());
            item.put("originalPriceCents", product.getOriginalPriceCents());
            item.put("durationDays", product.getDurationDays());
            item.put("tagText", product.getTagText());
            item.put("coverUrl", product.getCoverUrl());
            item.put("productCategory", product.getProductCategory() != null ? product.getProductCategory() : "RECHARGE");
            item.put("packageType", product.getPackageType() != null ? product.getPackageType() : "RECHARGE");
            item.put("membershipLevel", product.getMembershipLevel());
            item.put("firstPurchaseBonus", product.getFirstPurchaseBonus());
            item.put("dailyLimit", product.getDailyLimit());
            item.put("monthlyLimit", product.getMonthlyLimit());

            int localPriceCents = currencyRateService.convertFromUsdCents(
                product.getPriceCents(), targetCurrency);
            Integer localOriginalCents = null;
            if (product.getOriginalPriceCents() != null) {
                localOriginalCents = currencyRateService.convertFromUsdCents(
                    product.getOriginalPriceCents(), targetCurrency);
            }

            item.put("localPriceCents", localPriceCents);
            item.put("localOriginalPriceCents", localOriginalCents);
            item.put("currency", targetCurrency);
            item.put("currencySymbol", rate != null ? rate.getSymbol() : "$");
            item.put("currencyDecimals", rate != null ? rate.getDecimals() : 2);

            result.add(item);
        }
        return result;
    }
}

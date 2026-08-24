package com.duanju.service.entity;

import com.baomidou.mybatisplus.extension.service.IService;
import com.duanju.entity.PointProduct;

import java.util.List;
import java.util.Map;

public interface PointProductService extends IService<PointProduct> {

    List<Map<String, Object>> listWithLocale(String locale);
}

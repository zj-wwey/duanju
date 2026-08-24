package com.duanju.service;

import com.duanju.entity.ContentTranslation;
import com.duanju.mapper.entity.ContentTranslationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class ContentTranslationService {

    private static final Logger log = LoggerFactory.getLogger(ContentTranslationService.class);

    private static final String DEFAULT_LOCALE = "zh-CN";
    private static final long CACHE_TTL_MS = 30 * 60 * 1000L;
    private static final int CACHE_MAX_SIZE = 2000;

    private final ContentTranslationMapper mapper;

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler;

    public ContentTranslationService(ContentTranslationMapper mapper) {
        this.mapper = mapper;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "translation-cache-evictor");
            t.setDaemon(true);
            return t;
        });
        this.scheduler.scheduleAtFixedRate(this::evictExpiredEntries, 1, 1, TimeUnit.MINUTES);
    }

    /**
     * 翻译单个字段。
     *
     * @param entityType 实体类型: drama, category_option
     * @param entityId   实体ID或key
     * @param fieldName  字段名: title, synopsis, label
     * @param locale     语言代码
     * @return 翻译文本,若无翻译则返回 null
     */
    public String translate(String entityType, String entityId, String fieldName, String locale) {
        if (locale == null || DEFAULT_LOCALE.equalsIgnoreCase(locale)) {
            return null;
        }
        Map<String, String> translations = getTranslations(entityType, entityId);
        if (translations == null) {
            return null;
        }
        return translations.get(fieldName + ":" + locale);
    }

    /**
     * 批量翻译:将原始 map 中的字段替换为指定 locale 的翻译版本。
     *
     * @param entityType 实体类型
     * @param entityId   实体ID或key
     * @param source     原始字段 map (fieldName -> originalValue)
     * @param locale     目标语言
     * @return 翻译后的字段 map
     */
    public Map<String, String> translateBatch(String entityType, String entityId,
                                              Map<String, String> source, String locale) {
        if (locale == null || DEFAULT_LOCALE.equalsIgnoreCase(locale) || source == null) {
            return source;
        }
        Map<String, String> translations = getTranslations(entityType, entityId);
        if (translations == null) {
            return source;
        }
        Map<String, String> result = new ConcurrentHashMap<>(source);
        for (String fieldName : source.keySet()) {
            String translated = translations.get(fieldName + ":" + locale);
            if (translated != null) {
                result.put(fieldName, translated);
            }
        }
        return result;
    }

    /**
     * 缓存指定实体的所有翻译。
     */
    public void cacheEntity(String entityType, String entityId) {
        String cacheKey = buildCacheKey(entityType, entityId);
        if (cache.size() >= CACHE_MAX_SIZE) {
            evictExpiredEntries();
        }
        Map<String, String> translations = loadTranslations(entityType, entityId);
        cache.put(cacheKey, new CacheEntry(translations, System.currentTimeMillis() + CACHE_TTL_MS));
    }

    /**
     * 清除指定实体的缓存。
     */
    public void evictEntity(String entityType, String entityId) {
        cache.remove(buildCacheKey(entityType, entityId));
    }

    /**
     * 清除所有缓存。
     */
    public void evictAll() {
        cache.clear();
    }

    /**
     * 保存或更新翻译。
     */
    public void saveTranslation(String entityType, String entityId, String fieldName,
                                 String locale, String content) {
        ContentTranslation existing = mapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ContentTranslation>()
                        .eq(ContentTranslation::getEntityType, entityType)
                        .eq(ContentTranslation::getEntityId, entityId)
                        .eq(ContentTranslation::getFieldName, fieldName)
                        .eq(ContentTranslation::getLocale, locale)
        );
        if (existing != null) {
            existing.setContent(content);
            mapper.updateById(existing);
        } else {
            ContentTranslation ct = new ContentTranslation();
            ct.setEntityType(entityType);
            ct.setEntityId(entityId);
            ct.setFieldName(fieldName);
            ct.setLocale(locale);
            ct.setContent(content);
            mapper.insert(ct);
        }
        evictEntity(entityType, entityId);
    }

    private Map<String, String> getTranslations(String entityType, String entityId) {
        String cacheKey = buildCacheKey(entityType, entityId);
        CacheEntry entry = cache.get(cacheKey);
        long now = System.currentTimeMillis();
        if (entry != null && entry.expireAtMs > now) {
            return entry.translations;
        }
        Map<String, String> translations = loadTranslations(entityType, entityId);
        cache.put(cacheKey, new CacheEntry(translations, now + CACHE_TTL_MS));
        return translations;
    }

    private Map<String, String> loadTranslations(String entityType, String entityId) {
        List<ContentTranslation> list = mapper.selectByEntity(entityType, entityId);
        return list.stream()
                .collect(Collectors.toMap(
                        t -> t.getFieldName() + ":" + t.getLocale(),
                        ContentTranslation::getContent,
                        (a, b) -> a
                ));
    }

    private String buildCacheKey(String entityType, String entityId) {
        return entityType + ":" + entityId;
    }

    private void evictExpiredEntries() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(e -> e.getValue().expireAtMs <= now);
    }

    private static class CacheEntry {
        final Map<String, String> translations;
        final long expireAtMs;

        CacheEntry(Map<String, String> translations, long expireAtMs) {
            this.translations = translations;
            this.expireAtMs = expireAtMs;
        }
    }
}

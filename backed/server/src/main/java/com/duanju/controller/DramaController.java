package com.duanju.controller;

import com.duanju.common.R;
import com.duanju.service.DramaService;
import com.duanju.service.UserActionService;
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
    private final UserActionService userActionService;

    public DramaController(DramaService dramaService, UserActionService userActionService) {
        this.dramaService = dramaService;
        this.userActionService = userActionService;
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

    /** 剧集评论列表（公开浏览，白名单 /api/dramas 前缀已放行）；episodeId 非空时按集过滤 */
    @GetMapping("/dramas/{dramaId}/comments")
    public R<Map<String, Object>> getComments(@PathVariable Long dramaId,
                                              @RequestParam(required = false) Long episodeId,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(userActionService.getComments(dramaId, episodeId, page, pageSize));
    }

    /** 某根评论下的回复列表（"查看更多回复"） */
    @GetMapping("/dramas/comments/{rootId}/replies")
    public R<Map<String, Object>> getReplies(@PathVariable Long rootId,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(userActionService.getReplies(rootId, page, pageSize));
    }

    /** Feed 流：每剧一条，附带一集播放信息，分页 */
    @GetMapping("/dramas/feed")
    public R<Map<String, Object>> feed(@RequestParam(required = false) String contentType,
                                       @RequestParam(required = false) Boolean recommended,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "10") int size,
                                       @RequestHeader(value = "X-Locale", required = false) String locale) {
        return R.ok(dramaService.getFeed(contentType, recommended, page, size, locale));
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

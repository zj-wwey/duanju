package com.duanju.controller;

import com.duanju.common.R;
import com.duanju.dto.content.BatchEpisodeRequest;
import com.duanju.dto.content.CategoryFilterRequest;
import com.duanju.dto.content.DramaRequest;
import com.duanju.dto.content.EpisodeRequest;
import com.duanju.dto.content.FreePreviewRequest;
import com.duanju.dto.content.StorageObjectRequest;
import com.duanju.security.RequiresPermission;
import com.duanju.service.DramaService;
import com.duanju.service.StorageService;
import com.duanju.util.MapUtil;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequiresPermission("content:manage")
@RequestMapping("/api/admin")
public class AdminContentController {

    private final DramaService dramaService;
    private final StorageService storageService;

    public AdminContentController(DramaService dramaService, StorageService storageService) {
        this.dramaService = dramaService;
        this.storageService = storageService;
    }

    @GetMapping("/category-filters")
    public R<List<Map<String, Object>>> categoryFilters() {
        return R.ok(dramaService.getAdminCategoryFilters());
    }

    @PostMapping("/category-filters")
    public R<Map<String, Object>> createCategoryFilter(@Valid @RequestBody CategoryFilterRequest request) {
        return R.ok(dramaService.createCategoryFilter(request.toMap(null)));
    }

    @PutMapping("/category-filters/{id}")
    public R<Void> updateCategoryFilter(@PathVariable Long id, @Valid @RequestBody CategoryFilterRequest request) {
        dramaService.updateCategoryFilter(request.toMap(id));
        return R.ok();
    }

    @DeleteMapping("/category-filters/{id}")
    public R<Void> deleteCategoryFilter(@PathVariable Long id) {
        dramaService.deleteCategoryFilter(id);
        return R.ok();
    }

    @GetMapping("/dramas")
    public R<List<Map<String, Object>>> dramas(@RequestParam(required = false) String contentType,
                                               @RequestParam(required = false) Integer status,
                                               @RequestParam(required = false) String keyword) {
        return R.ok(dramaService.getAdminDramas(contentType, status, keyword));
    }

    @PostMapping("/dramas")
    public R<Map<String, Object>> createDrama(@Valid @RequestBody DramaRequest request) {
        return R.ok(dramaService.createDrama(request.toMap(null)));
    }

    @PutMapping("/dramas/{id}")
    public R<Void> updateDrama(@PathVariable Long id, @Valid @RequestBody DramaRequest request) {
        dramaService.updateDrama(request.toMap(id));
        return R.ok();
    }

    @DeleteMapping("/dramas/{id}")
    public R<Void> deleteDrama(@PathVariable Long id) {
        dramaService.deleteDrama(id);
        return R.ok();
    }

    @GetMapping("/dramas/{id}/episodes")
    public R<List<Map<String, Object>>> episodes(@PathVariable Long id,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) String accessType,
                                                 @RequestParam(required = false) Integer status) {
        return R.ok(dramaService.getAdminEpisodes(id, keyword, accessType, status));
    }

    @PostMapping("/dramas/{id}/episodes/free-preview")
    public R<Map<String, Object>> applyFreePreview(@PathVariable Long id,
                                                   @Valid @RequestBody FreePreviewRequest request) {
        int updated = dramaService.applyFreePreview(id, request.freeCount(), request.pricePoints());
        return R.ok(MapUtil.map("updated", updated));
    }

    @PostMapping("/episodes")
    public R<Map<String, Object>> createEpisode(@Valid @RequestBody EpisodeRequest request) {
        return R.ok(dramaService.createEpisode(request.toMap(null), request.dramaId()));
    }

    @PostMapping("/episodes/batch")
    public R<Map<String, Object>> batchCreateEpisodes(@Valid @RequestBody BatchEpisodeRequest request) {
        return R.ok(dramaService.batchCreateEpisodes(request));
    }

    @PutMapping("/episodes/{id}")
    public R<Void> updateEpisode(@PathVariable Long id, @Valid @RequestBody EpisodeRequest request) {
        dramaService.updateEpisode(request.toMap(id), request.dramaId());
        return R.ok();
    }

    @DeleteMapping("/episodes/{id}")
    public R<Void> deleteEpisode(@PathVariable Long id) {
        dramaService.deleteEpisode(id);
        return R.ok();
    }

    @PostMapping("/storage/object")
    public R<Map<String, Object>> saveStorageObject(@Valid @RequestBody StorageObjectRequest request) {
        return R.ok(storageService.saveStorageObject(request.storageProvider(), request.url()));
    }

    @PostMapping(value = "/storage/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                         @RequestParam(defaultValue = "file") String type) {
        return R.ok(storageService.uploadFile(file, type));
    }
}

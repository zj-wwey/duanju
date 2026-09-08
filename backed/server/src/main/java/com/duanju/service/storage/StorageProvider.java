package com.duanju.service.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 对象存储抽象层,统一本地磁盘和 Cloudflare R2 的上传/删除操作。
 *
 * <p>实现方通过 {@code @ConditionalOnProperty(name="duanju.storage.r2.enabled")}
 * 在 Spring 容器中自动切换:</p>
 * <ul>
 *   <li>{@code false} (默认,或属性缺失):注入 {@link LocalStorageProvider}</li>
 *   <li>{@code true}:注入 {@link R2StorageProvider}</li>
 * </ul>
 *
 * <p>{@link com.duanju.service.StorageService} 只依赖本接口,不感知具体实现,
 * 切换存储后端只需改配置,无需改业务代码。</p>
 */
public interface StorageProvider {
    /**
     * 返回存储提供方名称,如 "local" 或 "r2"。
     */
    String getName();
    /**
     * 返回当前提供方是否可用 (配置齐全且客户端已就绪)。
     * 本地实现始终返回 true;R2 实现检查 5 个必填配置项是否非空。
     */
    boolean isEnabled();
    /**
     * 上传字节数组,返回包含 URL/objectKey/size 等元信息的结果。
     *
     * @param bytes       文件内容
     * @param fileName    原始文件名 (用于推断扩展名,可为 null)
     * @param contentType MIME 类型,可为 null
     * @param type        业务类型 (image/video/avatar/file 等),用于决定存储子目录
     */
    UploadResult upload(byte[] bytes, String fileName, String contentType, String type);
    /**
     * 上传 MultipartFile,默认委托给 {@link #upload(byte[], String, String, String)}。
     * 实现方可重写以使用更高效的流式传输 (如本地磁盘的 {@code file.transferTo})。
     */
    default UploadResult upload(MultipartFile file, String type) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is empty");
        }
        try {
            return upload(file.getBytes(), file.getOriginalFilename(), file.getContentType(), type);
        } catch (IOException ex) {
            throw new IllegalArgumentException("read file failed", ex);
        }
    }

    /**
     * 从本地磁盘文件路径直接上传,默认实现:读流委托
     * {@link #upload(byte[], String, String, String)}。
     * <p>对于 500MB 级以上大文件,默认实现会把文件全部读入内存 → OOM 风险。
     * 因此实现方(如 R2)应该重写此方法,改用 S3/R2 SDK 的流式 body 上传,
     * 全程不把整个文件读入 byte[]。</p>
     *
     * @param file        本地文件路径 (批量上传场景)
     * @param fileName    原始文件名 (用于推断扩展名),可为 null,为空时取 {@code file.getFileName()}
     * @param contentType MIME,可为 null
     * @param type        业务类型 (image/video...)
     * @return 上传结果
     */
    default UploadResult uploadFromFile(Path file, String fileName, String contentType, String type) {
        if (file == null) throw new IllegalArgumentException("file is null");
        String actualName = (fileName == null || fileName.isBlank())
                ? String.valueOf(file.getFileName())
                : fileName;
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException ex) {
            throw new IllegalArgumentException("read file failed: " + file, ex);
        }
        return upload(bytes, actualName, contentType, type);
    }

    /**
     * 签发预签名 PUT URL,允许客户端绕过本服务直传到对象存储。
     * 仅 R2 等支持预签名的实现返回非 null;本地实现默认返回 null。
     *
     * <p>调用方拿到结果后,前端用 fetch PUT 文件到 presignedUrl,
     * 完成后将 objectKey/公开 URL 通过保存接口落库。</p>
     *
     * @param fileName    原始文件名 (用于生成 objectKey)
     * @param contentType MIME 类型,可作为签名约束
     * @param type        业务类型 (image/video/avatar/file 等)
     * @param expireSeconds 签名有效期 (秒),实现可限制上限
     * @return 预签名结果;不支持预签名时返回 null
     */
    default PresignResult presignUpload(String fileName, String contentType, String type, long expireSeconds) {
        return null;
    }
    /**
     * 下载对象到本地文件路径。用于HLS转码时从R2拉取原始视频。
     */
    default void download(String objectKey, Path destination) {
        throw new UnsupportedOperationException("download not supported by " + getName());
    }

    /**
     * 上传本地文件到指定objectKey（不自动生成key）。用于HLS分片上传到固定路径。
     */
    default UploadResult uploadToKey(Path file, String objectKey, String contentType) {
        throw new UnsupportedOperationException("uploadToKey not supported by " + getName());
    }

    /**
     * 删除对象。objectKey 不存在不报错,由实现方吞掉 404 / FileNotFoundException。
     */
    void delete(String objectKey);

    /**
     * 按前缀批量删除对象。用于删除剧集时级联清理 HLS 分片 (hls/{episodeId}/ 下的所有 .ts/.m3u8)。
     *
     * @param prefix 对象 key 前缀,如 "hls/123/"
     * @return 实际删除的对象数量
     */
    default int deleteByPrefix(String prefix) {
        throw new UnsupportedOperationException("deleteByPrefix not supported by " + getName());
    }
    /**
     * 上传结果。包含访问 URL 和对象在存储后端的唯一标识。
     *
     * @param url             可访问的公开 URL
     * @param objectKey       对象在存储后端的唯一标识 (本地为相对路径,R2 为 S3 object key)
     * @param path            兼容历史返回的 path 字段 (本地为相对路径,R2 同 objectKey)
     * @param size            文件字节数
     * @param contentType     MIME 类型,可能为 null
     * @param storageProvider 提供方名称 (local/r2)
     */
    record UploadResult(String url, String objectKey, String path, long size, String contentType, String storageProvider) {
    }

    /**
     * 预签名上传结果。
     *
     * @param presignedUrl 客户端 PUT 此 URL 完成上传
     * @param objectKey    对象在存储后端的唯一标识,落库用
     * @param publicUrl    上传完成后通过 CDN/反代访问的公开 URL
     * @param headers      PUT 时必须携带的请求头约束 (如 Content-Type),可能为空
     */
    record PresignResult(String presignedUrl, String objectKey, String publicUrl, java.util.Map<String, String> headers) {
    }
}

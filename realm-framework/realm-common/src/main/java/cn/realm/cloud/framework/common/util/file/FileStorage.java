package cn.realm.cloud.framework.common.util.file;

import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 统一文件存储抽象接口。
 *
 * <p><b>设计目标：</b>屏蔽不同云存储服务商（OSS、MinIO、本地文件系统等）的差异，
 * 为业务层提供统一的文件上传、下载、删除、元数据管理能力。
 *
 * <p><b>抽象模型：</b>
 * <ul>
 *     <li><b>bucket</b>：存储桶 / 命名空间。本地存储实现可将 bucket 映射为子目录。</li>
 *     <li><b>key</b>：对象在 bucket 内的唯一标识（如 {@code "images/avatar/user_123.png"}）。</li>
 *     <li><b>metadata</b>：对象元数据（如 Content-Type、自定义业务标签）。</li>
 * </ul>
 *
 * <p><b>流所有权（重要）：</b>
 * <ul>
 *     <li><b>传入流</b>（{@link #upload(String, String, InputStream, Map)} 的 {@code data}）：
 *         <b>由调用方负责关闭</b>。实现方不得关闭传入流，以便调用方复用或统一在 try-with-resources 中管理。</li>
 *     <li><b>返回流</b>（{@link #downloadStream(String, String)} 的返回值）：
 *         <b>由调用方负责关闭</b>。必须使用 try-with-resources 或手动 {@code close()}。</li>
 * </ul>
 *
 * <p><b>异常契约：</b>
 * <ul>
 *     <li>参数非法（{@code bucket} / {@code key} 为 {@code null} 或空白）：
 *         抛 {@link IllegalArgumentException}。</li>
 *     <li>存储层失败（网络、权限、超时、配额）：
 *         抛 {@link StorageException}。</li>
 *     <li>文件不存在：
 *         {@link #download} / {@link #downloadStream} / {@link #getMetadata}
 *         抛 {@link StorageException}；
 *         {@link #exists} 返回 {@code false}；
 *         {@link #delete} 视为成功（幂等）。</li>
 * </ul>
 *
 * <p><b>线程安全：</b>实现类应保证方法线程安全。本接口的所有方法不保证原子性，
 * 并发场景下的语义由实现类定义。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * FileStorage storage = ...;
 *
 * // 上传字节数组
 * URI url = storage.upload("avatars", "user_123.png", imageBytes, Map.of("Content-Type", "image/png"));
 *
 * // 上传流（调用方负责关闭 data）
 * try (InputStream in = new FileInputStream(file)) {
 *     storage.upload("avatars", "user_123.png", in, null);
 * }
 *
 * // 下载流（调用方负责关闭返回的流）
 * try (InputStream in = storage.downloadStream("avatars", "user_123.png")) {
 *     // 读取...
 * }
 *
 * // 生成临时 URL（10 分钟有效）
 * URI presigned = storage.generatePresignedUrl("avatars", "user_123.png", 600);
 * }</pre>
 *
 * @author QI Guang
 * @since 1.0.0
 */
public interface FileStorage {

    // ==================== 上传 ====================

    /**
     * 上传文件（字节数组）。
     *
     * @param bucket   存储桶，不允许为 {@code null} 或空白
     * @param key      对象键，不允许为 {@code null} 或空白
     * @param data     文件数据，不允许为 {@code null}
     * @param metadata 对象元数据，允许为 {@code null}（表示无元数据）
     * @return 文件访问 URL（永久或有效期由实现决定）
     * @throws IllegalArgumentException 参数非法时抛出
     * @throws StorageException         上传失败时抛出
     */
    URI upload(String bucket, String key, byte[] data, Map<String, String> metadata);

    /**
     * 上传文件（流式）。
     *
     * <p><b>流所有权：</b>本方法<b>不关闭</b> {@code data}，由调用方负责关闭。
     *
     * @param bucket   存储桶，不允许为 {@code null} 或空白
     * @param key      对象键，不允许为 {@code null} 或空白
     * @param data     文件数据流，不允许为 {@code null}
     * @param metadata 对象元数据，允许为 {@code null}
     * @return 文件访问 URL
     * @throws IllegalArgumentException 参数非法时抛出
     * @throws StorageException         上传失败时抛出
     */
    URI upload(String bucket, String key, InputStream data, Map<String, String> metadata);

    // ==================== 下载 ====================

    /**
     * 下载文件（字节数组）。
     *
     * <p>适用于文件较小的场景。大文件请使用 {@link #downloadStream(String, String)}。
     *
     * @param bucket 存储桶
     * @param key    对象键
     * @return 文件字节数组
     * @throws IllegalArgumentException 参数非法时抛出
     * @throws StorageException         下载失败或文件不存在时抛出
     */
    byte[] download(String bucket, String key);

    /**
     * 下载文件（流式）。
     *
     * <p><b>流所有权：</b>返回的流<b>由调用方负责关闭</b>，
     * 必须使用 try-with-resources 或手动 {@code close()}。
     *
     * @param bucket 存储桶
     * @param key    对象键
     * @return 文件输入流
     * @throws IllegalArgumentException 参数非法时抛出
     * @throws StorageException         下载失败或文件不存在时抛出
     */
    InputStream downloadStream(String bucket, String key);

    // ==================== 删除 ====================

    /**
     * 删除文件。
     *
     * <p><b>幂等语义：</b>若文件不存在，视为删除成功（不抛异常）。
     * 这与 AWS S3、MinIO 的 DELETE 语义一致。
     *
     * @param bucket 存储桶
     * @param key    对象键
     * @throws IllegalArgumentException 参数非法时抛出
     * @throws StorageException         删除失败（如无权限）时抛出
     */
    void delete(String bucket, String key);

    // ==================== 存在性 ====================

    /**
     * 判断文件是否存在。
     *
     * <p><b>返回值：</b>{@code true} 表示存在，{@code false} 表示不存在。
     * 存储层无法访问（如网络故障）时抛 {@link StorageException}。
     *
     * @param bucket 存储桶
     * @param key    对象键
     * @return {@code true} 表示存在
     * @throws IllegalArgumentException 参数非法时抛出
     * @throws StorageException         存储层访问失败时抛出
     */
    boolean exists(String bucket, String key);

    // ==================== 元数据 ====================

    /**
     * 获取文件元数据。
     *
     * <p>返回的 Map 应包含至少以下标准键（若存储支持）：
     * <ul>
     *     <li>{@code "Content-Type"}：MIME 类型</li>
     *     <li>{@code "Content-Length"}：字节大小</li>
     *     <li>{@code "Last-Modified"}：最后修改时间（ISO 8601 或 RFC 1123）</li>
     *     <li>{@code "ETag"}：对象哈希</li>
     * </ul>
     *
     * @param bucket 存储桶
     * @param key    对象键
     * @return 元数据 Map（永不为 {@code null}，可能为空）
     * @throws IllegalArgumentException 参数非法时抛出
     * @throws StorageException         文件不存在或读取失败时抛出
     */
    Map<String, String> getMetadata(String bucket, String key);

    /**
     * 获取文件大小（字节）。
     *
     * <p><b>默认实现：</b>从 {@link #getMetadata} 中读取 {@code "Content-Length"}。
     * 实现类可覆盖以提供更高效的查询。
     *
     * @param bucket 存储桶
     * @param key    对象键
     * @return 文件大小；文件不存在时返回 {@link Optional#empty()}
     * @throws IllegalArgumentException 参数非法时抛出
     * @throws StorageException         读取失败时抛出
     */
    default Optional<Long> getSize(String bucket, String key) {
        Map<String, String> metadata = getMetadata(bucket, key);
        String length = metadata.get("Content-Length");
        if (length == null || length.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(length));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    // ==================== 临时 URL ====================

    /**
     * 生成临时访问 URL。
     *
     * <p>适用于将文件临时授权给第三方访问，避免长期暴露。
     * 注意：并非所有存储都支持此功能（如本地文件系统可能不支持），
     * 此时实现类应抛 {@link UnsupportedOperationException}。
     *
     * @param bucket            存储桶
     * @param key               对象键
     * @param expirationSeconds 有效期（秒），必须为正整数
     * @return 临时访问 URL
     * @throws IllegalArgumentException      参数非法时抛出
     * @throws UnsupportedOperationException 存储不支持临时 URL 时抛出
     * @throws StorageException              生成失败时抛出
     */
    URI generatePresignedUrl(String bucket, String key, long expirationSeconds);

    // ==================== 类型 ====================

    /**
     * 获取存储类型。
     *
     * @return 存储类型枚举
     */
    StorageType getStorageType();

    // ==================== 默认方法：便捷重载 ====================

    /**
     * 上传文件（无元数据）。
     *
     * @see #upload(String, String, byte[], Map)
     */
    default URI upload(String bucket, String key, byte[] data) {
        return upload(bucket, key, data, null);
    }

    /**
     * 上传文件（流式，无元数据）。
     *
     * @see #upload(String, String, InputStream, Map)
     */
    default URI upload(String bucket, String key, InputStream data) {
        return upload(bucket, key, data, null);
    }

    /**
     * 生成临时访问 URL（默认有效期 1 小时）。
     *
     * @see #generatePresignedUrl(String, String, long)
     */
    default URI generatePresignedUrl(String bucket, String key) {
        return generatePresignedUrl(bucket, key, 3600L);
    }

    /**
     * 批量删除文件。
     *
     * <p><b>默认实现：</b>逐个调用 {@link #delete}，任一失败立即抛出。
     * 实现类若有批量 API（如 S3 的 DeleteObjects），应覆盖以提升性能。
     *
     * @param bucket 存储桶
     * @param keys   对象键列表
     * @throws IllegalArgumentException 参数非法时抛出
     * @throws StorageException         删除失败时抛出
     */
    default void deleteBatch(String bucket, List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        for (String key : keys) {
            delete(bucket, key);
        }
    }
}

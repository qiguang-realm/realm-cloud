package cn.realm.cloud.framework.common.util.file;

import org.springframework.http.MediaType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 文件元数据 / 标签管理工具类。
 *
 * <p><b>能力：</b>
 * <ul>
 *     <li>{@link #getMetadata(File)}：获取类型安全的 {@link FileMetadata} record</li>
 *     <li>{@link #getMetadataAsMap(File)}：获取 {@link Map} 版本（向后兼容，慎用）</li>
 *     <li>{@link #getMimeType(File)}：获取 MIME 类型（可能为 {@code null}）</li>
 *     <li>{@link #getMimeTypeOrOctetStream(File)}：获取 MIME 类型（带默认值）</li>
 * </ul>
 *
 * <p><b>时间字段：</b>返回 {@link java.time.Instant}（UTC），而非本地时间字符串。
 * 序列化时由 Jackson 统一处理时区。如需人类可读格式，调用 {@link java.time.Instant#toString()}。
 *
 * <p><b>线程安全：</b>本类所有方法均为无状态设计，线程安全。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 推荐：类型安全
 * FileMetadata meta = FileMetadataUtil.getMetadata(new File("/data/report.pdf"));
 * String name = meta.fileName();
 * long size = meta.fileSize();
 * Instant created = meta.creationTime();
 *
 * // 兼容：Map 版本
 * Map<String, Object> map = FileMetadataUtil.getMetadataAsMap(file);
 *
 * // MIME 类型
 * String mime = FileMetadataUtil.getMimeTypeOrOctetStream(file);
 * }</pre>
 *
 * @author QI Guang
 * @since 1.0.0
 */
public final class FileMetadataUtil {

    private FileMetadataUtil() {
        throw new UnsupportedOperationException(
                "FileMetadataUtil is a static utility class and cannot be instantiated");
    }

    // ==================== 元数据获取 ====================

    /**
     * 获取文件元数据（类型安全版本）。
     *
     * @param file 目标文件，不允许为 {@code null}
     * @return 不可变的 {@link FileMetadata}
     * @throws NullPointerException  当 {@code file} 为 {@code null} 时抛出
     * @throws IllegalArgumentException 当文件不存在时抛出
     * @throws IOException           读取文件属性失败时抛出
     */
    public static FileMetadata getMetadata(File file) throws IOException {
        Objects.requireNonNull(file, "file must not be null");
        if (!file.exists()) {
            throw new IllegalArgumentException("File does not exist: " + file.getAbsolutePath());
        }

        Path path = file.toPath();
        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);

        return new FileMetadata(
                file.getName(),
                file.getAbsolutePath(),
                file.length(),
                file.isDirectory(),
                file.isFile(),
                FileConvertUtil.getExtension(file),
                attrs.creationTime().toInstant(),
                attrs.lastModifiedTime().toInstant(),
                attrs.lastAccessTime().toInstant()
        );
    }

    /**
     * 获取文件元数据（{@link Map} 版本，向后兼容）。
     *
     * <p><b>推荐使用</b> {@link #getMetadata(File)} 的类型安全版本；
     * 本方法仅为兼容旧代码或需要动态字段的场景保留。
     *
     * <p>返回的 Map 使用 {@link LinkedHashMap} 保证字段顺序稳定，
     * 便于序列化为 JSON 时字段顺序可预测。
     *
     * @param file 目标文件，不允许为 {@code null}
     * @return 字段有序的 Map
     * @throws IOException 读取文件属性失败时抛出
     */
    public static Map<String, Object> getMetadataAsMap(File file) throws IOException {
        FileMetadata meta = getMetadata(file);
        Map<String, Object> map = new LinkedHashMap<>(16);
        map.put("fileName", meta.fileName());
        map.put("filePath", meta.filePath());
        map.put("fileSize", meta.fileSize());
        map.put("isDirectory", meta.directory());
        map.put("isFile", meta.regularFile());
        map.put("extension", meta.extension());
        map.put("creationTime", meta.creationTime());
        map.put("lastModifiedTime", meta.lastModifiedTime());
        map.put("lastAccessTime", meta.lastAccessTime());
        return map;
    }

    // ==================== MIME 类型 ====================

    /**
     * 获取文件的 MIME 类型。
     *
     * <p><b>注意：</b>当无法识别文件类型时返回 {@code null}。
     * 如需默认值，请使用 {@link #getMimeTypeOrOctetStream(File)}。
     *
     * @param file 目标文件，不允许为 {@code null}
     * @return MIME 类型，无法识别时返回 {@code null}
     * @throws IOException 读取失败时抛出
     */
    public static String getMimeType(File file) throws IOException {
        Objects.requireNonNull(file, "file must not be null");
        return Files.probeContentType(file.toPath());
    }

    /**
     * 获取文件的 MIME 类型，无法识别时返回 {@code application/octet-stream}。
     *
     * <p>这是大多数场景下的推荐方法，避免调用方处理 {@code null}。
     *
     * @param file 目标文件，不允许为 {@code null}
     * @return MIME 类型，永不返回 {@code null}
     * @throws IOException 读取失败时抛出
     */
    public static String getMimeTypeOrOctetStream(File file) throws IOException {
        String mime = getMimeType(file);
        return mime != null ? mime : MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    /**
     * 获取文件的 MIME 类型（{@link Optional} 版本）。
     */
    public static Optional<String> getMimeTypeOptional(File file) throws IOException {
        return Optional.ofNullable(getMimeType(file));
    }
}

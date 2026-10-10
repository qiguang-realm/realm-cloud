package cn.realm.cloud.framework.common.util.file;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * 文件元数据快照（不可变）。
 *
 * <p><b>字段说明：</b>
 * <ul>
 *     <li>{@link #fileName}：文件名（不含路径），如 {@code "report.pdf"}</li>
 *     <li>{@link #filePath}：绝对路径，如 {@code "/data/report.pdf"}</li>
 *     <li>{@link #fileSize}：文件大小（字节）</li>
 *     <li>{@link #directory}：是否为目录</li>
 *     <li>{@link #regularFile}：是否为普通文件</li>
 *     <li>{@link #extension}：扩展名（小写，不含 {@code .}）</li>
 *     <li>{@link #creationTime}：创建时间（{@link Instant}，UTC）</li>
 *     <li>{@link #lastModifiedTime}：最后修改时间（{@link Instant}，UTC）</li>
 *     <li>{@link #lastAccessTime}：最后访问时间（{@link Instant}，UTC）</li>
 * </ul>
 *
 * <p><b>时间字段：</b>统一使用 {@link Instant}（UTC 时间戳），
 * 序列化时由 Jackson 按 {@code spring.jackson.time-zone} 配置处理。
 * 如需字符串表示，调用 {@link Instant#toString()} 得到 ISO 8601 格式。
 *
 * @param fileName         文件名（不含路径）
 * @param filePath         绝对路径
 * @param fileSize         文件大小（字节）
 * @param directory        是否为目录
 * @param regularFile      是否为普通文件
 * @param extension        扩展名（小写，不含 {@code .}）
 * @param creationTime     创建时间（UTC）
 * @param lastModifiedTime 最后修改时间（UTC）
 * @param lastAccessTime   最后访问时间（UTC）
 * @author QI Guang
 * @since 1.0.0
 */
public record FileMetadata(
        String fileName,
        String filePath,
        long fileSize,
        boolean directory,
        boolean regularFile,
        String extension,
        Instant creationTime,
        Instant lastModifiedTime,
        Instant lastAccessTime
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}

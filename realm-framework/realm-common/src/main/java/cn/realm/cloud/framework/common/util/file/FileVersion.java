package cn.realm.cloud.framework.common.util.file;

import java.io.Serial;
import java.io.Serializable;
import java.nio.file.Path;
import java.time.Instant;

/**
 * 文件版本信息（不可变）。
 *
 * @param version      版本号（从 1 开始递增）
 * @param path         版本文件路径
 * @param size         文件大小（字节）
 * @param lastModified 最后修改时间（UTC，{@link Instant}）
 * @author QI Guang
 * @since 1.0.0
 */
public record FileVersion(
        int version,
        Path path,
        long size,
        Instant lastModified
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}

package cn.realm.cloud.framework.common.util.file;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 文件预览结果（不可变）。
 *
 * <p><b>字段说明：</b>
 * <ul>
 *     <li>{@link #fileName}：文件名</li>
 *     <li>{@link #fileSize}：文件大小（字节）</li>
 *     <li>{@link #fileType}：扩展名（小写，不含 {@code .}）</li>
 *     <li>{@link #preview}：预览文本（已截断，可能为 {@code null}）</li>
 *     <li>{@link #supported}：是否支持预览此类型</li>
 *     <li>{@link #attributes}：类型特定属性（如页数、图片宽高等），永不为 {@code null}</li>
 * </ul>
 *
 * @param fileName   文件名
 * @param fileSize   文件大小（字节）
 * @param fileType   扩展名
 * @param preview    预览文本
 * @param supported  是否支持预览
 * @param attributes 类型特定属性
 * @author QI Guang
 * @since 1.0.0
 */
public record FilePreview(
        String fileName,
        long fileSize,
        String fileType,
        String preview,
        boolean supported,
        Map<String, Object> attributes
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}

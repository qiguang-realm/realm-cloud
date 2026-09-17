package cn.realm.cloud.framework.common.util.file;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 文件元数据/标签管理工具类
 *
 * @author QI Guang
 * @version 1.0.0
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public final class FileMetadataUtil {

    private FileMetadataUtil() {
    }

    /**
     * 获取文件基础元数据
     */
    public static Map<String, Object> getMetadata(File file) throws java.io.IOException {
        if (file == null || !file.exists()) {
            throw new IllegalArgumentException("文件不存在");
        }

        Map<String, Object> metadata = new HashMap<>();
        BasicFileAttributes attrs = Files.readAttributes(file.toPath(), BasicFileAttributes.class);

        metadata.put("fileName", file.getName());
        metadata.put("filePath", file.getAbsolutePath());
        metadata.put("fileSize", file.length());
        metadata.put("isDirectory", file.isDirectory());
        metadata.put("isFile", file.isFile());
        metadata.put("extension", FileConvertUtil.getExtension(file));

        metadata.put("creationTime", formatTime(attrs.creationTime().toMillis()));
        metadata.put("lastModifiedTime", formatTime(attrs.lastModifiedTime().toMillis()));
        metadata.put("lastAccessTime", formatTime(attrs.lastAccessTime().toMillis()));

        return metadata;
    }

    /**
     * 获取文件MIME类型
     */
    public static String getMimeType(File file) throws java.io.IOException {
        return Files.probeContentType(file.toPath());
    }

    private static String formatTime(long millis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}

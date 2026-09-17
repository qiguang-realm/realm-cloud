package cn.realm.cloud.framework.common.enums;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 文件类型枚举，用于分类不同扩展名的文件。
 *
 * @author Qi
 */
public enum FileType {

    // 文档类型
    DOCUMENT("txt", "doc", "docx", "pdf", "ppt", "pptx", "pps", "xlsx", "xls", "rtf", "md", "html", "htm", "epub", "mobi"),

    // 音频类型
    AUDIO("mp3", "wav", "wma", "mpa", "ram", "ra", "aac", "aif", "m4a", "flac", "ogg", "wv", "aiff", "mid", "midi"),

    // 视频类型
    VIDEO("avi", "mpg", "mpe", "mpeg", "asf", "wmv", "mov", "qt", "rm", "mp4", "flv", "m4v", "webm", "ogv", "ogg", "mkv", "3gp", "3g2", "mts", "m2ts", "ts", "vob"),

    // 图片类型
    IMAGE("bmp", "dib", "pcp", "dif", "wmf", "gif", "jpg", "jpeg", "tif", "tiff", "eps", "psd", "cdr", "iff", "tga", "pcd", "mpt", "png", "webp", "ico", "svg", "heic", "heif", "raw", "arw", "cr2", "nef", "orf", "rw2"),

    // 压缩文件类型
    ARCHIVE("zip", "rar", "7z", "tar", "gz", "bz2", "iso", "dmg", "pkg", "deb", "rpm"),

    // 可执行文件类型
    EXECUTABLE("exe", "msi", "bat", "sh", "bin", "apk", "app", "dmg", "pkg", "deb", "rpm"),

    // 编程代码类型
    CODE("java", "class", "py", "c", "cpp", "h", "hpp", "js", "css", "html", "htm", "xml", "json", "sql", "php", "rb", "go", "rs", "ts");

    /**
     * 扩展名到文件类型的快速查找映射（不可变）
     */
    private static final Map<String, FileType> EXTENSION_MAP;

    static {
        // 构建扩展名 -> 文件类型的映射，扩展名统一为小写
        Map<String, FileType> map = new HashMap<>();
        for (FileType type : values()) {
            for (String ext : type.extensions) {
                map.put(ext, type);
            }
        }
        EXTENSION_MAP = Collections.unmodifiableMap(map);
    }

    /**
     * 该类型包含的扩展名集合（小写，不可变）
     */
    private final Set<String> extensions;

    FileType(String... extensions) {
        // 确保所有扩展名均为小写，避免后续大小写不一致
        Set<String> lowerSet = Arrays.stream(extensions)
                .map(ext -> ext.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        this.extensions = Collections.unmodifiableSet(lowerSet);
    }

    /**
     * 获取该文件类型包含的所有扩展名（不可变集合）
     */
    public Set<String> getExtensions() {
        return extensions;
    }

    /**
     * 判断给定的扩展名是否属于当前文件类型
     *
     * @param extension 扩展名（不含点号），大小写不敏感
     * @return 若扩展名匹配则返回 true，否则 false
     */
    public boolean contains(String extension) {
        if (extension == null) {
            return false;
        }
        return extensions.contains(extension.toLowerCase(Locale.ROOT));
    }

    /**
     * 根据扩展名获取对应的文件类型
     *
     * @param extension 扩展名（不含点号），大小写不敏感
     * @return 对应的文件类型，若扩展名为空或未知则返回 null
     */
    public static FileType fromExtension(String extension) {
        if (extension == null || extension.isEmpty()) {
            return null;
        }
        return EXTENSION_MAP.get(extension.toLowerCase(Locale.ROOT));
    }
}

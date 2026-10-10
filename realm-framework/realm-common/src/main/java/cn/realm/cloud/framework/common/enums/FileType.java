package cn.realm.cloud.framework.common.enums;

import java.util.*;

/**
 * 文件类型枚举，按扩展名将文件归类为 7 大类。
 *
 * <p><b>类型分类：</b>
 * <ul>
 *     <li>{@link #DOCUMENT}：文档（txt / docx / pdf / xlsx / md / html 等）</li>
 *     <li>{@link #AUDIO}：音频（mp3 / wav / flac / ogg 等）</li>
 *     <li>{@link #VIDEO}：视频（mp4 / avi / mkv / mov 等）</li>
 *     <li>{@link #IMAGE}：图片（jpg / png / gif / svg / webp 等）</li>
 *     <li>{@link #ARCHIVE}：压缩包 / 磁盘映像（zip / rar / 7z / dmg 等）</li>
 *     <li>{@link #EXECUTABLE}：可执行文件 / 安装包（exe / apk / deb / rpm 等）</li>
 *     <li>{@link #CODE}：编程源码（java / py / js / ts / sql 等）</li>
 * </ul>
 *
 * <p><b>扩展名唯一性：</b>每个扩展名<b>只归属于一个类型</b>。
 * 这避免了"同一扩展名归属不确定"的隐式行为（如 {@code ogg} 只归 AUDIO，
 * {@code html} 只归 DOCUMENT）。若需要"某文件是否也是源码"这类多归属查询，
 * 应使用独立方法而非在不同类型间重复声明扩展名。
 *
 * <p><b>大小写与格式约定：</b>
 * <ul>
 *     <li>扩展名<b>不含点号</b>，如 {@code "pdf"} 而非 {@code ".pdf"}</li>
 *     <li>查找时<b>大小写不敏感</b>（{@code "PDF"}、{@code "pdf"}、{@code "Pdf"} 均可）</li>
 *     <li>内部统一以<b>小写</b>存储（{@link Locale#ROOT}，避免土耳其语 I 问题）</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 按扩展名查类型
 * FileType type = FileType.fromExtension("pdf");        // → DOCUMENT
 *
 * // 按文件名查类型（推荐，自动处理点号）
 * FileType type2 = FileType.fromFileName("report.pdf"); // → DOCUMENT
 *
 * // 判断是否属于某类型
 * boolean isImage = FileType.IMAGE.contains("png");     // → true
 * }</pre>
 *
 * @author QI Guang
 * @since 1.0.0
 */
public enum FileType {

    // ==================== 文档类 ====================

    /**
     * 文档类型：文本、办公文档、电子书、网页等。
     *
     * <p><b>注意：</b>{@code html} / {@code htm} 归属此类而非 {@link #CODE}，
     * 因本枚举按"用户视角的文件分类"设计。若需判断"是否为源码"，
     * 请使用独立的业务方法。
     */
    DOCUMENT("txt", "doc", "docx", "pdf", "ppt", "pptx", "pps",
            "xlsx", "xls", "rtf", "md", "html", "htm", "epub", "mobi"),

    // ==================== 媒体类 ====================

    /**
     * 音频类型：音乐、录音、音效等。
     *
     * <p><b>注意：</b>{@code ogg} 归属此类（Ogg 容器主流用途是 Vorbis 音频）。
     */
    AUDIO("mp3", "wav", "wma", "mpa", "ram", "ra", "aac",
            "aif", "m4a", "flac", "ogg", "wv", "aiff", "mid", "midi"),

    /**
     * 视频类型：电影、短片、动画等。
     */
    VIDEO("avi", "mpg", "mpe", "mpeg", "asf", "wmv", "mov", "qt",
            "rm", "mp4", "flv", "m4v", "webm", "ogv", "mkv",
            "3gp", "3g2", "mts", "m2ts", "ts", "vob"),

    /**
     * 图片类型：位图、矢量图、相机原片等。
     */
    IMAGE("bmp", "dib", "pcp", "dif", "wmf", "gif", "jpg", "jpeg",
            "tif", "tiff", "eps", "psd", "cdr", "iff", "tga", "pcd",
            "mpt", "png", "webp", "ico", "svg", "heic", "heif",
            "raw", "arw", "cr2", "nef", "orf", "rw2"),

    // ==================== 归档类 ====================

    /**
     * 压缩包 / 磁盘映像类型。
     *
     * <p><b>注意：</b>{@code dmg} 归属此类（macOS 磁盘映像主要用于分发）。
     */
    ARCHIVE("zip", "rar", "7z", "tar", "gz", "bz2", "iso", "dmg"),

    // ==================== 可执行类 ====================

    /**
     * 可执行文件 / 安装包类型。
     *
     * <p><b>注意：</b>{@code pkg} / {@code deb} / {@code rpm} 归属此类
     * （虽为包格式，但主要用途是"安装"而非"解压使用"）。
     */
    EXECUTABLE("exe", "msi", "bat", "sh", "bin", "apk", "app",
            "pkg", "deb", "rpm"),

    // ==================== 源码类 ====================

    /**
     * 编程源码类型。
     *
     * <p><b>注意：</b>不含 {@code html} / {@code htm}（已归 {@link #DOCUMENT}）。
     */
    CODE("java", "class", "py", "c", "cpp", "h", "hpp", "js",
            "css", "xml", "json", "sql", "php", "rb", "go", "rs", "ts"),
    ;

    // ==================== 静态索引 ====================

    /**
     * 扩展名 → 文件类型的快速查找映射（不可变）。
     *
     * <p>构建时检测重复扩展名，若发现冲突立即抛 {@link IllegalStateException}，
     * 防止隐式覆盖导致行为不确定。
     */
    private static final Map<String, FileType> EXTENSION_MAP;

    static {
        Map<String, FileType> map = new HashMap<>();
        for (FileType type : values()) {
            for (String ext : type.extensions) {
                FileType existing = map.putIfAbsent(ext, type);
                if (existing != null) {
                    // 防御性编程：枚举定义错误应及早暴露（fail-fast）
                    throw new IllegalStateException(
                            "扩展名重复：'" + ext + "' 同时归属于 "
                                    + existing.name() + " 和 " + type.name());
                }
            }
        }
        EXTENSION_MAP = Collections.unmodifiableMap(map);
    }

    // ==================== 实例字段 ====================

    /**
     * 该类型包含的扩展名集合（小写、去重、保序、不可变）。
     */
    private final Set<String> extensions;

    // ==================== 构造器 ====================

    /**
     * 构造枚举常量。
     *
     * @param extensions 扩展名数组（不含点号，大小写不敏感）
     */
    FileType(String... extensions) {
        // 使用 LinkedHashSet 保序（便于日志阅读），并将所有扩展名统一为小写
        Set<String> lowerSet = new LinkedHashSet<>(extensions.length);
        for (String ext : extensions) {
            // null 元素不可接受，抛 NPE 让定义错误早暴露
            lowerSet.add(Objects.requireNonNull(ext, "扩展名不能为 null")
                    .toLowerCase(Locale.ROOT));
        }
        this.extensions = Collections.unmodifiableSet(lowerSet);
    }

    // ==================== 实例方法 ====================

    /**
     * 获取该文件类型包含的所有扩展名（不可变集合）。
     *
     * <p>返回的集合为<b>不可变视图</b>，任何修改操作将抛
     * {@link UnsupportedOperationException}。
     *
     * @return 扩展名集合（小写、保序），永不为 {@code null}
     */
    public Set<String> getExtensions() {
        return extensions;
    }

    /**
     * 判断给定扩展名是否属于当前文件类型。
     *
     * @param extension 扩展名（不含点号），允许为 {@code null}（返回 {@code false}）
     * @return {@code true} 表示属于当前类型
     */
    public boolean contains(String extension) {
        if (extension == null) {
            return false;
        }
        return extensions.contains(extension.toLowerCase(Locale.ROOT));
    }

    // ==================== 静态工厂 ====================

    /**
     * 根据扩展名获取文件类型。
     *
     * @param extension 扩展名（不含点号），大小写不敏感
     * @return 对应的文件类型；扩展名为 {@code null}、空串或未知时返回 {@code null}
     */
    public static FileType fromExtension(String extension) {
        if (extension == null || extension.isEmpty()) {
            return null;
        }
        return EXTENSION_MAP.get(extension.toLowerCase(Locale.ROOT));
    }

    /**
     * 根据文件名获取文件类型（自动提取扩展名）。
     *
     * <p>示例：
     * <ul>
     *     <li>{@code "report.pdf"} → {@link #DOCUMENT}</li>
     *     <li>{@code "photo.JPG"} → {@link #IMAGE}（大小写不敏感）</li>
     *     <li>{@code "archive.tar.gz"} → {@link #ARCHIVE}（取最后一段 {@code .gz}）</li>
     *     <li>{@code "no-extension"} → {@code null}</li>
     * </ul>
     *
     * @param fileName 文件名（可含路径），允许为 {@code null}
     * @return 对应的文件类型；无法识别时返回 {@code null}
     */
    public static FileType fromFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex >= fileName.length() - 1) {
            return null;
        }
        return fromExtension(fileName.substring(dotIndex + 1));
    }

    // ==================== 兼容性说明 ====================

    /**
     * 获取所有扩展名到文件类型的映射（仅供调试使用）。
     *
     * <p><b>注意：</b>返回的 Map 不可变，但<b>不推荐业务代码使用</b>——
     * 建议通过 {@link #fromExtension(String)} 或 {@link #fromFileName(String)} 查询。
     *
     * @return 扩展名到文件类型的不可变映射
     */
    public static Map<String, FileType> getExtensionMap() {
        return EXTENSION_MAP;
    }
}

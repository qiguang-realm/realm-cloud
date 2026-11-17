package cn.realm.cloud.framework.web.file;

import cn.realm.cloud.framework.utils.file.FileConvertUtil;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.Objects;

/**
 * {@link MultipartFile} 转换支撑类。
 *
 * <p><b>职责：</b>为 Spring MVC 的 {@link MultipartFile} 提供与
 * {@code byte[]} / {@link File} 之间的转换，以及基于 Spring
 * {@link MediaTypeFactory} 的 MIME 类型推断与文件分类判断。
 *
 * <p><b>为什么以 {@code Support} 结尾而非 {@code Util}：</b>
 * 本类不是通用的 {@code StringUtils} 式工具，而是<b>为特定技术（Spring MVC 的
 * {@link MultipartFile}）提供适配支撑</b>的类。{@code Support} 是 Spring 框架自身
 * 常用的命名惯例（如 {@code WebMvcConfigurationSupport}），用于表达"为特定上下文
 * 提供辅助功能"的语义；{@code Util} 则暗示与上下文无关的通用工具，会掩盖本类
 * 的适配器本质。
 *
 * <p><b>与核心类的分工：</b>
 * <ul>
 *     <li>纯 IO 转换（{@code byte[]} / {@link File} / {@link InputStream} 互转、
 *         复制、删除、扩展名提取）位于 {@code realm-utils} 的 {@link FileConvertUtil}</li>
 *     <li>本类只处理 {@link MultipartFile} 相关的桥接与 Spring 特有的 MIME 推断</li>
 * </ul>
 * 依赖方向：{@code realm-web → realm-utils}（单向）。本类在需要时委托
 * {@link FileConvertUtil} 完成纯 IO 操作，不重复实现。
 *
 * <p><b>与 {@code FileMetadataUtil} 的 MIME 推断差异：</b>
 * <ul>
 *     <li>本类使用 Spring 的 {@link MediaTypeFactory}：基于<b>文件名扩展名</b>的
 *         内置映射表（500+ 条目），跨平台行为一致</li>
 *     <li>{@code FileMetadataUtil#getMimeType(File)} 使用
 *         {@link java.nio.file.Files#probeContentType(java.nio.file.Path)}：
 *         依赖<b>操作系统的 MIME 数据库</b>，精简容器镜像中可能返回 {@code null}</li>
 * </ul>
 * 两者不是重复实现，而是服务于不同场景：Web 层倾向使用 Spring 的稳定映射，
 * 非 Web 的工具层倾向使用 JDK 标准能力。
 *
 * <p><b>线程安全：</b>本类所有方法均为无状态设计，线程安全。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // MultipartFile → byte[]
 * byte[] bytes = MultipartFileConvertSupport.toBytes(multipartFile);
 *
 * // byte[] → MultipartFile（无需落盘）
 * MultipartFile mf = MultipartFileConvertSupport.toMultipartFile(
 *         bytes, "file", "a.txt", "text/plain");
 *
 * // File → MultipartFile（自动推断 Content-Type）
 * MultipartFile fromFile = MultipartFileConvertSupport.toMultipartFile(new File("a.png"));
 *
 * // MIME 判断
 * boolean image = MultipartFileConvertSupport.isImage(new File("a.png"));
 * }</pre>
 *
 * @author QI Guang
 * @see FileConvertUtil
 */
public final class MultipartFileConvertSupport {

    private MultipartFileConvertSupport() {
        throw new UnsupportedOperationException(
                "MultipartFileConvertSupport is a static support class and cannot be instantiated");
    }

    // ================================================================
    // MultipartFile → byte[]
    // ================================================================

    /**
     * 从 {@link MultipartFile} 读取字节数组。
     *
     * @param multipartFile 上传文件，不允许为 {@code null}
     * @throws IOException 读取失败时抛出
     */
    public static byte[] toBytes(MultipartFile multipartFile) throws IOException {
        Objects.requireNonNull(multipartFile, "multipartFile must not be null");
        if (multipartFile.isEmpty()) {
            return new byte[0];
        }
        return multipartFile.getBytes();
    }

    // ================================================================
    // 转换为 MultipartFile
    // ================================================================

    /**
     * 将字节数组包装为 {@link MultipartFile}。
     *
     * <p><b>默认值：</b>{@code name="file"}、{@code originalFilename="file.bin"}、
     * {@code contentType="application/octet-stream"}。
     */
    public static MultipartFile toMultipartFile(byte[] bytes, String name,
                                                String originalFilename, String contentType) {
        return new ByteArrayMultipartFile(
                bytes != null ? bytes : new byte[0],
                name != null ? name : "file",
                originalFilename != null ? originalFilename : "file.bin",
                contentType != null ? contentType : MediaType.APPLICATION_OCTET_STREAM_VALUE);
    }

    /**
     * 将文件包装为 {@link MultipartFile}。
     *
     * <p>Content-Type 根据文件名推断；无法识别时使用 {@code application/octet-stream}。
     * 文件内容通过 {@link FileConvertUtil#toBytes(File)} 读取。
     */
    public static MultipartFile toMultipartFile(File file, String name) throws IOException {
        Objects.requireNonNull(file, "file must not be null");
        if (!file.exists()) {
            throw new FileNotFoundException("File not found: " + file.getAbsolutePath());
        }
        if (!file.isFile()) {
            throw new IllegalArgumentException("Not a regular file: " + file.getAbsolutePath());
        }
        return toMultipartFile(
                FileConvertUtil.toBytes(file),
                name,
                file.getName(),
                getContentType(file.getName()));
    }

    /**
     * 将文件包装为 {@link MultipartFile}（{@code name} 默认 {@code "file"}）。
     */
    public static MultipartFile toMultipartFile(File file) throws IOException {
        return toMultipartFile(file, null);
    }

    /**
     * 复制 {@link MultipartFile}，可指定新的 {@code name}。
     */
    public static MultipartFile copyMultipartFile(MultipartFile multipartFile, String newName)
            throws IOException {
        Objects.requireNonNull(multipartFile, "multipartFile must not be null");
        return toMultipartFile(
                toBytes(multipartFile),
                newName != null ? newName : multipartFile.getName(),
                multipartFile.getOriginalFilename(),
                multipartFile.getContentType());
    }

    // ================================================================
    // MIME 类型与分类判断
    // ================================================================

    /**
     * 根据文件名获取 MIME 类型。
     *
     * <p>使用 Spring 的 {@link MediaTypeFactory}，基于文件名扩展名的内置映射表
     * （500+ 条目），跨平台行为一致。无法识别时返回 {@code application/octet-stream}。
     *
     * <p><b>检测依据：</b>基于文件名/扩展名，不是基于文件内容（魔数）。
     * 对安全敏感场景（如上传校验），应结合魔数检测或 Apache Tika。
     */
    public static String getContentType(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        return MediaTypeFactory.getMediaType(fileName)
                .map(MediaType::toString)
                .orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE);
    }

    /**
     * 判断文件是否为图片（基于 MIME 类型）。
     */
    public static boolean isImage(File file) {
        if (file == null || !file.exists()) {
            return false;
        }
        return getContentType(file.getName()).startsWith("image/");
    }

    /**
     * 判断文件是否为 PDF（基于 MIME 类型）。
     */
    public static boolean isPdf(File file) {
        if (file == null || !file.exists()) {
            return false;
        }
        return MediaType.APPLICATION_PDF_VALUE.equals(getContentType(file.getName()));
    }

    // ================================================================
    // 内部类：基于 byte[] 的 MultipartFile 实现
    // ================================================================

    /**
     * 基于 {@code byte[]} 的 {@link MultipartFile} 实现。
     *
     * <p>适用于内存中构建上传文件，无需落盘。
     */
    private static final class ByteArrayMultipartFile implements MultipartFile {

        private final byte[] content;
        private final String name;
        private final String originalFilename;
        private final String contentType;

        ByteArrayMultipartFile(byte[] content, String name,
                               String originalFilename, String contentType) {
            this.content = content;
            this.name = name;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() {
            return content;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(File dest) throws IOException {
            Objects.requireNonNull(dest, "dest must not be null");
            FileConvertUtil.toFile(content, dest);
        }
    }
}

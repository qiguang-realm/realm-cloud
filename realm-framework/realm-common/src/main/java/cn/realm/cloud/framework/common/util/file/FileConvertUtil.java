package cn.realm.cloud.framework.common.util.file;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;

/**
 * 文件格式转换工具类。
 *
 * <p><b>能力：</b>提供 {@code byte[]}、{@link File}、{@link InputStream}、
 * {@link OutputStream}、{@link MultipartFile} 之间的统一转换，
 * 以及常用的文件操作（复制、读写、删除、类型判断）。
 *
 * <p><b>核心设计原则：</b>
 * <ol>
 *     <li>以 {@code byte[]} 为数据核心（最底层、最通用）</li>
 *     <li>所有转换方法命名统一：{@code toXxx()} 表示转换为 {@code Xxx} 类型</li>
 *     <li><b>不关闭调用方传入的流</b>：遵循"谁打开，谁关闭"原则（Effective Java Item 9）</li>
 *     <li>输入参数校验使用 {@link Objects#requireNonNull}，异常消息统一</li>
 * </ol>
 *
 * <p><b>内容类型识别：</b>使用 Spring 的 {@link MediaTypeFactory}，
 * 内置 500+ 扩展名映射，覆盖常见文件格式。
 *
 * <p><b>线程安全：</b>本类所有方法均为无状态设计，线程安全。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 读取文件
 * byte[] bytes = FileConvertUtil.toBytes(new File("a.txt"));
 *
 * // 输入流 → byte[]（调用方负责关闭 inputStream）
 * try (InputStream is = new FileInputStream("a.txt")) {
 *     byte[] data = FileConvertUtil.toBytes(is);
 * }
 *
 * // 写文件
 * FileConvertUtil.toFile(bytes, new File("b.txt"));
 *
 * // 转 MultipartFile
 * MultipartFile mf = FileConvertUtil.toMultipartFile(bytes, "file", "a.txt", "text/plain");
 * }</pre>
 *
 * @author QI Guang
 * @since 1.0.0
 */
public final class FileConvertUtil {

    private FileConvertUtil() {
        throw new UnsupportedOperationException(
                "FileConvertUtil is a static utility class and cannot be instantiated");
    }

    // ================================================================
    // 转换为 byte[]
    // ================================================================

    /**
     * 读取文件为字节数组。
     *
     * @param file 目标文件，不允许为 {@code null}
     * @throws IOException 文件不存在或读取失败时抛出
     */
    public static byte[] toBytes(File file) throws IOException {
        Objects.requireNonNull(file, "file must not be null");
        if (!file.exists()) {
            throw new FileNotFoundException("File not found: " + file.getAbsolutePath());
        }
        return FileUtils.readFileToByteArray(file);
    }

    /**
     * 从输入流读取字节数组。
     *
     * <p><b>注意：</b>本方法<b>不关闭</b> {@code inputStream}，由调用方负责关闭。
     *
     * @param inputStream 输入流，不允许为 {@code null}
     * @throws IOException 读取失败时抛出
     */
    public static byte[] toBytes(InputStream inputStream) throws IOException {
        Objects.requireNonNull(inputStream, "inputStream must not be null");
        return inputStream.readAllBytes();
    }

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

    /**
     * 将字符串按 UTF-8 编码转为字节数组。
     *
     * @param content 字符串；为 {@code null} 时返回空数组
     */
    public static byte[] toBytes(String content) {
        if (content == null) {
            return new byte[0];
        }
        return content.getBytes(StandardCharsets.UTF_8);
    }

    // ================================================================
    // 转换为 File
    // ================================================================

    /**
     * 将字节数组写入文件（自动创建父目录）。
     */
    public static void toFile(byte[] bytes, File file) throws IOException {
        Objects.requireNonNull(bytes, "bytes must not be null");
        Objects.requireNonNull(file, "file must not be null");
        forceMkdirs(file);
        FileUtils.writeByteArrayToFile(file, bytes);
    }

    /**
     * 将输入流写入文件（自动创建父目录）。
     *
     * <p><b>注意：</b>本方法<b>不关闭</b> {@code inputStream}，由调用方负责关闭。
     */
    public static void toFile(InputStream inputStream, File file) throws IOException {
        Objects.requireNonNull(inputStream, "inputStream must not be null");
        Objects.requireNonNull(file, "file must not be null");
        forceMkdirs(file);
        try (OutputStream out = Files.newOutputStream(file.toPath())) {
            inputStream.transferTo(out);
        }
    }

    /**
     * 将字符串按 UTF-8 写入文件（自动创建父目录）。
     */
    public static void toFile(String content, File file) throws IOException {
        Objects.requireNonNull(file, "file must not be null");
        toFile(toBytes(content), file);
    }

    /**
     * 将字节数组写入临时文件。
     *
     * <p>临时文件位于系统临时目录，通过 {@link File#deleteOnExit()} 在 JVM 退出时清理。
     *
     * @param bytes  字节数组，不允许为空
     * @param prefix 文件名前缀（默认 {@code "file_"}）
     * @param suffix 文件名后缀（默认 {@code ".tmp"}）
     */
    public static File toTempFile(byte[] bytes, String prefix, String suffix) throws IOException {
        Objects.requireNonNull(bytes, "bytes must not be null");
        if (bytes.length == 0) {
            throw new IllegalArgumentException("bytes must not be empty");
        }
        File tempFile = createTempFile(prefix, suffix);
        toFile(bytes, tempFile);
        return tempFile;
    }

    /**
     * 将输入流写入临时文件。
     *
     * <p><b>注意：</b>本方法<b>不关闭</b> {@code inputStream}。
     */
    public static File toTempFile(InputStream inputStream, String prefix, String suffix)
            throws IOException {
        Objects.requireNonNull(inputStream, "inputStream must not be null");
        File tempFile = createTempFile(prefix, suffix);
        toFile(inputStream, tempFile);
        return tempFile;
    }

    // ================================================================
    // 转换为 InputStream
    // ================================================================

    /**
     * 将字节数组包装为 {@link ByteArrayInputStream}。
     *
     * @param bytes 字节数组；为 {@code null} 时返回空输入流
     */
    public static InputStream toInputStream(byte[] bytes) {
        return new ByteArrayInputStream(bytes != null ? bytes : new byte[0]);
    }

    /**
     * 打开文件输入流。
     *
     * <p><b>注意：</b>调用方负责关闭返回的流。
     */
    public static InputStream toInputStream(File file) throws IOException {
        Objects.requireNonNull(file, "file must not be null");
        if (!file.exists()) {
            throw new FileNotFoundException("File not found: " + file.getAbsolutePath());
        }
        return new FileInputStream(file);
    }

    /**
     * 将字符串按 UTF-8 编码包装为 {@link ByteArrayInputStream}。
     */
    public static InputStream toInputStream(String content) {
        return toInputStream(toBytes(content));
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
                toBytes(file),
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
    // 文件操作
    // ================================================================

    /**
     * 复制文件（自动创建目标父目录）。
     */
    public static void copy(File source, File target) throws IOException {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(target, "target must not be null");
        if (!source.exists()) {
            throw new FileNotFoundException("Source not found: " + source.getAbsolutePath());
        }
        forceMkdirs(target);
        Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * 从输入流复制到输出流。
     *
     * <p><b>注意：</b>本方法<b>不关闭</b>任何流，由调用方负责关闭。
     *
     * @return 复制的字节数
     */
    public static long copy(InputStream input, OutputStream output) throws IOException {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(output, "output must not be null");
        return IOUtils.copyLarge(input, output);
    }

    /**
     * 按行读取文件（UTF-8 编码）。
     */
    public static List<String> readLines(File file) throws IOException {
        Objects.requireNonNull(file, "file must not be null");
        if (!file.exists()) {
            throw new FileNotFoundException("File not found: " + file.getAbsolutePath());
        }
        return FileUtils.readLines(file, StandardCharsets.UTF_8);
    }

    /**
     * 将字符串按 UTF-8 写入文件（自动创建父目录）。
     */
    public static void writeString(File file, String content) throws IOException {
        Objects.requireNonNull(file, "file must not be null");
        forceMkdirs(file);
        FileUtils.writeStringToFile(file, content != null ? content : "",
                StandardCharsets.UTF_8);
    }

    /**
     * 删除文件或目录（静默，失败返回 {@code false}）。
     */
    public static boolean delete(File file) {
        if (file == null || !file.exists()) {
            return false;
        }
        return FileUtils.deleteQuietly(file);
    }

    // ================================================================
    // 扩展名与内容类型
    // ================================================================

    /**
     * 获取文件扩展名（小写，不含 {@code .}）。
     */
    public static String getExtension(File file) {
        return file == null ? "" : getExtension(file.getName());
    }

    /**
     * 获取文件扩展名（小写，不含 {@code .}）。
     */
    public static String getExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1).toLowerCase();
        }
        return "";
    }

    /**
     * 根据文件名获取 MIME 类型。
     *
     * <p>使用 Spring 的 {@link MediaTypeFactory}，内置 500+ 扩展名映射。
     * 无法识别时返回 {@code application/octet-stream}。
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

    /**
     * 判断文件是否为空（不存在或长度为 0）。
     */
    public static boolean isEmpty(File file) {
        return file == null || !file.exists() || file.length() == 0;
    }

    // ================================================================
    // 私有辅助方法
    // ================================================================

    /**
     * 创建临时文件并注册 JVM 退出时删除。
     */
    private static File createTempFile(String prefix, String suffix) throws IOException {
        String normalizedPrefix = (prefix != null && !prefix.isEmpty()) ? prefix : "file_";
        String normalizedSuffix = normalizeSuffix(suffix);
        File tempFile = File.createTempFile(normalizedPrefix, normalizedSuffix);
        tempFile.deleteOnExit();
        return tempFile;
    }

    /**
     * 规范化后缀（确保以 {@code .} 开头）。
     */
    private static String normalizeSuffix(String suffix) {
        if (suffix == null || suffix.isEmpty()) {
            return ".tmp";
        }
        return suffix.startsWith(".") ? suffix : "." + suffix;
    }

    /**
     * 确保目标文件的父目录存在。
     */
    private static void forceMkdirs(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            FileUtils.forceMkdir(parent);
        }
    }

    // ================================================================
    // 内部类：MultipartFile 实现
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
            toFile(content, dest);
        }
    }
}

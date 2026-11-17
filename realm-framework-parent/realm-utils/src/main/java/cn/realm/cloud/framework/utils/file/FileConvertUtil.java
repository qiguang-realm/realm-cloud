package cn.realm.cloud.framework.utils.file;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Objects;

/**
 * 文件格式转换工具类（核心，无 Web 依赖，无第三方 IO 依赖）。
 *
 * <p><b>能力：</b>提供 {@code byte[]}、{@link File}、{@link InputStream}、
 * {@link OutputStream} 之间的统一转换，以及常用的文件操作（复制、读写、删除、扩展名提取）。
 *
 * <p><b>依赖边界：</b>
 * <ul>
 *     <li>本类只依赖 JDK 标准库（{@code java.nio.file}、{@code java.io}），
 *         不依赖 Apache Commons IO，保持 {@code realm-utils} 零第三方 IO 依赖</li>
 *     <li>不依赖 Spring Web。{@code MultipartFile} 相关转换、MIME 类型推断、图片/PDF 判断
 *         位于 {@code WebFileConvertUtil}（realm-web 模块）</li>
 * </ul>
 *
 * <p><b>核心设计原则：</b>
 * <ol>
 *     <li>以 {@code byte[]} 为数据核心（最底层、最通用）</li>
 *     <li>所有转换方法命名统一：{@code toXxx()} 表示转换为 {@code Xxx} 类型</li>
 *     <li><b>不关闭调用方传入的流</b>：遵循"谁打开，谁关闭"原则（Effective Java Item 9）</li>
 *     <li>输入参数校验使用 {@link Objects#requireNonNull}，异常消息统一</li>
 * </ol>
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
 * }</pre>
 *
 * @author QI Guang
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
        return Files.readAllBytes(file.toPath());
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
     *
     * <p>使用 {@link Files#write(Path, byte[], java.nio.file.OpenOption...)}
     * 的默认选项（CREATE、TRUNCATE_EXISTING、WRITE），与原先
     * {@code FileUtils.writeByteArrayToFile} 行为一致。
     */
    public static void toFile(byte[] bytes, File file) throws IOException {
        Objects.requireNonNull(bytes, "bytes must not be null");
        Objects.requireNonNull(file, "file must not be null");
        forceMkdirs(file);
        Files.write(file.toPath(), bytes);
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
     * <p><b>清理策略：</b>临时文件通过 {@link File#deleteOnExit()} 在 JVM 退出时清理。
     * 该机制仅适用于短生命周期进程；长运行的 Spring Boot 服务中，
     * {@code deleteOnExit} 的注册列表会持续增长，调用方应在使用后显式
     * {@link File#delete()}，或改用 {@code Files.createTempFile} + try-with-resources 手动清理。
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
     * 临时文件清理策略见 {@link #toTempFile(byte[], String, String)}。
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
        return input.transferTo(output);
    }

    /**
     * 按行读取文件（UTF-8 编码）。
     */
    public static List<String> readLines(File file) throws IOException {
        Objects.requireNonNull(file, "file must not be null");
        if (!file.exists()) {
            throw new FileNotFoundException("File not found: " + file.getAbsolutePath());
        }
        return Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
    }

    /**
     * 将字符串按 UTF-8 写入文件（自动创建父目录）。
     */
    public static void writeString(File file, String content) throws IOException {
        Objects.requireNonNull(file, "file must not be null");
        forceMkdirs(file);
        Files.writeString(file.toPath(), content != null ? content : "",
                StandardCharsets.UTF_8);
    }

    /**
     * 删除文件或目录（静默，失败返回 {@code false}）。
     *
     * <p>目录会递归删除。任何一步失败（如权限不足、文件被占用）都会返回
     * {@code false}，不抛异常，与原先 {@code FileUtils.deleteQuietly} 语义一致。
     */
    public static boolean delete(File file) {
        if (file == null || !file.exists()) {
            return false;
        }
        try {
            deleteRecursively(file.toPath());
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // ================================================================
    // 扩展名
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
     *
     * <p>{@link Files#createDirectories} 是幂等的，父目录已存在时不报错。
     */
    private static void forceMkdirs(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) {
            Files.createDirectories(parent.toPath());
        }
    }

    /**
     * 递归删除文件或目录。
     *
     * <p>先删除子项，再删除目录自身；{@link Files#deleteIfExists} 对不存在的路径
     * 不抛异常，因此对并发删除是安全的。
     */
    private static void deleteRecursively(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                    throws IOException {
                if (exc != null) {
                    throw exc;
                }
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}

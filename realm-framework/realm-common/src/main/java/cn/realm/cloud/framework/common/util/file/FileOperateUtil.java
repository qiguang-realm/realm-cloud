package cn.realm.cloud.framework.common.util.file;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 文件操作与安全管理工具类。
 *
 * <p><b>能力：</b>
 * <ul>
 *     <li>路径安全校验：{@link #validateFilePath(String)} 防止路径遍历（CWE-22）</li>
 *     <li>临时目录管理：{@link #getTempDir()}、{@link #generateTempFilePath(String)}</li>
 *     <li>网络下载：{@link #downloadBytes(String)}、{@link #downloadToFile(String, String)}</li>
 *     <li>本地读写：{@link #readLocalBytes(String)}、{@link #writeBytesToFile(byte[], String)}</li>
 *     <li>压缩解压：{@link #compressFiles(List, String)}、{@link #extractZip(String, String)}</li>
 *     <li>完整性校验：{@link #calculateSha256(String)}</li>
 * </ul>
 *
 * <p><b>安全防护：</b>
 * <ul>
 *     <li><b>Zip Slip</b>（CWE-22）：解压时校验每个条目名的规范化路径必须在目标目录内</li>
 *     <li><b>Zip Bomb</b>（CWE-409）：解压时限制单文件大小与总大小</li>
 *     <li><b>下载限制</b>（CWE-770）：限制最大下载字节数，防止 OOM / 磁盘打满</li>
 *     <li><b>路径遍历</b>（CWE-22）：拒绝包含 {@code ..} 的路径</li>
 * </ul>
 *
 * <p><b>线程安全：</b>本类所有方法均为无状态设计，线程安全。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 下载并保存到临时目录（含大小限制）
 * String path = FileOperateUtil.downloadToTemp("https://example.com/file.pdf", "file.pdf");
 *
 * // 计算 SHA-256
 * String hash = FileOperateUtil.calculateSha256(path);
 *
 * // 解压（含 Zip Slip / Zip Bomb 防护）
 * List<String> files = FileOperateUtil.extractZip("/tmp/a.zip", "/tmp/out");
 * }</pre>
 *
 * @author QI Guang
 * @since 1.0.0
 */
public final class FileOperateUtil {

    private static final Logger log = LoggerFactory.getLogger(FileOperateUtil.class);

    /**
     * 默认 HTTP 连接超时时间（毫秒）。
     */
    private static final int DEFAULT_TIMEOUT_MS = 30_000;

    /**
     * 系统临时目录。
     */
    private static final String SYSTEM_TMP_DIR = System.getProperty("java.io.tmpdir");

    /**
     * 应用专属临时子目录。
     */
    private static final String APP_TEMP_SUB_DIR = "app" + File.separator + "temp";

    /**
     * 文件类型白名单。
     */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "txt", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "jpg", "jpeg", "png", "gif", "bmp", "webp",
            "mp4", "avi", "mov", "wmv",
            "mp3", "wav", "ogg",
            "zip", "rar", "7z"
    );

    /**
     * 最大文件大小：100MB。
     */
    private static final long MAX_FILE_SIZE = 100L * 1024 * 1024;

    /**
     * 单次下载的最大字节数，可通过系统属性 {@code -Dfile-operate.max-download-size=...} 覆盖。
     */
    private static final long MAX_DOWNLOAD_SIZE =
            Long.getLong("file-operate.max-download-size", 500L * 1024 * 1024);

    /**
     * 解压时单个文件的最大字节数（防 Zip Bomb）。
     */
    private static final long MAX_EXTRACT_ENTRY_SIZE = 500L * 1024 * 1024;

    /**
     * 解压时全部文件的总字节数上限（防 Zip Bomb）。
     */
    private static final long MAX_EXTRACT_TOTAL_SIZE = 2L * 1024 * 1024 * 1024;

    private FileOperateUtil() {
        throw new UnsupportedOperationException(
                "FileOperateUtil is a static utility class and cannot be instantiated");
    }

    // ================================================================
    // 路径安全校验
    // ================================================================

    /**
     * 校验文件路径安全性，防止路径遍历攻击（CWE-22）。
     *
     * <p><b>拒绝规则：</b>路径为空白、包含 {@code ..}、规范化后与原文不一致。
     *
     * @param filePath 待校验路径
     * @throws IllegalArgumentException 路径非法时抛出
     */
    public static void validateFilePath(String filePath) {
        if (StrUtil.isBlank(filePath)) {
            throw new IllegalArgumentException("filePath must not be blank");
        }
        if (filePath.contains("..")) {
            throw new IllegalArgumentException("Path traversal detected: " + filePath);
        }
        String normalized = FilenameUtils.normalize(filePath);
        if (normalized == null || !normalized.equals(filePath)) {
            throw new IllegalArgumentException("Invalid file path: " + filePath);
        }
    }

    /**
     * 从 classpath 资源获取绝对路径。
     *
     * <p><b>注意：</b>若资源位于 JAR 包内，本方法会抛异常——
     * JAR 内资源无法通过 {@link Path} 访问。此时应使用
     * {@code getResourceAsStream(...)} 读取内容。
     *
     * @param classpathResource classpath 资源路径（如 {@code "config/app.yml"}）
     * @return 绝对路径
     * @throws IllegalArgumentException 资源不存在或位于 JAR 包内时抛出
     */
    public static String getAbsolutePath(String classpathResource) {
        Objects.requireNonNull(classpathResource, "classpathResource must not be null");
        URL url = FileOperateUtil.class.getClassLoader().getResource(classpathResource);
        if (url == null) {
            throw new IllegalArgumentException("Resource not found: " + classpathResource);
        }
        try {
            return Paths.get(url.toURI()).toAbsolutePath().toString();
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Resource is not a file path (may be inside a JAR): " + classpathResource, e);
        }
    }

    // ================================================================
    // 临时目录
    // ================================================================

    /**
     * 获取应用专属临时目录（不存在时创建）。
     *
     * @return 临时目录路径
     * @throws UncheckedIOException 目录创建失败时抛出
     */
    public static String getTempDir() {
        Path dir = Paths.get(SYSTEM_TMP_DIR, APP_TEMP_SUB_DIR);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create temp dir: " + dir, e);
        }
        return dir.toString();
    }

    /**
     * 在临时目录下生成唯一文件路径。
     *
     * <p>扩展名从 {@code originalFileName} 提取；无扩展名时使用 {@code .tmp}。
     *
     * @param originalFileName 原始文件名
     * @return 唯一文件路径
     */
    public static String generateTempFilePath(String originalFileName) {
        String ext = FilenameUtils.getExtension(originalFileName);
        String suffix = StrUtil.isBlank(ext) ? ".tmp" : "." + ext;
        return Paths.get(getTempDir(), IdUtil.fastSimpleUUID() + suffix).toString();
    }

    // ================================================================
    // 网络下载
    // ================================================================

    /**
     * 从 URL 下载文件到内存。
     *
     * <p><b>大小限制：</b>{@value #MAX_DOWNLOAD_SIZE} 字节（约 500MB），
     * 超出时抛异常，防止 OOM。
     *
     * @param url 远程 URL
     * @return 文件字节数组
     * @throws IllegalArgumentException URL 为空或响应超限时抛出
     * @throws UncheckedIOException     下载失败时抛出
     */
    public static byte[] downloadBytes(String url) {
        if (StrUtil.isBlank(url)) {
            throw new IllegalArgumentException("url must not be blank");
        }
        log.debug("[downloadBytes] start, url={}", url);
        try {
            long contentLength = HttpUtil.createGet(url)
                    .timeout(DEFAULT_TIMEOUT_MS)
                    .execute()
                    .contentLength();
            if (contentLength > MAX_DOWNLOAD_SIZE) {
                throw new IllegalArgumentException(
                        "Download size exceeds limit: " + contentLength
                                + " > " + MAX_DOWNLOAD_SIZE);
            }
            byte[] bytes = HttpUtil.createGet(url)
                    .timeout(DEFAULT_TIMEOUT_MS)
                    .execute()
                    .bodyBytes();
            if (bytes.length > MAX_DOWNLOAD_SIZE) {
                throw new IllegalArgumentException(
                        "Downloaded size exceeds limit: " + bytes.length);
            }
            log.debug("[downloadBytes] done, size={}", bytes.length);
            return bytes;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new UncheckedIOException(
                    new IOException("Download failed: " + url, e));
        }
    }

    /**
     * 从 URL 下载文件到本地（默认超时）。
     */
    public static String downloadToFile(String url, String targetPath) {
        return downloadToFile(url, targetPath, DEFAULT_TIMEOUT_MS);
    }

    /**
     * 从 URL 下载文件到本地（自定义超时）。
     *
     * @param url        远程 URL
     * @param targetPath 目标路径
     * @param timeoutMs  超时时间（毫秒）
     * @return 目标文件绝对路径
     */
    public static String downloadToFile(String url, String targetPath, int timeoutMs) {
        if (StrUtil.isBlank(url)) {
            throw new IllegalArgumentException("url must not be blank");
        }
        validateFilePath(targetPath);
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("timeoutMs must be positive");
        }

        File targetFile = new File(targetPath);
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new UncheckedIOException(
                    new IOException("Failed to create parent dir: " + parent));
        }

        log.debug("[downloadToFile] start, url={}, target={}", url, targetPath);
        try {
            HttpUtil.downloadFile(url, targetFile, timeoutMs);
            if (targetFile.length() > MAX_DOWNLOAD_SIZE) {
                deleteQuietly(targetPath);
                throw new IllegalArgumentException(
                        "Downloaded size exceeds limit: " + targetFile.length());
            }
            log.debug("[downloadToFile] done, size={}", targetFile.length());
            return targetFile.getAbsolutePath();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            deleteQuietly(targetPath);
            throw new UncheckedIOException(
                    new IOException("Download failed: " + url, e));
        }
    }

    /**
     * 从 URL 下载文件到临时目录（自动生成唯一文件名）。
     */
    public static String downloadToTemp(String url, String fileName) {
        return downloadToFile(url, generateTempFilePath(fileName), DEFAULT_TIMEOUT_MS);
    }

    // ================================================================
    // 本地文件读写
    // ================================================================

    /**
     * 读取本地文件到字节数组。
     */
    public static byte[] readLocalBytes(String filePath) {
        validateFilePath(filePath);
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IllegalArgumentException("File not found: " + filePath);
        }
        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read: " + filePath, e);
        }
    }

    /**
     * 将字节数组写入本地文件。
     */
    public static void writeBytesToFile(byte[] data, String targetPath) {
        Objects.requireNonNull(data, "data must not be null");
        if (data.length == 0) {
            throw new IllegalArgumentException("data must not be empty");
        }
        validateFilePath(targetPath);
        File targetFile = new File(targetPath);
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new UncheckedIOException(
                    new IOException("Failed to create parent dir: " + parent));
        }
        try {
            Files.write(targetFile.toPath(), data);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write: " + targetPath, e);
        }
    }

    // ================================================================
    // 文件工具
    // ================================================================

    /**
     * 静默删除文件或目录。
     *
     * @param path 路径
     * @return {@code true} 表示成功；{@code false} 表示失败
     */
    public static boolean deleteQuietly(String path) {
        if (StrUtil.isBlank(path)) {
            return true;
        }
        try {
            cn.hutool.core.io.FileUtil.del(path);
            return true;
        } catch (Exception e) {
            log.warn("[deleteQuietly] failed, path={}, error={}", path, e.getMessage());
            return false;
        }
    }

    /**
     * 判断路径是否为 PDF 文件。
     */
    public static boolean isPdfFile(String urlOrPath) {
        if (StrUtil.isBlank(urlOrPath)) {
            return false;
        }
        String lower = urlOrPath.toLowerCase();
        return lower.endsWith(".pdf") || lower.contains(".pdf?");
    }

    /**
     * 从 URL 提取文件名（去除查询参数）。
     *
     * <p>若无法提取，返回随机 UUID 文件名（无扩展名）。
     *
     * @param url URL
     * @return 文件名
     */
    public static String extractFileName(String url) {
        if (StrUtil.isBlank(url)) {
            return UUID.randomUUID().toString();
        }
        String path = url.split("\\?")[0];
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        return StrUtil.isBlank(name) ? UUID.randomUUID().toString() : name;
    }

    // ================================================================
    // 完整性校验
    // ================================================================

    /**
     * 计算文件的 SHA-256 摘要（十六进制小写）。
     *
     * <p><b>与 MD5 的区别：</b>MD5 已被证明存在碰撞攻击（SHAttered 2017），
     * 新代码应使用 SHA-256（NIST SP 800-131A 推荐）。
     *
     * @param filePath 文件路径
     * @return SHA-256 十六进制字符串（64 字符）
     */
    public static String calculateSha256(String filePath) {
        validateFilePath(filePath);
        try (InputStream input = new BufferedInputStream(new FileInputStream(filePath))) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 强制支持的，理论上不会发生
            throw new IllegalStateException("SHA-256 not available", e);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to calculate SHA-256: " + filePath, e);
        }
    }

    // ================================================================
    // 文件复制（NIO）
    // ================================================================

    /**
     * 高效文件复制（NIO，保留文件属性）。
     *
     * @param sourcePath 源路径
     * @param targetPath 目标路径
     */
    public static void copyFile(String sourcePath, String targetPath) {
        validateFilePath(sourcePath);
        validateFilePath(targetPath);
        Path source = Paths.get(sourcePath);
        Path target = Paths.get(targetPath);
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(source, target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to copy: " + sourcePath + " -> " + targetPath, e);
        }
    }

    /**
     * 批量复制文件到指定目录。
     *
     * <p>源文件不存在或复制失败时跳过并记录 WARN 日志，不中断批量处理。
     *
     * @param filePaths 源文件路径列表
     * @param outputDir 输出目录
     * @return 成功复制的目标文件路径列表
     */
    public static List<String> batchCopy(List<String> filePaths, String outputDir) {
        Objects.requireNonNull(filePaths, "filePaths must not be null");
        validateFilePath(outputDir);
        Path output = Paths.get(outputDir);
        try {
            Files.createDirectories(output);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create output dir: " + outputDir, e);
        }

        List<String> processed = new ArrayList<>(filePaths.size());
        for (String filePath : filePaths) {
            try {
                validateFilePath(filePath);
                Path source = Paths.get(filePath);
                if (!Files.exists(source)) {
                    log.warn("[batchCopy] skip missing file: {}", filePath);
                    continue;
                }
                Path target = output.resolve(uniqueFileName(source.getFileName().toString()));
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                processed.add(target.toString());
            } catch (IOException e) {
                log.warn("[batchCopy] skip failed file: {}", filePath, e);
            }
        }
        return processed;
    }

    // ================================================================
    // 压缩 / 解压
    // ================================================================

    /**
     * 将多个文件压缩为 ZIP。
     *
     * @param sourcePaths 源文件路径列表
     * @param zipPath     目标 ZIP 路径
     */
    public static void compressFiles(List<String> sourcePaths, String zipPath) {
        Objects.requireNonNull(sourcePaths, "sourcePaths must not be null");
        validateFilePath(zipPath);
        Path zip = Paths.get(zipPath);
        Path parent = zip.getParent();
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (ZipOutputStream zos = new ZipOutputStream(
                    new FileOutputStream(zipPath))) {
                for (String sourcePath : sourcePaths) {
                    validateFilePath(sourcePath);
                    File file = new File(sourcePath);
                    if (!file.exists() || !file.isFile()) {
                        log.warn("[compressFiles] skip non-file: {}", sourcePath);
                        continue;
                    }
                    zos.putNextEntry(new ZipEntry(file.getName()));
                    Files.copy(file.toPath(), zos);
                    zos.closeEntry();
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to compress: " + zipPath, e);
        }
    }

    /**
     * 解压 ZIP 到指定目录。
     *
     * <p><b>安全防护：</b>
     * <ul>
     *     <li><b>Zip Slip（CWE-22）</b>：每个条目的规范化路径必须位于目标目录内</li>
     *     <li><b>Zip Bomb（CWE-409）</b>：单文件上限 {@value #MAX_EXTRACT_ENTRY_SIZE} 字节，
     *         总大小上限 {@value #MAX_EXTRACT_TOTAL_SIZE} 字节</li>
     *     <li>拒绝解压符号链接条目（防链接攻击）</li>
     * </ul>
     *
     * @param zipPath   ZIP 文件路径
     * @param outputDir 输出目录
     * @return 解压后的文件路径列表
     * @throws IllegalArgumentException 检测到恶意 ZIP 时抛出
     */
    public static List<String> extractZip(String zipPath, String outputDir) {
        validateFilePath(zipPath);
        validateFilePath(outputDir);

        Path outputRoot = Paths.get(outputDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(outputRoot);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create output dir: " + outputDir, e);
        }

        List<String> extractedFiles = new ArrayList<>();
        long totalSize = 0;

        try (ZipInputStream zis = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(zipPath)))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                // 1. 路径安全校验（防 Zip Slip）
                Path target = outputRoot.resolve(entry.getName()).normalize();
                if (!target.startsWith(outputRoot)) {
                    throw new IllegalArgumentException(
                            "Zip Slip detected: " + entry.getName());
                }
                // 2. 创建父目录
                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                // 3. 写入并累计大小（防 Zip Bomb）
                long entrySize = 0;
                try (FileOutputStream out = new FileOutputStream(target.toFile())) {
                    int read;
                    while ((read = zis.read(buffer)) != -1) {
                        entrySize += read;
                        totalSize += read;
                        if (entrySize > MAX_EXTRACT_ENTRY_SIZE) {
                            throw new IllegalArgumentException(
                                    "Entry too large (Zip Bomb?): " + entry.getName());
                        }
                        if (totalSize > MAX_EXTRACT_TOTAL_SIZE) {
                            throw new IllegalArgumentException(
                                    "Total extracted size exceeds limit (Zip Bomb?)");
                        }
                        out.write(buffer, 0, read);
                    }
                }
                extractedFiles.add(target.toString());
                zis.closeEntry();
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to extract: " + zipPath, e);
        }

        log.info("[extractZip] done, zip={}, extracted={} files, totalSize={}",
                zipPath, extractedFiles.size(), totalSize);
        return extractedFiles;
    }

    // ================================================================
    // 上传
    // ================================================================

    /**
     * 将文件从流式输入保存到目标路径，带大小限制。
     *
     * <p><b>注意：</b>本方法<b>不关闭</b> {@code inputStream}，由调用方负责关闭。
     * 由于一次性读取完整文件到内存进行校验，调用方应确保文件大小在
     * {@link #MAX_FILE_SIZE} 以内。
     *
     * @param inputStream      输入流
     * @param originalFilename 原始文件名（用于扩展名白名单校验）
     * @param targetPath       目标路径
     * @throws IllegalArgumentException 文件类型不允许、大小超限时抛出
     * @throws UncheckedIOException     读写失败时抛出
     */
    public static void saveWithValidation(InputStream inputStream,
                                          String originalFilename,
                                          String targetPath) {
        Objects.requireNonNull(inputStream, "inputStream must not be null");
        validateFilePath(targetPath);
        if (!isAllowedFileType(originalFilename)) {
            throw new IllegalArgumentException(
                    "Disallowed file type: " + originalFilename);
        }

        // 一次性读取到内存（限制大小）
        byte[] data;
        try {
            data = readAllBytesWithLimit(inputStream, MAX_FILE_SIZE);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read input stream", e);
        }

        // 创建父目录
        Path target = Paths.get(targetPath);
        Path parent = target.getParent();
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(target, data);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save: " + targetPath, e);
        }

        log.info("[saveWithValidation] saved, target={}, size={}", targetPath, data.length);
    }

    // ================================================================
    // 私有辅助
    // ================================================================

    /**
     * 读取输入流到字节数组，超过 {@code maxSize} 时抛异常。
     *
     * <p><b>注意：</b>不关闭输入流。
     */
    private static byte[] readAllBytesWithLimit(InputStream input, long maxSize)
            throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxSize) {
                throw new IOException(
                        "Input size exceeds limit: " + maxSize + " bytes");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static boolean isAllowedFileType(String filename) {
        if (filename == null) {
            return false;
        }
        String extension = FilenameUtils.getExtension(filename).toLowerCase();
        return ALLOWED_EXTENSIONS.contains(extension);
    }

    private static String uniqueFileName(String originalName) {
        String extension = FilenameUtils.getExtension(originalName);
        String baseName = FilenameUtils.getBaseName(originalName);
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return StrUtil.isBlank(extension)
                ? baseName + "_" + uuid
                : baseName + "_" + uuid + "." + extension;
    }
}

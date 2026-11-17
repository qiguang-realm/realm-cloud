package cn.realm.cloud.framework.utils.file;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 文件操作与安全管理工具类（无 Commons IO 依赖）。
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
 * <p><b>依赖边界：</b>本类只依赖 JDK 标准库 + Hutool core（{@code StrUtil}、{@code IdUtil}），
 * <b>不依赖 Apache Commons IO</b>。扩展名与基础名解析复用
 * {@link FileConvertUtil#getExtension(String)}，与本模块的其他文件工具保持一致。
 *
 * <p><b>安全防护：</b>
 * <ul>
 *     <li><b>Zip Slip</b>（CWE-22）：解压时校验每个条目名的规范化路径必须在目标目录内</li>
 *     <li><b>Zip Bomb</b>（CWE-409）：解压时限制单文件大小与总大小</li>
 *     <li><b>下载限制</b>（CWE-770）：限制最大下载字节数，防止 OOM / 磁盘打满</li>
 *     <li><b>路径遍历</b>（CWE-22）：拒绝包含 {@code ..} 的路径，且要求 {@link Path#normalize()}
 *         不改变原路径元素序列</li>
 * </ul>
 *
 * <p><b>线程安全：</b>本类所有方法均为无状态设计，线程安全。
 *
 * @author QI Guang
 */
public final class FileOperateUtil {

    private static final Logger log = LoggerFactory.getLogger(FileOperateUtil.class);

    private static final int DEFAULT_TIMEOUT_MS = 30_000;

    private static final String SYSTEM_TMP_DIR = System.getProperty("java.io.tmpdir");

    private static final String APP_TEMP_SUB_DIR = "app" + File.separator + "temp";

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "txt", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "jpg", "jpeg", "png", "gif", "bmp", "webp",
            "mp4", "avi", "mov", "wmv",
            "mp3", "wav", "ogg",
            "zip", "rar", "7z"
    );

    private static final long MAX_FILE_SIZE = 100L * 1024 * 1024;

    private static final long MAX_DOWNLOAD_SIZE =
            Long.getLong("file-operate.max-download-size", 500L * 1024 * 1024);

    private static final long MAX_EXTRACT_ENTRY_SIZE = 500L * 1024 * 1024;

    private static final long MAX_EXTRACT_TOTAL_SIZE = 2L * 1024 * 1024 * 1024;

    private static final int BUFFER_SIZE = 8192;

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
     * <p><b>拒绝规则：</b>
     * <ol>
     *     <li>路径为空白</li>
     *     <li>路径包含 {@code ..}（路径遍历的直接特征）</li>
     *     <li>路径不是 {@link Path#normalize()} 的结果（含 {@code .}、{@code //} 等冗余段）</li>
     *     <li>路径包含平台非法字符（如 Windows 上的 {@code :}、{@code *}）</li>
     * </ol>
     *
     * <p><b>行为说明：</b>末尾分隔符（如 {@code "/a/b/"}）会被 {@link Path} 视为与
     * {@code "/a/b"} 相同，不构成安全风险，因此不拒绝。这与 {@code FilenameUtils.normalize}
     * 的严格字符串比较不同，但语义更合理。
     */
    public static void validateFilePath(String filePath) {
        if (StrUtil.isBlank(filePath)) {
            throw new IllegalArgumentException("filePath must not be blank");
        }
        if (filePath.contains("..")) {
            throw new IllegalArgumentException("Path traversal detected: " + filePath);
        }
        Path path;
        try {
            path = Paths.get(filePath);
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("Invalid file path: " + filePath, e);
        }
        if (!path.normalize().equals(path)) {
            throw new IllegalArgumentException("Invalid file path: " + filePath);
        }
    }

    /**
     * 从 classpath 资源获取绝对路径。
     *
     * <p><b>注意：</b>若资源位于 JAR 包内，本方法会抛异常——JAR 内资源无法通过
     * {@link Path} 访问。此时应使用 {@code getResourceAsStream(...)} 读取内容。
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
     * 扩展名解析复用 {@link FileConvertUtil#getExtension(String)}，返回小写、不含点号。
     */
    public static String generateTempFilePath(String originalFileName) {
        String ext = FileConvertUtil.getExtension(originalFileName);
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
     * <p><b>注意：</b>本方法依赖 {@code hutool-http}。若本类要留在
     * {@code realm-utils}（POM 仅含 {@code hutool-core}），必须将下载方法移出到
     * 出站调用能力模块，否则编译失败。迁移方案见架构文档的"网络下载"章节。
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

    public static boolean isPdfFile(String urlOrPath) {
        if (StrUtil.isBlank(urlOrPath)) {
            return false;
        }
        String lower = urlOrPath.toLowerCase();
        return lower.endsWith(".pdf") || lower.contains(".pdf?");
    }

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

    public static String calculateSha256(String filePath) {
        validateFilePath(filePath);
        try (InputStream input = new BufferedInputStream(new FileInputStream(filePath))) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to calculate SHA-256: " + filePath, e);
        }
    }

    // ================================================================
    // 文件复制
    // ================================================================

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
                    new BufferedOutputStream(new FileOutputStream(zipPath)))) {
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
     * </ul>
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
            byte[] buffer = new byte[BUFFER_SIZE];
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                Path target = outputRoot.resolve(entry.getName()).normalize();
                if (!target.startsWith(outputRoot)) {
                    throw new IllegalArgumentException("Zip Slip detected: " + entry.getName());
                }
                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
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

    public static void saveWithValidation(InputStream inputStream,
                                          String originalFilename,
                                          String targetPath) {
        Objects.requireNonNull(inputStream, "inputStream must not be null");
        validateFilePath(targetPath);
        if (!isAllowedFileType(originalFilename)) {
            throw new IllegalArgumentException("Disallowed file type: " + originalFilename);
        }

        byte[] data;
        try {
            data = readAllBytesWithLimit(inputStream, MAX_FILE_SIZE);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read input stream", e);
        }

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

    private static byte[] readAllBytesWithLimit(InputStream input, long maxSize)
            throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxSize) {
                throw new IOException("Input size exceeds limit: " + maxSize + " bytes");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static boolean isAllowedFileType(String filename) {
        if (filename == null) {
            return false;
        }
        // FileConvertUtil.getExtension 返回小写扩展名，空串表示无扩展名
        String extension = FileConvertUtil.getExtension(filename);
        return ALLOWED_EXTENSIONS.contains(extension);
    }

    /**
     * 生成唯一文件名：{@code baseName_xxxxxxxx.ext}。
     *
     * <p>使用 {@link FileConvertUtil#getExtension(String)} 提取扩展名，与模块内其他
     * 文件工具保持一致；基础名通过本地辅助方法提取，避免依赖 Commons IO。
     */
    private static String uniqueFileName(String originalName) {
        String extension = FileConvertUtil.getExtension(originalName);
        String baseName = getBaseName(originalName);
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return StrUtil.isBlank(extension)
                ? baseName + "_" + uuid
                : baseName + "_" + uuid + "." + extension;
    }

    /**
     * 提取文件名的基础部分（不含最后一个 {@code .} 及其之后的内容）。
     *
     * <p>与 {@code FilenameUtils.getBaseName} 行为一致：
     * {@code "foo.tar.gz"} → {@code "foo.tar"}；{@code "foo"} → {@code "foo"}；
     * {@code ".bashrc"} → {@code ".bashrc"}（点开头视为隐藏文件，整体为基础名）。
     */
    private static String getBaseName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int dotIndex = fileName.lastIndexOf('.');
        // dotIndex == 0 表示隐藏文件（如 ".bashrc"），整体作为基础名
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }
}

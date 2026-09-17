package cn.realm.cloud.framework.common.util.file;

import cn.hutool.core.io.IORuntimeException;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 专注文件操作和安全管理
 * <p>
 * 提供文件路径安全校验、网络下载、临时目录管理、MD5校验、压缩解压、
 * 以及各大厂（阿里、腾讯、字节、Google、京东）的安全规范实现。
 * </p>
 *
 * @author QI Guang
 * @version 2.0.0
 * @since 1.0.0
 */
public final class FileOperateUtil {

    private static final Logger logger = LoggerFactory.getLogger(FileOperateUtil.class);

    /**
     * 默认 HTTP 连接超时时间（毫秒）
     */
    private static final int DEFAULT_TIMEOUT = 30000;

    /**
     * 系统临时目录基础路径
     */
    private static final String SYSTEM_TMP_DIR = System.getProperty("java.io.tmpdir");

    /**
     * 应用专属临时子目录
     */
    private static final String APP_TEMP_SUB_DIR = "app" + File.separator + "temp";

    /**
     * 文件类型白名单（阿里安全规范）
     */
    private static final List<String> ALLOWED_EXTENSIONS = List.of(
            "txt", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "jpg", "jpeg", "png", "gif", "bmp", "webp",
            "mp4", "avi", "mov", "wmv",
            "mp3", "wav", "ogg",
            "zip", "rar", "7z"
    );

    /**
     * 最大文件大小限制：100MB（腾讯规范）
     */
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024;

    // ★ 静态块：JVM 关闭时递归删除临时目录
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                String tempDir = SYSTEM_TMP_DIR + File.separator + APP_TEMP_SUB_DIR;
                if (cn.hutool.core.io.FileUtil.exist(tempDir)) {
                    logger.info("JVM Shutdown: 正在清理临时目录: {}", tempDir);
                    cn.hutool.core.io.FileUtil.del(tempDir);
                }
            } catch (Exception e) {
                logger.warn("JVM Shutdown: 临时目录清理失败", e);
            }
        }));
    }

    private FileOperateUtil() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    // ================================================================
    // 基础文件操作
    // ================================================================

    public static String getAbsolutePath(String classpathResource) {
        try {
            URL resourceUrl = FileOperateUtil.class.getClassLoader().getResource(classpathResource);
            if (resourceUrl == null) {
                throw new RuntimeException("资源文件不存在: " + classpathResource);
            }
            return Paths.get(resourceUrl.toURI()).toAbsolutePath().toString();
        } catch (Exception e) {
            logger.error("获取文件路径失败: {}", classpathResource, e);
            throw new RuntimeException("获取文件路径失败: " + classpathResource, e);
        }
    }

    /**
     * 验证文件路径安全性（防止路径遍历攻击）
     */
    private static void validateFilePath(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new RuntimeException("文件路径不能为空");
        }
        if (filePath.contains("..") || filePath.contains("~") || filePath.contains("//")) {
            throw new RuntimeException("非法文件路径: " + filePath);
        }
        String normalizedPath = FilenameUtils.normalize(filePath);
        if (normalizedPath == null || !normalizedPath.equals(filePath)) {
            throw new RuntimeException("文件路径格式错误: " + filePath);
        }
    }

    // ================================================================
    // 临时目录管理
    // ================================================================

    /**
     * 获取应用专属的临时目录（确保目录存在）
     */
    public static String getTempDir() {
        String dirPath = SYSTEM_TMP_DIR + File.separator + APP_TEMP_SUB_DIR;
        File dir = new File(dirPath);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                logger.warn("创建临时目录失败: {}, 将回退到系统根 tmp", dirPath);
                return SYSTEM_TMP_DIR;
            }
        }
        return dirPath;
    }

    /**
     * 在临时目录下生成唯一的文件路径
     */
    public static String generateTempFilePath(String originalFileName) {
        String ext = "";
        if (StrUtil.isNotBlank(originalFileName) && originalFileName.contains(".")) {
            ext = originalFileName.substring(originalFileName.lastIndexOf("."));
        }
        if (StrUtil.isBlank(ext)) {
            ext = ".tmp";
        }
        String fileName = IdUtil.fastSimpleUUID() + ext;
        return getTempDir() + File.separator + fileName;
    }

    // ================================================================
    // 网络下载
    // ================================================================

    /**
     * 从远程 URL 下载文件到内存（零磁盘 IO）
     */
    public static byte[] downloadBytes(String url) {
        if (StrUtil.isBlank(url)) {
            throw new IllegalArgumentException("URL 不能为空");
        }
        logger.debug("[内存加载] 开始下载: {}", url);
        try {
            byte[] bytes = HttpUtil.createGet(url)
                    .timeout(DEFAULT_TIMEOUT)
                    .execute()
                    .bodyBytes();
            logger.debug("[内存加载] 完成, 大小: {} bytes", bytes.length);
            return bytes;
        } catch (Exception e) {
            logger.error("[内存加载] 下载失败: {}", url, e);
            throw new RuntimeException("远程文件下载失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从远程 URL 下载文件并保存到本地
     */
    public static String downloadToFile(String url, String targetPath) {
        return downloadToFile(url, targetPath, DEFAULT_TIMEOUT);
    }

    /**
     * 从远程 URL 下载文件并保存到本地（可自定义超时时间）
     */
    public static String downloadToFile(String url, String targetPath, int timeout) {
        if (StrUtil.isBlank(url)) {
            throw new IllegalArgumentException("URL 不能为空");
        }
        if (StrUtil.isBlank(targetPath)) {
            throw new IllegalArgumentException("目标路径不能为空");
        }

        File targetFile = cn.hutool.core.io.FileUtil.file(targetPath);
        cn.hutool.core.io.FileUtil.mkParentDirs(targetFile);

        logger.debug("[落盘加载] 开始下载文件: {} -> {}", url, targetPath);

        try {
            HttpUtil.downloadFile(url, targetFile, timeout);
            logger.debug("[落盘加载] 下载完成, 大小: {} bytes", targetFile.length());
            return targetFile.getAbsolutePath();
        } catch (Exception e) {
            logger.error("[落盘加载] 下载失败: {}", url, e);
            cn.hutool.core.io.FileUtil.del(targetFile);
            throw new RuntimeException("远程文件下载失败: " + e.getMessage(), e);
        }
    }

    /**
     * 下载文件到系统临时目录（自动生成唯一文件名）
     */
    public static String downloadToTemp(String url, String fileName) {
        String targetPath = generateTempFilePath(fileName);
        return downloadToFile(url, targetPath, DEFAULT_TIMEOUT);
    }

    // ================================================================
    // 本地文件读取与写入
    // ================================================================

    /**
     * 读取本地文件内容到字节数组
     */
    public static byte[] readLocalBytes(String filePath) {
        if (StrUtil.isBlank(filePath)) {
            throw new IllegalArgumentException("文件路径不能为空");
        }

        File file = cn.hutool.core.io.FileUtil.file(filePath);
        if (!cn.hutool.core.io.FileUtil.exist(file)) {
            throw new RuntimeException("本地文件不存在: " + filePath);
        }

        try {
            byte[] bytes = cn.hutool.core.io.FileUtil.readBytes(file);
            logger.debug("[读取本地] 文件: {}, 大小: {} bytes", filePath, bytes.length);
            return bytes;
        } catch (IORuntimeException e) {
            logger.error("[读取本地] 文件读取失败: {}", filePath, e);
            throw new RuntimeException("本地文件读取失败: " + e.getMessage(), e);
        }
    }

    /**
     * 将字节数组写入本地文件
     */
    public static void writeBytesToFile(byte[] data, String targetPath) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("写入数据不能为空");
        }
        if (StrUtil.isBlank(targetPath)) {
            throw new IllegalArgumentException("目标路径不能为空");
        }

        File targetFile = cn.hutool.core.io.FileUtil.file(targetPath);
        cn.hutool.core.io.FileUtil.mkParentDirs(targetFile);

        logger.debug("[写入文件] 保存文件: {}, 大小: {} bytes", targetPath, data.length);
        try {
            cn.hutool.core.io.FileUtil.writeBytes(data, targetFile);
        } catch (IORuntimeException e) {
            logger.error("[写入文件] 保存失败: {}", targetPath, e);
            throw new RuntimeException("文件保存失败: " + e.getMessage(), e);
        }
    }

    // ================================================================
    // 文件工具方法
    // ================================================================

    /**
     * 安全删除文件或目录（递归删除）
     */
    public static boolean deleteQuietly(String path) {
        if (StrUtil.isBlank(path)) {
            return true;
        }
        try {
            cn.hutool.core.io.FileUtil.del(path);
            return true;
        } catch (Exception e) {
            logger.warn("[清理文件] 删除失败: {}, 原因: {}", path, e.getMessage());
            return false;
        }
    }

    /**
     * 判断文件是否为 PDF（根据 URL 或路径扩展名）
     */
    public static boolean isPdfFile(String urlOrPath) {
        if (StrUtil.isBlank(urlOrPath)) {
            return false;
        }
        String lower = urlOrPath.toLowerCase();
        return lower.endsWith(".pdf") || lower.contains(".pdf?");
    }

    /**
     * 从 URL 提取文件名（去除查询参数）
     */
    public static String extractFileName(String url) {
        if (StrUtil.isBlank(url)) {
            return UUID.randomUUID().toString();
        }
        String path = url.split("\\?")[0];
        String name = path.substring(path.lastIndexOf("/") + 1);
        return StrUtil.isBlank(name) ? UUID.randomUUID().toString() : name;
    }

    // ================================================================
    // 大厂安全规范实现
    // ================================================================

    /**
     * 阿里方案：分片上传（适合大文件）
     */
    public static void aliUploadWithChunking(InputStream inputStream, String targetPath, int chunkSize) {
        validateFilePath(targetPath);

        try (FileOutputStream fos = new FileOutputStream(targetPath);
             BufferedOutputStream bos = new BufferedOutputStream(fos)) {

            byte[] buffer = new byte[chunkSize];
            int bytesRead;
            long totalBytes = 0;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;

                if (totalBytes % (1024 * 1024) == 0) {
                    logger.info("阿里分片上传进度: {} MB", totalBytes / (1024 * 1024));
                }
            }

            logger.info("阿里分片上传完成: {}, 总大小: {} MB", targetPath, totalBytes / (1024 * 1024));

        } catch (IOException e) {
            logger.error("阿里分片上传失败: {}", targetPath, e);
            throw new RuntimeException("文件上传失败", e);
        }
    }

    /**
     * 腾讯方案：文件下载带MD5校验
     */
    public static void tencentDownloadWithVerification(String sourcePath, String targetPath) {
        validateFilePath(sourcePath);
        validateFilePath(targetPath);

        try {
            File sourceFile = new File(sourcePath);
            if (!sourceFile.exists()) {
                throw new RuntimeException("源文件不存在: " + sourcePath);
            }

            String sourceMd5 = calculateMD5(sourcePath);
            logger.info("腾讯下载校验 - 源文件MD5: {}", sourceMd5);

            Files.copy(Paths.get(sourcePath), Paths.get(targetPath), StandardCopyOption.REPLACE_EXISTING);

            String targetMd5 = calculateMD5(targetPath);
            if (!sourceMd5.equals(targetMd5)) {
                throw new RuntimeException("文件下载完整性校验失败");
            }

            logger.info("腾讯下载完成: {}, MD5校验通过", targetPath);

        } catch (IOException e) {
            logger.error("腾讯下载失败", e);
            throw new RuntimeException("文件下载失败", e);
        }
    }

    /**
     * 字节方案：安全文件上传（类型检查+大小限制+病毒扫描模拟）
     */
    public static void bytedanceSafeUpload(InputStream inputStream, String originalFilename, String targetPath) {
        validateFilePath(targetPath);

        if (!isAllowedFileType(originalFilename)) {
            throw new RuntimeException("不支持的文件类型: " + originalFilename);
        }

        try {
            checkFileSize(inputStream, MAX_FILE_SIZE);

            if (!virusScan(inputStream)) {
                throw new RuntimeException("文件安全扫描未通过");
            }

            inputStream.reset();

            Files.copy(inputStream, Paths.get(targetPath), StandardCopyOption.REPLACE_EXISTING);

            logger.info("字节安全上传完成: {}", targetPath);

        } catch (IOException e) {
            logger.error("字节上传失败", e);
            throw new RuntimeException("文件上传失败", e);
        }
    }

    /**
     * Google方案：高效文件复制（使用NIO）
     */
    public static void googleEfficientCopy(String sourcePath, String targetPath) {
        validateFilePath(sourcePath);
        validateFilePath(targetPath);

        try {
            Path source = Paths.get(sourcePath);
            Path target = Paths.get(targetPath);

            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);

            logger.info("Google高效复制完成: {} -> {}", sourcePath, targetPath);

        } catch (IOException e) {
            logger.error("Google复制失败", e);
            throw new RuntimeException("文件复制失败", e);
        }
    }

    /**
     * 京东方案：批量文件处理
     */
    public static List<String> jdBatchProcess(List<String> filePaths, String outputDir) {
        validateFilePath(outputDir);

        List<String> processedFiles = new ArrayList<>();
        File outputDirectory = new File(outputDir);
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs();
        }

        for (String filePath : filePaths) {
            validateFilePath(filePath);
            try {
                File file = new File(filePath);
                if (file.exists()) {
                    String uniqueName = generateUniqueFileName(file.getName());
                    String targetPath = outputDir + File.separator + uniqueName;

                    Files.copy(file.toPath(), Paths.get(targetPath), StandardCopyOption.REPLACE_EXISTING);
                    processedFiles.add(targetPath);

                    logger.info("京东批量处理: {} -> {}", filePath, targetPath);
                }
            } catch (IOException e) {
                logger.warn("京东批量处理跳过文件: {}", filePath, e);
            }
        }

        return processedFiles;
    }

    // ================================================================
    // 压缩/解压
    // ================================================================

    public static void compressFiles(List<String> sourcePaths, String zipPath) {
        validateFilePath(zipPath);

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath))) {
            for (String sourcePath : sourcePaths) {
                validateFilePath(sourcePath);
                File file = new File(sourcePath);
                if (file.exists()) {
                    zos.putNextEntry(new ZipEntry(file.getName()));
                    Files.copy(file.toPath(), zos);
                    zos.closeEntry();
                }
            }
            logger.info("文件压缩完成: {}", zipPath);
        } catch (IOException e) {
            logger.error("文件压缩失败", e);
            throw new RuntimeException("文件压缩失败", e);
        }
    }

    public static List<String> extractZip(String zipPath, String outputDir) {
        validateFilePath(zipPath);
        validateFilePath(outputDir);

        List<String> extractedFiles = new ArrayList<>();
        File outputDirectory = new File(outputDir);
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs();
        }

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    String entryPath = outputDir + File.separator + entry.getName();
                    Files.copy(zis, Paths.get(entryPath), StandardCopyOption.REPLACE_EXISTING);
                    extractedFiles.add(entryPath);
                    zis.closeEntry();
                }
            }
            logger.info("文件解压完成: {} -> {}", zipPath, outputDir);
        } catch (IOException e) {
            logger.error("文件解压失败", e);
            throw new RuntimeException("文件解压失败", e);
        }

        return extractedFiles;
    }

    // ================================================================
    // 通用工具方法
    // ================================================================

    private static String calculateMD5(String filePath) {
        try (InputStream inputStream = new FileInputStream(filePath)) {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int read;

            while ((read = inputStream.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }

            byte[] md5Bytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : md5Bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();

        } catch (Exception e) {
            throw new RuntimeException("MD5计算失败", e);
        }
    }

    private static boolean isAllowedFileType(String filename) {
        if (filename == null) {
            return false;
        }
        String extension = FilenameUtils.getExtension(filename).toLowerCase();
        return ALLOWED_EXTENSIONS.contains(extension);
    }

    private static void checkFileSize(InputStream inputStream, long maxSize) throws IOException {
        long size = 0;
        byte[] buffer = new byte[8192];
        int read;

        while ((read = inputStream.read(buffer)) != -1) {
            size += read;
            if (size > maxSize) {
                throw new RuntimeException("文件大小超过限制: " + (maxSize / 1024 / 1024) + "MB");
            }
        }

        inputStream.reset();
    }

    private static boolean virusScan(InputStream inputStream) {
        try {
            byte[] header = new byte[1024];
            inputStream.read(header);
            inputStream.reset();
            return Math.random() > 0.001;
        } catch (IOException e) {
            return false;
        }
    }

    private static String generateUniqueFileName(String originalName) {
        String extension = FilenameUtils.getExtension(originalName);
        String baseName = FilenameUtils.getBaseName(originalName);
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return baseName + "_" + uuid + "." + extension;
    }
}

package cn.realm.cloud.framework.common.util.file;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 专注格式转换
 * <p>
 * 基于 Apache Commons IO + Google Guava 构建，提供 byte[]、File、InputStream、
 * OutputStream、MultipartFile 之间的统一转换能力。
 * </p>
 *
 * <p><b>核心设计原则：</b></p>
 * <ul>
 *   <li>以 byte[] 为数据核心（最底层、最通用、最安全）</li>
 *   <li>所有转换方法命名统一：toXxx() 表示转换为 Xxx 类型</li>
 *   <li>支持链式调用和批量操作</li>
 *   <li>自动管理资源（try-with-resources）</li>
 * </ul>
 *
 * @author QI Guang
 * @version 2.0.0
 * @since 1.0.0
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public final class FileConvertUtil {

    private static final int DEFAULT_BUFFER_SIZE = 8192;

    private FileConvertUtil() {
    }

    // ================================================================
    // 📥 转换为 byte[]
    // ================================================================

    public static byte[] toBytes(File file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("File 不能为 null");
        }
        if (!file.exists()) {
            throw new FileNotFoundException("文件不存在: " + file.getAbsolutePath());
        }
        return FileUtils.readFileToByteArray(file);
    }

    public static byte[] toBytes(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            throw new IllegalArgumentException("InputStream 不能为 null");
        }
        try (InputStream is = inputStream) {
            return IOUtils.toByteArray(is);
        }
    }

    public static byte[] toBytes(MultipartFile multipartFile) throws IOException {
        if (multipartFile == null) {
            throw new IllegalArgumentException("MultipartFile 不能为 null");
        }
        if (multipartFile.isEmpty()) {
            return new byte[0];
        }
        return multipartFile.getBytes();
    }

    public static byte[] toBytes(Reader reader) throws IOException {
        if (reader == null) {
            throw new IllegalArgumentException("Reader 不能为 null");
        }
        try (Reader r = reader) {
            char[] buffer = new char[DEFAULT_BUFFER_SIZE];
            StringBuilder sb = new StringBuilder();
            int len;
            while ((len = r.read(buffer)) != -1) {
                sb.append(buffer, 0, len);
            }
            return sb.toString().getBytes(StandardCharsets.UTF_8);
        }
    }

    public static byte[] toBytes(String content) {
        if (content == null) {
            return new byte[0];
        }
        return content.getBytes(StandardCharsets.UTF_8);
    }

    // ================================================================
    // 📄 转换为 File
    // ================================================================

    public static void toFile(byte[] bytes, File file) throws IOException {
        if (bytes == null) {
            throw new IllegalArgumentException("byte[] 不能为 null");
        }
        if (file == null) {
            throw new IllegalArgumentException("File 不能为 null");
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            FileUtils.forceMkdir(parent);
        }
        FileUtils.writeByteArrayToFile(file, bytes);
    }

    public static void toFile(InputStream inputStream, File file) throws IOException {
        if (inputStream == null) {
            throw new IllegalArgumentException("InputStream 不能为 null");
        }
        if (file == null) {
            throw new IllegalArgumentException("File 不能为 null");
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            FileUtils.forceMkdir(parent);
        }
        try (InputStream is = inputStream) {
            FileUtils.copyInputStreamToFile(is, file);
        }
    }

    public static void toFile(String content, File file) throws IOException {
        if (content == null) {
            throw new IllegalArgumentException("content 不能为 null");
        }
        toFile(content.getBytes(StandardCharsets.UTF_8), file);
    }

    /**
     * 将字节数组写入临时文件（使用系统临时目录）
     * <p>文件会在 JVM 退出时自动删除（deleteOnExit）</p>
     *
     * @param bytes  字节数组
     * @param prefix 文件名前缀
     * @param suffix 文件名后缀（如 ".pdf"）
     * @return 临时文件对象
     */
    public static File toTempFile(byte[] bytes, String prefix, String suffix) throws IOException {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("字节数组不能为空");
        }
        String ext = (suffix != null && suffix.startsWith(".")) ? suffix : (suffix != null ? "." + suffix : ".tmp");
        String fileName = (prefix != null ? prefix : "file_") + System.currentTimeMillis() + ext;
        File tempFile = File.createTempFile(prefix != null ? prefix : "file_", ext);
        tempFile.deleteOnExit();
        toFile(bytes, tempFile);
        return tempFile;
    }

    public static File toTempFile(InputStream inputStream, String prefix, String suffix) throws IOException {
        if (inputStream == null) {
            throw new IllegalArgumentException("InputStream 不能为 null");
        }
        String ext = (suffix != null && suffix.startsWith(".")) ? suffix : (suffix != null ? "." + suffix : ".tmp");
        File tempFile = File.createTempFile(prefix != null ? prefix : "file_", ext);
        tempFile.deleteOnExit();
        toFile(inputStream, tempFile);
        return tempFile;
    }

    // ================================================================
    // 📤 转换为 InputStream
    // ================================================================

    public static InputStream toInputStream(byte[] bytes) {
        if (bytes == null) {
            return new ByteArrayInputStream(new byte[0]);
        }
        return new ByteArrayInputStream(bytes);
    }

    public static InputStream toInputStream(File file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("File 不能为 null");
        }
        if (!file.exists()) {
            throw new FileNotFoundException("文件不存在: " + file.getAbsolutePath());
        }
        return new FileInputStream(file);
    }

    public static InputStream toInputStream(String content) {
        if (content == null) {
            return new ByteArrayInputStream(new byte[0]);
        }
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    // ================================================================
    // 🌐 转换为 MultipartFile
    // ================================================================

    public static MultipartFile toMultipartFile(byte[] bytes, String name,
                                                String originalFilename, String contentType) {
        if (bytes == null) {
            bytes = new byte[0];
        }
        return new ByteArrayMultipartFile(
                bytes,
                name != null ? name : "file",
                originalFilename != null ? originalFilename : "file.bin",
                contentType != null ? contentType : "application/octet-stream"
        );
    }

    public static MultipartFile toMultipartFile(File file, String name) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("File 不能为 null");
        }
        if (!file.exists()) {
            throw new FileNotFoundException("文件不存在: " + file.getAbsolutePath());
        }
        if (!file.isFile()) {
            throw new IllegalArgumentException("路径不是文件: " + file.getAbsolutePath());
        }
        byte[] bytes = toBytes(file);
        String fileName = file.getName();
        String contentType = Files.probeContentType(file.toPath());
        if (contentType == null) {
            contentType = getContentType(fileName);
        }
        return toMultipartFile(bytes, name, fileName, contentType);
    }

    public static MultipartFile toMultipartFile(File file) throws IOException {
        return toMultipartFile(file, null);
    }

    public static MultipartFile copyMultipartFile(MultipartFile multipartFile, String newName) throws IOException {
        if (multipartFile == null) {
            throw new IllegalArgumentException("MultipartFile 不能为 null");
        }
        byte[] bytes = toBytes(multipartFile);
        return toMultipartFile(
                bytes,
                newName != null ? newName : multipartFile.getName(),
                multipartFile.getOriginalFilename(),
                multipartFile.getContentType()
        );
    }

    // ================================================================
    // 📋 文件操作工具方法
    // ================================================================

    public static void copy(File source, File target) throws IOException {
        if (source == null || target == null) {
            throw new IllegalArgumentException("源文件或目标文件不能为 null");
        }
        if (!source.exists()) {
            throw new FileNotFoundException("源文件不存在: " + source.getAbsolutePath());
        }
        FileUtils.copyFile(source, target);
    }

    public static void copyNio(File source, File target) throws IOException {
        if (source == null || target == null) {
            throw new IllegalArgumentException("源文件或目标文件不能为 null");
        }
        if (!source.exists()) {
            throw new FileNotFoundException("源文件不存在: " + source.getAbsolutePath());
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            FileUtils.forceMkdir(parent);
        }
        Path sourcePath = source.toPath();
        Path targetPath = target.toPath();
        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
    }

    public static long copy(InputStream input, OutputStream output) throws IOException {
        if (input == null || output == null) {
            throw new IllegalArgumentException("输入流或输出流不能为 null");
        }
        try (InputStream is = input; OutputStream os = output) {
            return IOUtils.copyLarge(is, os);
        }
    }

    public static java.util.List<String> readLines(File file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("File 不能为 null");
        }
        if (!file.exists()) {
            throw new FileNotFoundException("文件不存在: " + file.getAbsolutePath());
        }
        return FileUtils.readLines(file, StandardCharsets.UTF_8);
    }

    public static void writeString(File file, String content) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("File 不能为 null");
        }
        if (content == null) {
            content = "";
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            FileUtils.forceMkdir(parent);
        }
        FileUtils.writeStringToFile(file, content, StandardCharsets.UTF_8);
    }

    public static long size(File file) {
        if (file == null || !file.exists()) {
            return 0;
        }
        return file.length();
    }

    public static boolean exists(File file) {
        return file != null && file.exists();
    }

    public static boolean delete(File file) {
        if (file == null || !file.exists()) {
            return false;
        }
        return FileUtils.deleteQuietly(file);
    }

    public static String getExtension(File file) {
        if (file == null) {
            return "";
        }
        return getExtension(file.getName());
    }

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

    // ================================================================
    // 💡 便捷工具方法
    // ================================================================

    public static boolean isImage(File file) {
        if (file == null) {
            return false;
        }
        String ext = getExtension(file);
        return "jpg".equals(ext) || "jpeg".equals(ext) ||
                "png".equals(ext) || "gif".equals(ext) ||
                "bmp".equals(ext) || "webp".equals(ext) ||
                "svg".equals(ext);
    }

    public static boolean isPdf(File file) {
        if (file == null) {
            return false;
        }
        String ext = getExtension(file);
        return "pdf".equalsIgnoreCase(ext);
    }

    public static boolean isEmpty(File file) {
        return file == null || !file.exists() || file.length() == 0;
    }

    public static String getFileName(File file) {
        return file == null ? "" : file.getName();
    }

    /**
     * 根据文件名获取 Content-Type（MIME 类型）
     *
     * @param fileName 文件名
     * @return MIME 类型
     */
    public static String getContentType(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "application/octet-stream";
        }
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        } else if (lower.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        } else if (lower.endsWith(".doc")) {
            return "application/msword";
        } else if (lower.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        } else if (lower.endsWith(".xls")) {
            return "application/vnd.ms-excel";
        } else if (lower.endsWith(".pptx")) {
            return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        } else if (lower.endsWith(".ppt")) {
            return "application/vnd.ms-powerpoint";
        } else if (lower.endsWith(".png")) {
            return "image/png";
        } else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (lower.endsWith(".gif")) {
            return "image/gif";
        } else if (lower.endsWith(".bmp")) {
            return "image/bmp";
        } else if (lower.endsWith(".webp")) {
            return "image/webp";
        } else if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        } else if (lower.endsWith(".txt")) {
            return "text/plain";
        } else if (lower.endsWith(".json")) {
            return "application/json";
        } else if (lower.endsWith(".xml")) {
            return "application/xml";
        } else if (lower.endsWith(".zip")) {
            return "application/zip";
        } else if (lower.endsWith(".rar")) {
            return "application/x-rar-compressed";
        } else if (lower.endsWith(".7z")) {
            return "application/x-7z-compressed";
        } else if (lower.endsWith(".mp4")) {
            return "video/mp4";
        } else if (lower.endsWith(".mp3")) {
            return "audio/mpeg";
        } else if (lower.endsWith(".wav")) {
            return "audio/wav";
        } else {
            return "application/octet-stream";
        }
    }

    // ================================================================
    // 🔧 私有内部类：MultipartFile 自定义实现
    // ================================================================

    private static final class ByteArrayMultipartFile implements MultipartFile {

        private final byte[] content;
        private final String name;
        private final String originalFilename;
        private final String contentType;

        private ByteArrayMultipartFile(byte[] content, String name,
                                       String originalFilename, String contentType) {
            this.content = content != null ? content : new byte[0];
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
            return content.clone();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(File dest) throws IOException {
            if (dest == null) {
                throw new IllegalArgumentException("目标文件不能为 null");
            }
            toFile(content, dest);
        }
    }
}

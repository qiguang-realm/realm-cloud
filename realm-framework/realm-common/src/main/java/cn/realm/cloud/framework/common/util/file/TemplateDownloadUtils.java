package cn.realm.cloud.framework.common.util.file;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.io.*;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 通用文件下载 / 预览工具类。
 *
 * <p>特性：
 * <ul>
 *     <li>支持 classpath 资源、本地文件、字节数组、任意 InputStream；</li>
 *     <li>支持 attachment 下载 与 inline 预览；</li>
 *     <li>中文文件名按 RFC 5987 编码，附带 ASCII fallback；</li>
 *     <li>自动根据扩展名推断 Content-Type；</li>
 *     <li>尽可能设置 Content-Length，便于前端展示进度；</li>
 *     <li>不破坏已有的 CORS / 业务 Header；</li>
 *     <li>保证输入流被正确关闭。</li>
 * </ul>
 *
 * <p>使用前请确认：response 尚未调用 {@code getWriter()}，也未 commit。
 *
 * @author QI Guang
 */
@Slf4j
public final class TemplateDownloadUtils {

    /**
     * 默认下载文件名（无法从入参推断时使用）
     */
    private static final String DEFAULT_FILE_NAME = "download";

    /**
     * 缓冲区大小 8KB
     */
    private static final int BUFFER_SIZE = 8192;

    /**
     * ASCII fallback 中允许的字符范围之外，一律替换
     */
    private static final String ASCII_FALLBACK_REPLACEMENT = "_";

    private TemplateDownloadUtils() {
    }

    /* ==================== 对外 API ==================== */

    /**
     * 下载 classpath 下的模板文件（最常用）。
     *
     * @param response      HttpServletResponse
     * @param classpathPath classpath 路径，如 "templates/import_template.xlsx"
     * @param downloadName  下载后展示的文件名，如 "用户导入模板.xlsx"
     */
    public static void downloadClasspathTemplate(HttpServletResponse response,
                                                 String classpathPath,
                                                 String downloadName) {
        ClassPathResource resource = new ClassPathResource(classpathPath);
        if (!resource.exists()) {
            throw new TemplateDownloadException("模板文件不存在: " + classpathPath);
        }
        long contentLength;
        try {
            contentLength = resource.contentLength();
        } catch (IOException e) {
            contentLength = -1L;
        }
        try (InputStream in = resource.getInputStream()) {
            write(response, in, resolveFileName(downloadName, resource.getFilename()),
                    false, contentLength);
        } catch (IOException e) {
            throw new TemplateDownloadException("模板文件读取失败: " + classpathPath, e);
        }
    }

    /**
     * 在线预览 classpath 下的模板（浏览器能识别的类型会直接打开，如 pdf、图片）。
     */
    public static void previewClasspathTemplate(HttpServletResponse response,
                                                String classpathPath,
                                                String fileName) {
        ClassPathResource resource = new ClassPathResource(classpathPath);
        if (!resource.exists()) {
            throw new TemplateDownloadException("模板文件不存在: " + classpathPath);
        }
        long contentLength;
        try {
            contentLength = resource.contentLength();
        } catch (IOException e) {
            contentLength = -1L;
        }
        try (InputStream in = resource.getInputStream()) {
            write(response, in, resolveFileName(fileName, resource.getFilename()),
                    true, contentLength);
        } catch (IOException e) {
            throw new TemplateDownloadException("模板文件读取失败: " + classpathPath, e);
        }
    }

    /**
     * 下载本地磁盘文件。
     *
     * <p><b>安全提醒：</b>如果 {@code filePath} 来自用户输入，调用方必须先做路径穿越校验，
     * 否则可能被读取任意系统文件。
     *
     * @param filePath 绝对路径，如 "D:/templates/demo.xlsx"
     */
    public static void downloadLocalFile(HttpServletResponse response,
                                         String filePath,
                                         String downloadName) {
        if (filePath == null || filePath.isBlank()) {
            throw new TemplateDownloadException("文件路径不能为空");
        }
        Path path = Paths.get(filePath);
        File file = path.toFile();
        if (!file.exists() || !file.isFile()) {
            throw new TemplateDownloadException("文件不存在: " + filePath);
        }
        try (InputStream in = Files.newInputStream(path)) {
            write(response, in, resolveFileName(downloadName, file.getName()),
                    false, file.length());
        } catch (IOException e) {
            throw new TemplateDownloadException("文件读取失败: " + filePath, e);
        }
    }

    /**
     * 下载内存中的字节数组（例如 EasyExcel / POI 动态生成的模板）。
     */
    public static void downloadBytes(HttpServletResponse response,
                                     byte[] data,
                                     String downloadName) {
        if (data == null) {
            throw new TemplateDownloadException("文件内容不能为空");
        }
        try (InputStream in = new ByteArrayInputStream(data)) {
            write(response, in, resolveFileName(downloadName, null), false, data.length);
        } catch (IOException e) {
            throw new TemplateDownloadException("文件写出失败: " + downloadName, e);
        }
    }

    /**
     * 下载任意输入流。
     *
     * <p><b>注意：</b>本方法负责关闭传入的 {@code InputStream}（无论成功或失败）。
     */
    public static void downloadStream(HttpServletResponse response,
                                      InputStream in,
                                      String downloadName) {
        downloadStream(response, in, downloadName, -1L);
    }

    /**
     * 下载任意输入流，并显式指定内容长度（可选，用于前端展示下载进度）。
     *
     * @param contentLength 内容长度，未知传 {@code -1}
     */
    public static void downloadStream(HttpServletResponse response,
                                      InputStream in,
                                      String downloadName,
                                      long contentLength) {
        if (in == null) {
            throw new TemplateDownloadException("输入流不能为空");
        }
        try (InputStream input = in) {
            write(response, input, resolveFileName(downloadName, null), false, contentLength);
        } catch (IOException e) {
            throw new TemplateDownloadException("文件写出失败: " + downloadName, e);
        }
    }

    /* ==================== 核心写出逻辑 ==================== */

    /**
     * 把输入流写到 response。
     *
     * @param inline        true=浏览器内联预览，false=作为附件下载
     * @param contentLength 内容长度，未知传 -1
     */
    private static void write(HttpServletResponse response,
                              InputStream in,
                              String fileName,
                              boolean inline,
                              long contentLength) throws IOException {

        if (response.isCommitted()) {
            throw new TemplateDownloadException(
                    "响应已提交，无法写出文件: " + fileName);
        }

        // 只清 body buffer，不动 CORS、业务 Header 以及状态码
        response.resetBuffer();

        // 只对文本类型设置字符集，避免二进制文件被附加 charset=UTF-8
        String contentType = resolveContentType(fileName);
        response.setContentType(contentType);
        if (isTextContentType(contentType)) {
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        }

        // 附件名
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                buildContentDisposition(fileName, inline));

        // 只有当前没设置过才追加，避免覆盖全局 CORS 配置
        appendExposeHeader(response, HttpHeaders.CONTENT_DISPOSITION);

        // Content-Length（可选，但更友好）
        if (contentLength >= 0 && contentLength <= Integer.MAX_VALUE) {
            response.setContentLengthLong(contentLength);
        }

        try (OutputStream out = response.getOutputStream()) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            out.flush();
        } catch (IOException e) {
            log.error("文件写出失败, fileName={}", fileName, e);
            throw e;
        }
    }

    /**
     * 构建 Content-Disposition，处理中文文件名乱码。
     *
     * <p>格式：{@code attachment; filename="ascii_fallback"; filename*=UTF-8''%E4%B8%AD%E6%96%87.xlsx}
     */
    private static String buildContentDisposition(String fileName, boolean inline) {
        String type = inline ? "inline" : "attachment";
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                .replace("+", "%20");
        String asciiFallback = buildAsciiFallback(fileName);
        return String.format("%s; filename=\"%s\"; filename*=UTF-8''%s",
                type, asciiFallback, encoded);
    }

    /**
     * 生成 ASCII fallback 文件名。老浏览器 / 部分客户端只认 filename。
     */
    private static String buildAsciiFallback(String fileName) {
        StringBuilder sb = new StringBuilder(fileName.length());
        for (int i = 0; i < fileName.length(); i++) {
            char c = fileName.charAt(i);
            // 只保留可见 ASCII，并去掉会破坏 header 语法的字符
            if (c >= 0x20 && c <= 0x7E && c != '"' && c != '\\' && c != ';') {
                sb.append(c);
            } else {
                sb.append(ASCII_FALLBACK_REPLACEMENT);
            }
        }
        return sb.length() == 0 ? DEFAULT_FILE_NAME : sb.toString();
    }

    /**
     * 根据文件名推断 Content-Type。
     */
    private static String resolveContentType(String fileName) {
        String contentType = URLConnection.guessContentTypeFromName(fileName);
        if (contentType != null) {
            return contentType;
        }
        String ext = getExtension(fileName);
        switch (ext) {
            case "xlsx":
                return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "xls":
                return "application/vnd.ms-excel";
            case "docx":
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "doc":
                return "application/msword";
            case "pdf":
                return MediaType.APPLICATION_PDF_VALUE;
            case "zip":
                return "application/zip";
            case "csv":
                return "text/csv";
            case "txt":
                return MediaType.TEXT_PLAIN_VALUE;
            case "json":
                return MediaType.APPLICATION_JSON_VALUE;
            case "xml":
                return MediaType.APPLICATION_XML_VALUE;
            case "png":
                return MediaType.IMAGE_PNG_VALUE;
            case "jpg":
            case "jpeg":
                return MediaType.IMAGE_JPEG_VALUE;
            case "gif":
                return MediaType.IMAGE_GIF_VALUE;
            default:
                return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
    }

    /**
     * 是否是文本类型（需要设置 charset）。
     */
    private static boolean isTextContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        return contentType.startsWith("text/")
                || contentType.startsWith("application/json")
                || contentType.startsWith("application/xml")
                || contentType.startsWith("application/javascript");
    }

    /**
     * 追加 Access-Control-Expose-Headers，不覆盖已有配置。
     */
    private static void appendExposeHeader(HttpServletResponse response, String headerName) {
        String existing = response.getHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS);
        if (existing == null || existing.isBlank()) {
            response.setHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, headerName);
            return;
        }
        for (String h : existing.split(",")) {
            if (h.trim().equalsIgnoreCase(headerName)) {
                return;
            }
        }
        response.setHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                existing + ", " + headerName);
    }

    /**
     * 统一解析文件名：优先 downloadName，其次 fallbackName，最后默认值。
     */
    private static String resolveFileName(String downloadName, String fallbackName) {
        if (downloadName != null && !downloadName.isBlank()) {
            return sanitize(downloadName);
        }
        if (fallbackName != null && !fallbackName.isBlank()) {
            return sanitize(fallbackName);
        }
        return DEFAULT_FILE_NAME;
    }

    /**
     * 去掉 header 中不安全字符，避免 CRLF 注入。
     */
    private static String sanitize(String fileName) {
        return fileName.replace("\r", "")
                .replace("\n", "")
                .replace("/", "_")
                .replace("\\", "_");
    }

    private static String getExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int index = fileName.lastIndexOf('.');
        return index == -1 ? "" : fileName.substring(index + 1).toLowerCase();
    }

    /* ==================== 专用异常 ==================== */

    /**
     * 下载相关异常，方便上层全局异常处理器统一识别。
     */
    public static class TemplateDownloadException extends RuntimeException {
        public TemplateDownloadException(String message) {
            super(message);
        }

        public TemplateDownloadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

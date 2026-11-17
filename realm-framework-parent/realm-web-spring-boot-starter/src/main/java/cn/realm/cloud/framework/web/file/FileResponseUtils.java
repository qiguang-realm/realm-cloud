package cn.realm.cloud.framework.web.file;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * 文件 HTTP 响应写出工具类。
 *
 * <p><b>职责：</b>将文件内容（{@link Resource}、classpath 资源、本地文件、
 * 字节数组、任意 {@link InputStream}）写入 {@link HttpServletResponse}，
 * 并按 RFC 6266 / RFC 5987 设置 {@code Content-Disposition}、
 * 按 Spring {@link MediaTypeFactory} 推断 {@code Content-Type}、
 * 在已知长度时设置 {@code Content-Length}。
 *
 * <p><b>为什么叫 {@code FileResponseUtils} 而非 {@code TemplateDownloadUtils}：</b>
 * 本类的能力远超"模板下载"——它支持 attachment 下载和 inline 预览两种模式，
 * 覆盖 {@link Resource}、classpath、本地文件、字节数组、流五类输入源。
 * {@code FileResponse} 精确表达"文件 + HTTP 响应"的双重语义，
 * {@code Utils} 后缀符合 Spring 官方对无状态静态工具类的惯例
 * （如 {@code StringUtils}、{@code FileCopyUtils}、{@code WebUtils}）。
 *
 * <p><b>主方法：</b>{@link #writeResource} 是核心入口——资源由调用方解析并完成
 * 合法性校验（如白名单），本类只负责响应写出，不关心资源来源。
 *
 * <p><b>前置条件：</b>调用前 {@code response} 必须满足：
 * <ul>
 *     <li>尚未调用 {@link HttpServletResponse#getWriter()}</li>
 *     <li>尚未调用 {@link HttpServletResponse#getOutputStream()}</li>
 *     <li>{@link HttpServletResponse#isCommitted()} 返回 {@code false}</li>
 * </ul>
 *
 * <p><b>流所有权：</b>所有接收 {@link InputStream} 的方法<b>不关闭</b>调用方传入的流，
 * 遵循"谁打开，谁关闭"原则（Effective Java Item 9）。
 *
 * <p><b>CORS 边界：</b>本类不设置 {@code Access-Control-Expose-Headers}。
 * 若前端需要通过 JavaScript 读取 {@code Content-Disposition} 中的文件名，
 * 应由全局 CORS 配置统一暴露该头，避免与工具类各自 {@code setHeader} 互相覆盖。
 *
 * <p><b>线程安全：</b>本类所有方法均为无状态设计，线程安全。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 推荐：先经业务 Service 校验白名单，再交给本类写出
 * Resource resource = templateWhitelistService.getTemplateResource("user_import_template.xlsx");
 * FileResponseUtils.writeResource(response, resource, "用户导入模板.xlsx", false);
 *
 * // 写出动态生成的字节数组
 * byte[] excel = generateExcel();
 * FileResponseUtils.writeBytes(response, excel, "导出结果.xlsx");
 *
 * // 写出流（调用方负责关闭）
 * try (InputStream in = new FileInputStream("/data/large.zip")) {
 *     FileResponseUtils.writeStream(response, in, "large.zip", fileSize);
 * }
 *
 * // 在线预览（浏览器内联打开）
 * FileResponseUtils.writeResource(response, pdfResource, "手册.pdf", true);
 * }</pre>
 *
 * @author QI Guang
 */
@Slf4j
public final class FileResponseUtils {

    /**
     * 默认下载文件名（无法从入参推断时使用）。
     */
    private static final String DEFAULT_FILE_NAME = "download";

    /**
     * 缓冲区大小 8KB。
     */
    private static final int BUFFER_SIZE = 8192;

    /**
     * ASCII fallback 中不允许的字符替换符。
     */
    private static final String ASCII_FALLBACK_REPLACEMENT = "_";

    private FileResponseUtils() {
        throw new UnsupportedOperationException(
                "FileResponseUtils is a static utility class and cannot be instantiated");
    }

    // ================================================================
    // 对外 API
    // ================================================================

    /**
     * 将已解析的 {@link Resource} 写入 HTTP 响应（下载或预览）。
     *
     * <p><b>这是本类的主入口：</b>资源由调用方解析并完成合法性校验
     * （例如白名单），本方法只负责响应写出，不关心资源来源。
     *
     * @param response     HTTP 响应，不允许为 {@code null}
     * @param resource     已解析的资源，调用方负责其合法性
     * @param downloadName 展示文件名；为 {@code null} 或空白时回退到资源文件名
     * @param inline       {@code true}=浏览器内联预览；{@code false}=作为附件下载
     * @throws FileResponseException 写出失败或响应已提交时抛出
     */
    public static void writeResource(HttpServletResponse response,
                                     Resource resource,
                                     String downloadName,
                                     boolean inline) {
        Objects.requireNonNull(response, "response must not be null");
        Objects.requireNonNull(resource, "resource must not be null");

        long contentLength = -1L;
        try {
            contentLength = resource.contentLength();
        } catch (IOException ignored) {
            // 无法获取长度时不设置 Content-Length，不中断下载
        }

        try (InputStream in = resource.getInputStream()) {
            write(response, in, resolveFileName(downloadName, resource.getFilename()),
                    inline, contentLength);
        } catch (IOException e) {
            throw new FileResponseException("Failed to write resource: " + downloadName, e);
        }
    }

    /**
     * 将 classpath 下的模板文件写入 HTTP 响应（作为附件下载）。
     *
     * <p><b>注意：</b>本方法<b>不校验路径白名单</b>，仅做存在性检查。
     * 若 {@code classpathPath} 可能来自用户输入或业务配置，调用方<b>必须</b>先完成
     * 白名单校验，否则可能读取 classpath 下任意资源（如 {@code application.yml}）。
     * 业务模板下载推荐走模块内 Service 校验后调用
     * {@link #writeResource(HttpServletResponse, Resource, String, boolean)}。
     *
     * @param response      HTTP 响应，不允许为 {@code null}
     * @param classpathPath classpath 路径，如 {@code "templates/import.xlsx"}
     * @param downloadName  下载后展示的文件名，如 {@code "用户导入模板.xlsx"}
     * @throws FileResponseException 模板不存在或读取失败时抛出
     */
    public static void writeClasspathTemplate(HttpServletResponse response,
                                              String classpathPath,
                                              String downloadName) {
        writeClasspathTemplate(response, classpathPath, downloadName, false);
    }

    /**
     * 在线预览 classpath 下的模板（浏览器能识别的类型会直接打开，如 PDF、图片）。
     *
     * <p><b>注意：</b>与 {@link #writeClasspathTemplate} 相同的白名单旁路风险，
     * 调用方必须自行保证 {@code classpathPath} 的合法性。
     *
     * @see #writeClasspathTemplate(HttpServletResponse, String, String)
     */
    public static void previewClasspathTemplate(HttpServletResponse response,
                                                String classpathPath,
                                                String fileName) {
        writeClasspathTemplate(response, classpathPath, fileName, true);
    }

    /**
     * 将本地磁盘文件写入 HTTP 响应（作为附件下载）。
     *
     * <p><b>安全警告（CWE-22）：</b>若 {@code filePath} 来自用户输入，
     * 调用方<b>必须</b>先做路径穿越校验（{@code normalize} + {@code startsWith}
     * 白名单目录），否则可能被读取任意系统文件。
     *
     * @param response     HTTP 响应，不允许为 {@code null}
     * @param filePath     绝对路径，如 {@code "/data/templates/demo.xlsx"}
     * @param downloadName 下载后展示的文件名
     * @throws FileResponseException 文件不存在或读取失败时抛出
     */
    public static void writeLocalFile(HttpServletResponse response,
                                      String filePath,
                                      String downloadName) {
        Objects.requireNonNull(response, "response must not be null");
        if (filePath == null || filePath.isBlank()) {
            throw new FileResponseException("filePath must not be blank");
        }
        Path path = Paths.get(filePath);
        File file = path.toFile();
        if (!file.exists() || !file.isFile()) {
            throw new FileResponseException("File not found: " + filePath);
        }
        try (InputStream in = Files.newInputStream(path)) {
            write(response, in, resolveFileName(downloadName, file.getName()),
                    false, file.length());
        } catch (IOException e) {
            throw new FileResponseException("Failed to read file: " + filePath, e);
        }
    }

    /**
     * 将内存中的字节数组写入 HTTP 响应（作为附件下载）。
     *
     * <p>允许空数组（{@code new byte[0]}），会生成 0 字节文件；
     * 若业务不允许空文件，调用方应在调用前显式校验。
     *
     * @param response     HTTP 响应，不允许为 {@code null}
     * @param data         字节数组，不允许为 {@code null}
     * @param downloadName 下载后展示的文件名
     * @throws FileResponseException 写出失败时抛出
     */
    public static void writeBytes(HttpServletResponse response,
                                  byte[] data,
                                  String downloadName) {
        Objects.requireNonNull(response, "response must not be null");
        Objects.requireNonNull(data, "data must not be null");
        try (InputStream in = new ByteArrayInputStream(data)) {
            write(response, in, resolveFileName(downloadName, null), false, data.length);
        } catch (IOException e) {
            throw new FileResponseException("Failed to write bytes: " + downloadName, e);
        }
    }

    /**
     * 将任意输入流写入 HTTP 响应（长度未知）。
     *
     * <p><b>流所有权：</b>本方法<b>不关闭</b> {@code in}，由调用方负责关闭。
     *
     * @see #writeStream(HttpServletResponse, InputStream, String, long)
     */
    public static void writeStream(HttpServletResponse response,
                                   InputStream in,
                                   String downloadName) {
        writeStream(response, in, downloadName, -1L);
    }

    /**
     * 将任意输入流写入 HTTP 响应，并显式指定内容长度（用于前端展示下载进度）。
     *
     * <p><b>流所有权：</b>本方法<b>不关闭</b> {@code in}，由调用方负责关闭。
     *
     * @param response      HTTP 响应，不允许为 {@code null}
     * @param in            输入流，不允许为 {@code null}
     * @param downloadName  下载后展示的文件名
     * @param contentLength 内容长度（字节）；未知时传 {@code -1}
     * @throws FileResponseException 写出失败或响应已提交时抛出
     */
    public static void writeStream(HttpServletResponse response,
                                   InputStream in,
                                   String downloadName,
                                   long contentLength) {
        Objects.requireNonNull(response, "response must not be null");
        Objects.requireNonNull(in, "in must not be null");
        try {
            write(response, in, resolveFileName(downloadName, null), false, contentLength);
        } catch (IOException e) {
            throw new FileResponseException("Failed to write stream: " + downloadName, e);
        }
    }

    // ================================================================
    // 内部实现
    // ================================================================

    /**
     * classpath 下载 / 预览的统一实现。
     */
    private static void writeClasspathTemplate(HttpServletResponse response,
                                               String classpathPath,
                                               String displayName,
                                               boolean inline) {
        Objects.requireNonNull(response, "response must not be null");
        Objects.requireNonNull(classpathPath, "classpathPath must not be null");
        ClassPathResource resource = new ClassPathResource(classpathPath);
        if (!resource.exists()) {
            throw new FileResponseException("Template not found: " + classpathPath);
        }
        writeResource(response, resource, displayName, inline);
    }

    /**
     * 将输入流写入 {@link HttpServletResponse}。
     *
     * @param response      HTTP 响应
     * @param in            输入流（本方法不关闭）
     * @param fileName      已 sanitize 的文件名
     * @param inline        {@code true}=浏览器内联预览；{@code false}=作为附件下载
     * @param contentLength 内容长度（字节）；未知时传 {@code -1}
     */
    private static void write(HttpServletResponse response,
                              InputStream in,
                              String fileName,
                              boolean inline,
                              long contentLength) throws IOException {

        if (response.isCommitted()) {
            throw new FileResponseException(
                    "Response already committed, cannot write file: " + fileName);
        }

        // 只清 body buffer，保留 headers / 状态码（全局 CORS 配置不受影响）
        response.resetBuffer();

        // Content-Type：只对文本类型设置字符集，避免二进制文件被附加 charset
        String contentType = resolveContentType(fileName);
        response.setContentType(contentType);
        if (isTextContentType(contentType)) {
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        }

        // Content-Disposition：RFC 6266 + RFC 5987 编码
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                buildContentDisposition(fileName, inline));

        // Content-Length：支持 long 值（如 4GB+ 大文件）
        if (contentLength >= 0) {
            response.setContentLengthLong(contentLength);
        }

        log.debug("开始写出文件, name: {}, inline: {}, size: {}", fileName, inline, contentLength);

        // 写出数据（不关闭 response.getOutputStream()——由容器管理）
        OutputStream out = response.getOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        int len;
        while ((len = in.read(buffer)) != -1) {
            out.write(buffer, 0, len);
        }
        out.flush();

        log.debug("文件写出完成, name: {}", fileName);
    }

    /**
     * 构建 Content-Disposition 头（RFC 6266 + RFC 5987）。
     *
     * <p>格式：{@code attachment; filename="ascii_fallback"; filename*=UTF-8''%E4%B8%AD%E6%96%87.xlsx}
     *
     * <p>RFC 5987 要求 {@code *} 编码为 {@code %2A}、{@code ~} 保留原样，
     * {@link URLEncoder} 的默认行为与规范不一致，需要显式修正。
     */
    private static String buildContentDisposition(String fileName, boolean inline) {
        String type = inline ? "inline" : "attachment";
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
        String asciiFallback = buildAsciiFallback(fileName);
        return String.format("%s; filename=\"%s\"; filename*=UTF-8''%s",
                type, asciiFallback, encoded);
    }

    /**
     * 生成 ASCII fallback 文件名（兼容不识别 {@code filename*} 的旧客户端）。
     *
     * <p>只保留可见 ASCII 字符，去掉会破坏 header 语法的 {@code "}、{@code \}、{@code ;}。
     */
    private static String buildAsciiFallback(String fileName) {
        StringBuilder sb = new StringBuilder(fileName.length());
        for (int i = 0; i < fileName.length(); i++) {
            char c = fileName.charAt(i);
            if (c >= 0x20 && c <= 0x7E && c != '"' && c != '\\' && c != ';') {
                sb.append(c);
            } else {
                sb.append(ASCII_FALLBACK_REPLACEMENT);
            }
        }
        return sb.isEmpty() ? DEFAULT_FILE_NAME : sb.toString();
    }

    /**
     * 根据文件名推断 Content-Type。
     *
     * <p>使用 Spring 的 {@link MediaTypeFactory}，内置 500+ 扩展名映射；
     * 无法识别时回退到 {@code application/octet-stream}。
     */
    private static String resolveContentType(String fileName) {
        return MediaTypeFactory.getMediaType(fileName)
                .map(MediaType::toString)
                .orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE);
    }

    /**
     * 判断是否为文本类型（需设置 charset）。
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
     * 解析文件名：优先 {@code downloadName}，其次 {@code fallbackName}，最后默认值。
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
     * 去掉 header 中不安全字符，避免 CRLF 注入（CWE-113）。
     */
    private static String sanitize(String fileName) {
        return fileName.replace("\r", "")
                .replace("\n", "")
                .replace("/", "_")
                .replace("\\", "_");
    }

    // ================================================================
    // 专用异常
    // ================================================================

    /**
     * 文件响应写出异常，供全局异常处理器统一识别。
     *
     * <p>替代原 {@code TemplateDownloadException}——后者把异常限定在"模板下载"语义，
     * 而本类覆盖任意文件资源的响应写出，异常名应同步泛化。
     */
    public static class FileResponseException extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        public FileResponseException(String message) {
            super(message);
        }

        public FileResponseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

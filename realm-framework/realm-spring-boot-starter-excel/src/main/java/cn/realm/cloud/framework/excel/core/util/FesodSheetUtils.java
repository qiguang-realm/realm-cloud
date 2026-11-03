package cn.realm.cloud.framework.excel.core.util;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.fesod.sheet.ExcelWriter;
import org.apache.fesod.sheet.FesodSheet;
import org.apache.fesod.sheet.context.AnalysisContext;
import org.apache.fesod.sheet.event.AnalysisEventListener;
import org.apache.fesod.sheet.read.listener.ReadListener;
import org.apache.fesod.sheet.write.metadata.WriteSheet;
import org.apache.fesod.sheet.write.metadata.fill.FillConfig;
import org.apache.fesod.sheet.write.style.column.LongestMatchColumnWidthStyleStrategy;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Apache Fesod (Incubating) 工具类，封装常见的 Excel 读写、多 Sheet、模板填充与 Web 导出能力。
 *
 * <p><b>流关闭约定：</b>传入的输入/输出流默认由本工具类负责关闭（Fesod 的 autoCloseStream 默认为 true）；
 * {@code HttpServletResponse#getOutputStream()} 例外，导出时显式设置 {@code autoCloseStream(false)}，
 * 交由容器管理。</p>
 *
 * <p><b>监听器生命周期：</b>通过 {@code ReadListener} 或 {@code Consumer} 传入的监听器/回调，
 * 每次读取都会创建新的监听器实例；调用方自行传入的 {@code ReadListener} 不应注册为 Spring 单例，
 * 因为监听器持有可变状态，并发读取时会导致数据串扰。</p>
 *
 * <p><b>安全边界：</b>本工具类仅负责 Excel 数据格式转换，<b>不包含</b>文件上传安全校验。
 * 调用 {@link #read(MultipartFile, Class)} 前，调用方必须自行完成 OWASP File Upload Cheat Sheet
 * 要求的校验：文件类型（基于魔数而非 Content-Type）、文件大小限制、文件名 sanitization、授权检查。
 * 工具类方法中的 {@code fileName} 参数仅用于生成下载文件名，不替代上传侧的文件名安全处理，
 * 也不构成对上传内容的任何信任背书。</p>
 *
 * @author QI Guang
 */
public final class FesodSheetUtils {

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private static final String DEFAULT_SHEET_NAME = "Sheet1";

    private static final String ASCII_FALLBACK_FILE_NAME = "export.xlsx";

    private FesodSheetUtils() {
    }

    // ==================== 读取 ====================

    /**
     * 读取第一个 Sheet 的全部数据（适用于小数据量）。
     */
    public static <T> List<T> read(InputStream in, Class<T> clazz) {
        return read(in, clazz, 0, 1);
    }

    /**
     * 按指定 Sheet 和表头行读取全部数据。
     *
     * @param sheetNo       从 0 开始的 Sheet 下标
     * @param headRowNumber 表头行数，无表头传 0
     */
    public static <T> List<T> read(InputStream in, Class<T> clazz, int sheetNo, int headRowNumber) {
        requireNonNull(in, "in");
        requireNonNull(clazz, "clazz");
        try {
            return FesodSheet.read(in)
                    .head(clazz)
                    .sheet(sheetNo)
                    .headRowNumber(headRowNumber)
                    .doReadSync();
        } catch (Exception e) {
            throw new ExcelException("读取 Excel 失败", e);
        }
    }

    /**
     * 从上传文件中读取（Web 场景便捷方法）。
     *
     * <p><b>安全提示：</b>本方法不做任何上传安全校验，调用方必须自行完成文件类型（魔数）、
     * 文件大小、文件名 sanitization 与授权检查，详见类注释中的“安全边界”。</p>
     */
    public static <T> List<T> read(MultipartFile file, Class<T> clazz) {
        requireNonNull(file, "file");
        try (InputStream in = file.getInputStream()) {
            return read(in, clazz);
        } catch (IOException e) {
            throw new ExcelException("读取上传文件失败", e);
        }
    }

    /**
     * 通过监听器读取，适合大数据量场景。
     *
     * <p>监听器应每次读取时新建，不要复用或注册为 Spring 单例。</p>
     */
    public static <T> void read(InputStream in, Class<T> clazz, ReadListener<T> listener) {
        requireNonNull(in, "in");
        requireNonNull(clazz, "clazz");
        requireNonNull(listener, "listener");
        try {
            FesodSheet.read(in, clazz, listener).sheet().doRead();
        } catch (Exception e) {
            throw new ExcelException("读取 Excel 失败", e);
        }
    }

    /**
     * 分批消费读取，每累积 {@code batchSize} 条回调一次，适合入库等批量处理。
     *
     * <p>每次回调传入的是独立的 {@code ArrayList} 副本，消费者可以安全地异步处理或长期持有。</p>
     */
    public static <T> void read(InputStream in, Class<T> clazz, int batchSize, Consumer<List<T>> consumer) {
        requireNonNull(in, "in");
        requireNonNull(clazz, "clazz");
        requireNonNull(consumer, "consumer");
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize 必须大于 0");
        }
        read(in, clazz, new AnalysisEventListener<T>() {
            private final List<T> cache = new ArrayList<>(batchSize);

            @Override
            public void invoke(T data, AnalysisContext context) {
                cache.add(data);
                if (cache.size() >= batchSize) {
                    flush();
                }
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
                flush();
            }

            private void flush() {
                if (!cache.isEmpty()) {
                    List<T> batch = new ArrayList<>(cache);
                    cache.clear();
                    consumer.accept(batch);
                }
            }
        });
    }

    // ==================== 写入 ====================

    /**
     * 写入默认 Sheet，并自动按内容匹配列宽。
     */
    public static <T> void write(OutputStream out, List<T> data, Class<T> clazz) {
        write(out, DEFAULT_SHEET_NAME, data, clazz, true);
    }

    /**
     * 写入指定名称的 Sheet。
     *
     * @param autoColumnWidth 是否启用最长内容列宽自适应；大数据量导出时建议关闭以降低逐单元格回调开销
     */
    public static <T> void write(OutputStream out, String sheetName, List<T> data,
                                 Class<T> clazz, boolean autoColumnWidth) {
        requireNonNull(out, "out");
        requireNonNull(clazz, "clazz");
        String actualSheetName = (sheetName == null || sheetName.isBlank()) ? DEFAULT_SHEET_NAME : sheetName;
        List<T> actualData = (data == null) ? Collections.emptyList() : data;
        try {
            var builder = FesodSheet.write(out, clazz);
            if (autoColumnWidth) {
                builder.registerWriteHandler(new LongestMatchColumnWidthStyleStrategy());
            }
            builder.sheet(actualSheetName).doWrite(actualData);
        } catch (Exception e) {
            throw new ExcelException("写入 Excel 失败", e);
        }
    }

    public static <T> void write(File file, List<T> data, Class<T> clazz) {
        requireNonNull(file, "file");
        try (OutputStream out = new FileOutputStream(file)) {
            write(out, data, clazz);
        } catch (IOException e) {
            throw new ExcelException("写入 Excel 文件失败", e);
        }
    }

    public static <T> void write(String path, List<T> data, Class<T> clazz) {
        requireNonNull(path, "path");
        write(new File(path), data, clazz);
    }

    /**
     * 写入多个 Sheet，每个 Sheet 可拥有独立的表头类型与数据。
     *
     * <p>所有 Sheet 共享列宽自适应策略，默认开启。</p>
     */
    public static void writeMultiSheet(OutputStream out, List<SheetData<?>> sheets) {
        writeMultiSheet(out, sheets, true);
    }

    /**
     * 写入多个 Sheet。
     *
     * @param autoColumnWidth 是否启用列宽自适应
     */
    public static void writeMultiSheet(OutputStream out, List<SheetData<?>> sheets, boolean autoColumnWidth) {
        requireNonNull(out, "out");
        if (sheets == null || sheets.isEmpty()) {
            throw new IllegalArgumentException("sheets 不能为空");
        }
        try (ExcelWriter writer = FesodSheet.write(out).build()) {
            for (int i = 0; i < sheets.size(); i++) {
                SheetData<?> sheetData = sheets.get(i);
                var sheetBuilder = FesodSheet.writerSheet(i, sheetData.getSheetName())
                        .head(sheetData.getClazz());
                if (autoColumnWidth) {
                    sheetBuilder.registerWriteHandler(new LongestMatchColumnWidthStyleStrategy());
                }
                WriteSheet sheet = sheetBuilder.build();
                List<?> data = (sheetData.getData() == null)
                        ? Collections.emptyList() : sheetData.getData();
                writer.write(data, sheet);
            }
        } catch (Exception e) {
            throw new ExcelException("写入多 Sheet Excel 失败", e);
        }
    }

    // ==================== 模板填充 ====================

    /**
     * 使用模板进行单对象填充。
     */
    public static void fill(InputStream templateIn, OutputStream out, Object data) {
        fill(templateIn, out, null, data);
    }

    /**
     * 使用模板填充，支持多次填充（如一个普通对象 + 多个列表）。
     * 填充列表时请使用 {@link org.apache.fesod.sheet.write.metadata.fill.FillWrapper} 包装，
     * 并通过 {@link FillConfig} 的 {@code forceNewRow(true)} 控制换行行为。
     */
    public static void fill(InputStream templateIn, OutputStream out, FillConfig fillConfig, Object... data) {
        requireNonNull(templateIn, "templateIn");
        requireNonNull(out, "out");
        if (data == null) {
            throw new IllegalArgumentException("data 不能为空");
        }
        try (ExcelWriter writer = FesodSheet.write(out).withTemplate(templateIn).build()) {
            WriteSheet sheet = FesodSheet.writerSheet().build();
            for (Object item : data) {
                if (fillConfig == null) {
                    writer.fill(item, sheet);
                } else {
                    writer.fill(item, fillConfig, sheet);
                }
            }
        } catch (Exception e) {
            throw new ExcelException("模板填充失败", e);
        }
    }

    // ==================== Web 导出 ====================

    /**
     * 导出到 HttpServletResponse，默认 Sheet 名为 {@link #DEFAULT_SHEET_NAME}。
     */
    public static <T> void export(HttpServletResponse response, String fileName, List<T> data, Class<T> clazz) {
        export(response, fileName, DEFAULT_SHEET_NAME, data, clazz);
    }

    /**
     * 导出到 HttpServletResponse。
     *
     * <p>显式设置 {@code autoCloseStream(false)}，不关闭 ServletOutputStream。</p>
     *
     * <p>注意：导出的数据量过大时应保证数据库查询本身是分批的；一旦流写入中途抛异常，
     * 客户端可能收到损坏的文件，这是流式导出的固有限制。如需更细粒度控制，
     * 可改用 {@link #exportStreaming}。</p>
     */
    public static <T> void export(HttpServletResponse response, String fileName, String sheetName,
                                  List<T> data, Class<T> clazz) {
        requireNonNull(response, "response");
        requireNonNull(clazz, "clazz");
        String actualSheetName = (sheetName == null || sheetName.isBlank()) ? DEFAULT_SHEET_NAME : sheetName;
        List<T> actualData = (data == null) ? Collections.emptyList() : data;
        try {
            prepareResponse(response, fileName);
            FesodSheet.write(response.getOutputStream(), clazz)
                    .autoCloseStream(false)
                    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                    .sheet(actualSheetName)
                    .doWrite(actualData);
        } catch (IOException e) {
            throw new ExcelException("导出 Excel 失败", e);
        }
    }

    /**
     * 流式导出：调用方通过 {@code writer} 自行分页写入，避免全量数据驻留内存。
     *
     * <pre>{@code
     * FesodSheetUtil.exportStreaming(response, "report", clazz, writer -> {
     *     WriteSheet sheet = FesodSheet.writerSheet("数据").head(clazz).build();
     *     int page = 0;
     *     List<Foo> batch;
     *     while (!(batch = queryPage(page++)).isEmpty()) {
     *         writer.write(batch, sheet);
     *     }
     * });
     * }</pre>
     */
    public static <T> void exportStreaming(HttpServletResponse response, String fileName, Class<T> clazz,
                                           Consumer<ExcelWriter> writerConsumer) {
        requireNonNull(response, "response");
        requireNonNull(clazz, "clazz");
        requireNonNull(writerConsumer, "writerConsumer");
        try {
            prepareResponse(response, fileName);
            try (ExcelWriter writer = FesodSheet.write(response.getOutputStream(), clazz)
                    .autoCloseStream(false)
                    .build()) {
                writerConsumer.accept(writer);
            }
        } catch (IOException e) {
            throw new ExcelException("流式导出 Excel 失败", e);
        }
    }

    /**
     * 构造下载响应头。
     *
     * <p>遵循 RFC 6266 / RFC 5987：{@code filename} 提供纯 ASCII 回退名，
     * {@code filename*} 承载 UTF-8 百分号编码的完整文件名。避免在 {@code filename}
     * 中出现百分号转义序列，因为不同浏览器（Firefox/Chrome 会解码、Safari 不会）处理不一致。</p>
     */
    private static void prepareResponse(HttpServletResponse response, String fileName) {
        String fullName = normalizeFileName(fileName);
        String asciiName = toAsciiFallbackName(fullName);
        String encoded = URLEncoder.encode(fullName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType(XLSX_CONTENT_TYPE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + asciiName + "\"; filename*=UTF-8''" + encoded);
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
    }

    /**
     * 统一规范化为 .xlsx。传入 .xls 时替换扩展名，避免文件名、内容格式与 Content-Type 不一致。
     */
    private static String normalizeFileName(String fileName) {
        String name = (fileName == null || fileName.isBlank()) ? "export" : fileName.trim();
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".xlsx")) {
            return name;
        }
        if (lower.endsWith(".xls")) {
            return name.substring(0, name.length() - 4) + ".xlsx";
        }
        return name + ".xlsx";
    }

    /**
     * 生成 Content-Disposition 中 {@code filename} 参数使用的 ASCII 回退名。
     *
     * <p>当 {@code fullName} 全部为可打印 ASCII 且不含双引号、反斜杠、正斜杠时直接使用原名称，
     * 否则回退为 {@link #ASCII_FALLBACK_FILE_NAME}，避免在 {@code filename} 中输出
     * 百分号转义序列或引号导致不同浏览器解析不一致。</p>
     */
    private static String toAsciiFallbackName(String fullName) {
        boolean asciiSafe = fullName.chars().allMatch(c ->
                c >= 0x20 && c < 0x7F && c != '"' && c != '\\' && c != '/');
        return asciiSafe ? fullName : ASCII_FALLBACK_FILE_NAME;
    }

    private static void requireNonNull(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
    }

    // ==================== 辅助类型 ====================

    /**
     * 多 Sheet 写入的数据载体。
     */
    public static final class SheetData<T> {

        private final String sheetName;
        private final Class<T> clazz;
        private final List<T> data;

        public SheetData(String sheetName, Class<T> clazz, List<T> data) {
            this.sheetName = sheetName;
            this.clazz = clazz;
            this.data = data;
        }

        public static <T> SheetData<T> of(String sheetName, Class<T> clazz, List<T> data) {
            return new SheetData<>(sheetName, clazz, data);
        }

        public String getSheetName() {
            return sheetName;
        }

        public Class<T> getClazz() {
            return clazz;
        }

        public List<T> getData() {
            return data;
        }
    }

    /**
     * Excel 操作统一异常。
     */
    public static class ExcelException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public ExcelException(String message) {
            super(message);
        }

        public ExcelException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

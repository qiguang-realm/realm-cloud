package cn.realm.cloud.framework.common.util.file;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * PDF 统一工具类（基于 PDFBox 3.x）。
 *
 * <p><b>能力分区：</b>
 * <ol>
 *     <li><b>文档操作</b>：加载、保存、页数、加密判断、文本提取、合并、转图片</li>
 *     <li><b>字体管理</b>：从 classpath 加载中文字体（常规 / 粗体），带 LRU 缓存</li>
 *     <li><b>业务排版辅助</b>：金额格式化、文本宽度计算、列宽调整</li>
 *     <li><b>表格绘制</b>：基础表格、增强表格（换行 / 对齐 / 合并单元格）、分页绘制</li>
 *     <li><b>创建简单 PDF</b>：纯文本 / 中文文本快速创建</li>
 *     <li><b>Web 输出</b>：将 PDF 写入 HTTP 响应（RFC 5987 编码）</li>
 * </ol>
 *
 * <p><b>资源所有权：</b>
 * <ul>
 *     <li>返回 {@link PDDocument} 的方法由调用方负责关闭（推荐 try-with-resources）</li>
 *     <li>接收 {@link InputStream} 的方法<b>不关闭</b>传入流（"谁打开，谁关闭"）</li>
 * </ul>
 *
 * <p><b>字体缓存：</b>缓存字体<b>字节数据</b>（{@code byte[]}），而非 {@link PDType0Font}——
 * 后者与加载它的 {@link PDDocument} 强绑定，跨文档使用会抛异常。缓存使用 Guava Cache
 * 限制容量（{@value #FONT_CACHE_MAX_SIZE} 个）与访问过期（24 小时），防止 OOM（CWE-770）。
 *
 * <p><b>字体加载：</b>使用 {@link Thread#getContextClassLoader()} 而非
 * {@code PdfUtils.class.getClassLoader()}——前者兼容字体位于业务模块 classpath 的场景，
 * 后者仅能加载框架模块内的字体。
 *
 * <p><b>线程安全：</b>
 * <ul>
 *     <li>本类所有静态方法均线程安全</li>
 *     <li>{@link PDDocument} 实例本身<b>非线程安全</b>，不可跨线程共享</li>
 *     <li>字体缓存与表格绘制方法均为无状态，线程安全</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 1. 文档合并
 * PdfUtils.merge(List.of(file1, file2), Path.of("/data/merged.pdf"));
 *
 * // 2. 中文 PDF 创建
 * try (PDDocument doc = new PDDocument()) {
 *     PDType0Font font = PdfUtils.loadFont(doc, "/fonts/msyh.ttf");
 *     // 绘制表格...
 * }
 *
 * // 3. 快速创建简单 PDF
 * PdfUtils.createSimplePdf("Hello World", new File("/data/hello.pdf"));
 * PdfUtils.createSimplePdfWithChinese("你好，世界", new File("/data/hello-zh.pdf"), "/fonts/msyh.ttf");
 *
 * // 4. Web 下载
 * PdfUtils.writePdfResponse(response, pdfBytes, "报告.pdf");
 * }</pre>
 *
 * @author QI Guang
 * @since 1.0.0
 */
public final class PdfUtils {

    private static final Logger log = LoggerFactory.getLogger(PdfUtils.class);

    /**
     * 字体缓存最大容量（防止多个大字体文件导致 OOM）。
     */
    private static final int FONT_CACHE_MAX_SIZE = 20;

    /**
     * 字体缓存过期时间（避免长期不用的字体占用内存）。
     */
    private static final Duration FONT_CACHE_EXPIRE = Duration.ofHours(24);

    /**
     * US 区域的十进制格式化符号（不可变，可安全共享）。
     * <p>特性：千分位分隔符为 {@code ,}，小数点为 {@code .}。
     */
    private static final DecimalFormatSymbols US_DECIMAL_SYMBOLS =
            DecimalFormatSymbols.getInstance(Locale.US);

    /**
     * 金额格式化模式：保留两位小数、千分位分隔。
     */
    private static final String AMOUNT_PATTERN = "#,##0.00";

    /**
     * 常规字体字节缓存（LRU + 24 小时过期）。
     */
    private static final Cache<String, byte[]> FONT_DATA_CACHE = CacheBuilder.newBuilder()
            .maximumSize(FONT_CACHE_MAX_SIZE)
            .expireAfterAccess(FONT_CACHE_EXPIRE)
            .build();

    /**
     * 粗体字体字节缓存（独立缓存）。
     */
    private static final Cache<String, byte[]> FONT_BOLD_DATA_CACHE = CacheBuilder.newBuilder()
            .maximumSize(FONT_CACHE_MAX_SIZE)
            .expireAfterAccess(FONT_CACHE_EXPIRE)
            .build();

    private PdfUtils() {
        throw new UnsupportedOperationException("PdfUtils 为静态工具类，禁止实例化");
    }

    // ================================================================
    // 第一部分：文档操作（加载 / 保存 / 信息 / 文本 / 合并 / 转图）
    // ================================================================

    /**
     * 从文件加载 PDF。
     *
     * @param file PDF 文件，不允许为 {@code null}
     * @return {@link PDDocument}，调用方负责关闭
     */
    public static PDDocument load(File file) throws IOException {
        Objects.requireNonNull(file, "file 不能为 null");
        if (log.isDebugEnabled()) {
            log.debug("[load] 加载 PDF 文件：{}", file.getAbsolutePath());
        }
        return Loader.loadPDF(file);
    }

    /**
     * 从字节数组加载 PDF。
     */
    public static PDDocument load(byte[] bytes) throws IOException {
        Objects.requireNonNull(bytes, "bytes 不能为 null");
        return Loader.loadPDF(bytes);
    }

    /**
     * 从字节数组加载 PDF（使用 {@link RandomAccessReadBuffer}）。
     *
     * <p>与 {@link #load(byte[])} 的区别：使用更底层的流式读取，对大字节数组可减少内存拷贝。
     */
    public static PDDocument loadFromBytes(byte[] bytes) throws IOException {
        Objects.requireNonNull(bytes, "bytes 不能为 null");
        return Loader.loadPDF(new RandomAccessReadBuffer(bytes));
    }

    /**
     * 将 {@link PDDocument} 保存为字节数组。
     */
    public static byte[] toByteArray(PDDocument document) throws IOException {
        Objects.requireNonNull(document, "document 不能为 null");
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            document.save(baos);
            return baos.toByteArray();
        }
    }

    /**
     * 获取页数。
     */
    public static int getPageCount(PDDocument document) {
        Objects.requireNonNull(document, "document 不能为 null");
        return document.getPages().getCount();
    }

    /**
     * 判断是否已加密。
     */
    public static boolean isEncrypted(PDDocument document) {
        Objects.requireNonNull(document, "document 不能为 null");
        return document.isEncrypted();
    }

    /**
     * 安全关闭 {@link PDDocument}（记录 WARN 日志，不抛异常）。
     *
     * <p>适用于 {@code finally} 块，避免关闭异常影响主流程。
     *
     * @param document 文档实例，允许为 {@code null}
     */
    public static void closeQuietly(PDDocument document) {
        if (document == null) {
            return;
        }
        try {
            document.close();
        } catch (IOException e) {
            log.warn("[closeQuietly] 关闭 PDDocument 失败", e);
        }
    }

    // ---------- 文本提取 ----------

    /**
     * 提取全文文本。
     */
    public static String extractText(PDDocument document) throws IOException {
        Objects.requireNonNull(document, "document 不能为 null");
        return new PDFTextStripper().getText(document);
    }

    /**
     * 提取指定页范围文本（页码从 1 开始）。
     *
     * @param startPage 起始页（≥1）
     * @param endPage   结束页（≥startPage，≤总页数）
     */
    public static String extractText(PDDocument document, int startPage, int endPage)
            throws IOException {
        Objects.requireNonNull(document, "document 不能为 null");
        int pageCount = document.getPages().getCount();
        if (startPage < 1 || endPage > pageCount || startPage > endPage) {
            throw new IllegalArgumentException(
                    "页码范围非法 [" + startPage + ", " + endPage + "]，总页数：" + pageCount);
        }
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(startPage);
        stripper.setEndPage(endPage);
        return stripper.getText(document);
    }

    // ---------- 合并 ----------

    /**
     * 合并多个 PDF 文件。
     *
     * @param sources    源文件列表，不允许为空
     * @param outputFile 输出路径，不允许为 {@code null}
     */
    public static void merge(List<File> sources, Path outputFile) throws IOException {
        Objects.requireNonNull(sources, "sources 不能为 null");
        Objects.requireNonNull(outputFile, "outputFile 不能为 null");
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("sources 不能为空");
        }
        if (log.isInfoEnabled()) {
            log.info("[merge] 开始合并 PDF，共 {} 个文件，输出：{}", sources.size(), outputFile);
        }
        try (PDDocument destination = new PDDocument();
             FileOutputStream out = new FileOutputStream(outputFile.toFile())) {
            PDFMergerUtility merger = new PDFMergerUtility();
            merger.setDestinationStream(out);
            for (File source : sources) {
                try (PDDocument src = Loader.loadPDF(source)) {
                    merger.appendDocument(destination, src);
                }
            }
            destination.save(out);
        }
    }

    /**
     * 合并多个 PDF 字节数组。
     */
    public static void mergeFromBytes(List<byte[]> sources, Path outputFile) throws IOException {
        Objects.requireNonNull(sources, "sources 不能为 null");
        Objects.requireNonNull(outputFile, "outputFile 不能为 null");
        try (PDDocument destination = new PDDocument();
             FileOutputStream out = new FileOutputStream(outputFile.toFile())) {
            PDFMergerUtility merger = new PDFMergerUtility();
            merger.setDestinationStream(out);
            for (byte[] bytes : sources) {
                try (PDDocument src = Loader.loadPDF(new RandomAccessReadBuffer(bytes))) {
                    merger.appendDocument(destination, src);
                }
            }
            destination.save(out);
        }
    }

    // ---------- 转图片 ----------

    /**
     * 将 PDF 所有页面转换为图片。
     *
     * @param dpi 分辨率，必须为正整数（推荐 150 / 200 / 300）
     */
    public static List<BufferedImage> toImages(PDDocument document, int dpi) throws IOException {
        Objects.requireNonNull(document, "document 不能为 null");
        if (dpi <= 0) {
            throw new IllegalArgumentException("dpi 必须为正数，当前值：" + dpi);
        }
        PDFRenderer renderer = new PDFRenderer(document);
        int pageCount = document.getPages().getCount();
        List<BufferedImage> images = new ArrayList<>(pageCount);
        for (int i = 0; i < pageCount; i++) {
            images.add(renderer.renderImageWithDPI(i, dpi));
        }
        return images;
    }

    /**
     * 将 PDF 指定页转换为图片。
     *
     * @param pageIndex 页码索引（从 0 开始）
     */
    public static BufferedImage toImage(PDDocument document, int pageIndex, int dpi)
            throws IOException {
        Objects.requireNonNull(document, "document 不能为 null");
        int pageCount = document.getPages().getCount();
        if (pageIndex < 0 || pageIndex >= pageCount) {
            throw new IllegalArgumentException(
                    "页码索引越界 [0, " + pageCount + ")，当前值：" + pageIndex);
        }
        if (dpi <= 0) {
            throw new IllegalArgumentException("dpi 必须为正数，当前值：" + dpi);
        }
        return new PDFRenderer(document).renderImageWithDPI(pageIndex, dpi);
    }

    // ---------- 创建简单 PDF ----------

    /**
     * 创建包含简单文本的 PDF（使用标准 Helvetica 字体，不支持中文）。
     *
     * <p>适用于纯英文 / 数字内容的快速创建。
     *
     * @param content    文本内容，允许为 {@code null}（视为空串）
     * @param outputFile 输出文件，不允许为 {@code null}
     * @see #createSimplePdfWithChinese(String, File, String)
     */
    public static void createSimplePdf(String content, File outputFile) throws IOException {
        Objects.requireNonNull(outputFile, "outputFile 不能为 null");
        if (log.isInfoEnabled()) {
            log.info("[createSimplePdf] 创建简单 PDF：{}", outputFile.getAbsolutePath());
        }
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 750);
                cs.showText(content != null ? content : "");
                cs.endText();
            }
            document.save(outputFile);
        }
    }

    /**
     * 创建包含中文文本的 PDF（使用外部中文字体）。
     *
     * <p>字体文件需位于 classpath（推荐放在 {@code src/main/resources/static/fonts/}）。
     *
     * @param content    文本内容，允许为 {@code null}（视为空串）
     * @param outputFile 输出文件，不允许为 {@code null}
     * @param fontPath   中文字体在 classpath 下的路径，如 {@code "/static/fonts/msyh.ttf"}
     * @see #loadFont(PDDocument, String)
     */
    public static void createSimplePdfWithChinese(String content, File outputFile, String fontPath)
            throws IOException {
        Objects.requireNonNull(outputFile, "outputFile 不能为 null");
        Objects.requireNonNull(fontPath, "fontPath 不能为 null");
        if (log.isInfoEnabled()) {
            log.info("[createSimplePdfWithChinese] 创建中文 PDF：{}，字体：{}",
                    outputFile.getAbsolutePath(), fontPath);
        }
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDType0Font font = loadFont(document, fontPath);
            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                cs.beginText();
                cs.setFont(font, 12);
                cs.newLineAtOffset(50, 750);
                cs.showText(content != null ? content : "");
                cs.endText();
            }
            document.save(outputFile);
        }
    }

    // ================================================================
    // 第二部分：字体管理
    // ================================================================

    /**
     * 从 classpath 加载常规字体（绑定到当前文档）。
     *
     * @param document 当前 PDF 文档，不允许为 {@code null}
     * @param fontPath classpath 路径，如 {@code "/static/fonts/msyh.ttf"}
     * @return 已绑定到文档的 {@link PDType0Font}
     */
    public static PDType0Font loadFont(PDDocument document, String fontPath) throws IOException {
        return loadFontInternal(document, fontPath, FONT_DATA_CACHE, "常规");
    }

    /**
     * 加载粗体字体。
     */
    public static PDType0Font loadFontBold(PDDocument document, String fontBoldPath)
            throws IOException {
        return loadFontInternal(document, fontBoldPath, FONT_BOLD_DATA_CACHE, "粗体");
    }

    /**
     * 清除所有字体缓存。
     */
    public static void clearFontCache() {
        FONT_DATA_CACHE.invalidateAll();
        FONT_BOLD_DATA_CACHE.invalidateAll();
        log.info("[clearFontCache] 字体缓存已清空");
    }

    /**
     * 判断常规字体是否已缓存。
     */
    public static boolean isFontCached(String fontPath) {
        return FONT_DATA_CACHE.getIfPresent(fontPath) != null;
    }

    /**
     * 判断粗体字体是否已缓存。
     */
    public static boolean isFontBoldCached(String fontBoldPath) {
        return FONT_BOLD_DATA_CACHE.getIfPresent(fontBoldPath) != null;
    }

    /**
     * 获取 PDFBox 标准 14 字体之一（仅支持英文/数字）。
     */
    public static PDType1Font getStandardFont(Standard14Fonts.FontName fontName) {
        Objects.requireNonNull(fontName, "fontName 不能为 null");
        return new PDType1Font(fontName);
    }

    /**
     * 字体加载核心逻辑（常规 / 粗体共用）。
     */
    private static PDType0Font loadFontInternal(PDDocument document, String fontPath,
                                                Cache<String, byte[]> cache, String kind)
            throws IOException {
        Objects.requireNonNull(document, "document 不能为 null");
        Objects.requireNonNull(fontPath, "fontPath 不能为 null");

        byte[] fontData = cache.getIfPresent(fontPath);
        if (fontData == null) {
            fontData = readFontBytes(fontPath);
            cache.put(fontPath, fontData);
            if (log.isInfoEnabled()) {
                log.info("[loadFont] 已缓存{}字体，路径：{}，大小：{} 字节",
                        kind, fontPath, fontData.length);
            }
        }

        try (ByteArrayInputStream bais = new ByteArrayInputStream(fontData)) {
            return PDType0Font.load(document, bais, true);
        } catch (IOException e) {
            // 缓存数据可能损坏，清除后重新抛出
            cache.invalidate(fontPath);
            throw new IOException("从缓存加载字体失败：" + fontPath, e);
        }
    }

    /**
     * 从 classpath 读取字体字节。
     *
     * <p><b>类加载器选择：</b>使用 {@link Thread#getContextClassLoader()} 而非
     * {@code PdfUtils.class.getClassLoader()}，以兼容以下场景：
     * <ul>
     *     <li>字体文件位于业务应用的 classpath（而非框架模块 JAR）</li>
     *     <li>Web 容器（Tomcat、Undertow）中框架与业务使用不同类加载器</li>
     * </ul>
     * 若 ContextClassLoader 为 {@code null}（罕见，如纯 Java SE 环境），回退到本类的类加载器。
     */
    private static byte[] readFontBytes(String fontPath) throws IOException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = PdfUtils.class.getClassLoader();
        }
        try (InputStream is = cl.getResourceAsStream(fontPath)) {
            if (is == null) {
                throw new IOException("classpath 中未找到字体文件：" + fontPath);
            }
            byte[] data = is.readAllBytes();
            if (data.length == 0) {
                throw new IOException("字体文件为空：" + fontPath);
            }
            return data;
        }
    }

    // ================================================================
    // 第三部分：业务排版辅助（格式化 / 宽度 / 列宽）
    // ================================================================

    /**
     * 格式化金额：保留两位小数，添加千分位分隔符（US 格式）。
     *
     * <p><b>性能说明：</b>每次调用创建 {@link DecimalFormat}（微秒级），
     * 避免 {@code ThreadLocal} 在长生命周期线程中导致内存泄漏（CWE-401）。
     * {@link DecimalFormatSymbols} 已提取为静态常量，节省每次的 {@link Locale} 查找。
     *
     * @param amount 金额；{@code null} 时返回 {@code "0.00"}
     */
    public static String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }
        return new DecimalFormat(AMOUNT_PATTERN, US_DECIMAL_SYMBOLS).format(amount);
    }

    /**
     * 格式化整数金额：保留两位小数。
     */
    public static String formatAmount(Integer value) {
        if (value == null) {
            return "0.00";
        }
        return new DecimalFormat(AMOUNT_PATTERN, US_DECIMAL_SYMBOLS).format(value);
    }

    /**
     * 安全获取对象字符串。
     */
    public static String safeString(Object obj) {
        return obj == null ? "" : obj.toString();
    }

    /**
     * 计算文本宽度（单位：点）。
     */
    public static float getStringWidth(PDType0Font font, String text, float fontSize)
            throws IOException {
        Objects.requireNonNull(font, "font 不能为 null");
        if (text == null || text.isEmpty()) {
            return 0f;
        }
        return font.getStringWidth(text) / 1000f * fontSize;
    }

    /**
     * 计算列宽数组的总宽度。
     */
    public static float getTotalWidth(float[] colWidths) {
        if (colWidths == null) {
            return 0f;
        }
        float sum = 0;
        for (float w : colWidths) {
            sum += w;
        }
        return sum;
    }

    /**
     * 按比例调整列宽以适应目标总宽度。
     *
     * <p><b>注意：</b>传入的数组会被<b>原地修改</b>。
     */
    public static void scaleColWidths(float[] colWidths, float targetWidth) {
        if (colWidths == null || colWidths.length == 0 || targetWidth <= 0) {
            return;
        }
        float total = getTotalWidth(colWidths);
        if (total == 0) {
            return;
        }
        float scale = targetWidth / total;
        for (int i = 0; i < colWidths.length; i++) {
            colWidths[i] *= scale;
        }
    }

    // ---------- 文本绘制 ----------

    /**
     * 绘制单行文本。
     *
     * @return 下一行的 Y 坐标（{@code y - rowHeight}）
     */
    public static float drawTextLine(PDPageContentStream cs, PDType0Font font, String text,
                                     float x, float y, float fontSize, float rowHeight)
            throws IOException {
        cs.setFont(font, fontSize);
        cs.beginText();
        cs.newLineAtOffset(x, y);
        cs.showText(text != null ? text : "");
        cs.endText();
        return y - rowHeight;
    }

    /**
     * 绘制标题。
     *
     * @return 下一行的 Y 坐标（{@code y - marginBottom}）
     */
    public static float drawTitle(PDPageContentStream cs, PDType0Font font, String title,
                                  float x, float y, float fontSize, float marginBottom)
            throws IOException {
        cs.setFont(font, fontSize);
        cs.beginText();
        cs.newLineAtOffset(x, y);
        cs.showText(title != null ? title : "");
        cs.endText();
        return y - marginBottom;
    }

    // ================================================================
    // 第四部分：表格绘制（基础 / 增强 / 分页）
    // ================================================================

    /**
     * 表格行映射器：将数据对象转换为字符串数组。
     */
    @FunctionalInterface
    public interface RowMapper {
        /**
         * 映射一行数据。
         *
         * @param rowIdx 行号（从 1 开始，可用于生成序号列）
         * @param item   数据对象
         * @return 字符串数组，长度应与列数一致
         */
        String[] map(int rowIdx, Object item);
    }

    /**
     * 水平对齐方式。
     */
    public enum TextAlignment {
        LEFT, CENTER, RIGHT
    }

    /**
     * 垂直对齐方式。
     */
    public enum VerticalAlignment {
        TOP, MIDDLE, BOTTOM
    }

    /**
     * 合并单元格配置（不可变）。
     *
     * @param row     起始行索引（0 基，表头为 0，数据行从 1 开始）
     * @param col     起始列索引（0 基）
     * @param rowSpan 合并行数（≥1）
     * @param colSpan 合并列数（≥1）
     * @param text    显示文本
     */
    public record MergeCell(int row, int col, int rowSpan, int colSpan, String text) {
        public MergeCell {
            if (row < 0 || col < 0 || rowSpan < 1 || colSpan < 1) {
                throw new IllegalArgumentException(
                        "行、列索引必须 >= 0，行/列合并数必须 >= 1");
            }
        }
    }

    /**
     * 表格样式配置（不可变）。
     *
     * <p>通过 {@link Builder} 创建：{@code TableStyle.builder().fontSize(12).build()}
     */
    public record TableStyle(
            float fontSize,
            float rowHeight,
            float padding,
            float borderWidth,
            TextAlignment headerHAlign,
            VerticalAlignment headerVAlign,
            boolean headerBackground,
            float headerBgR, float headerBgG, float headerBgB,
            TextAlignment dataHAlign,
            VerticalAlignment dataVAlign,
            float dataTextR, float dataTextG, float dataTextB,
            boolean wrapText,
            float borderR, float borderG, float borderB
    ) {

        public static TableStyle defaultStyle() {
            return builder().build();
        }

        public static TableStyle reportStyle() {
            return builder().padding(4f).build();
        }

        public static Builder builder() {
            return new Builder();
        }

        /**
         * TableStyle 构建器（链式调用）。
         */
        public static final class Builder {
            private float fontSize = 10f;
            private float rowHeight = 22f;
            private float padding = 3f;
            private float borderWidth = 1f;
            private TextAlignment headerHAlign = TextAlignment.CENTER;
            private VerticalAlignment headerVAlign = VerticalAlignment.MIDDLE;
            private boolean headerBackground = true;
            private float headerBgR = 0.92f, headerBgG = 0.92f, headerBgB = 0.92f;
            private TextAlignment dataHAlign = TextAlignment.CENTER;
            private VerticalAlignment dataVAlign = VerticalAlignment.MIDDLE;
            private float dataTextR = 0f, dataTextG = 0f, dataTextB = 0f;
            private boolean wrapText = true;
            private float borderR = 0f, borderG = 0f, borderB = 0f;

            public Builder fontSize(float v) {
                this.fontSize = v;
                return this;
            }

            public Builder rowHeight(float v) {
                this.rowHeight = v;
                return this;
            }

            public Builder padding(float v) {
                this.padding = v;
                return this;
            }

            public Builder borderWidth(float v) {
                this.borderWidth = v;
                return this;
            }

            public Builder headerHAlign(TextAlignment v) {
                this.headerHAlign = v;
                return this;
            }

            public Builder headerVAlign(VerticalAlignment v) {
                this.headerVAlign = v;
                return this;
            }

            public Builder headerBackground(boolean v) {
                this.headerBackground = v;
                return this;
            }

            public Builder headerBgColor(float r, float g, float b) {
                this.headerBgR = r;
                this.headerBgG = g;
                this.headerBgB = b;
                return this;
            }

            public Builder dataHAlign(TextAlignment v) {
                this.dataHAlign = v;
                return this;
            }

            public Builder dataVAlign(VerticalAlignment v) {
                this.dataVAlign = v;
                return this;
            }

            public Builder dataTextColor(float r, float g, float b) {
                this.dataTextR = r;
                this.dataTextG = g;
                this.dataTextB = b;
                return this;
            }

            public Builder wrapText(boolean v) {
                this.wrapText = v;
                return this;
            }

            public Builder borderColor(float r, float g, float b) {
                this.borderR = r;
                this.borderG = g;
                this.borderB = b;
                return this;
            }

            public TableStyle build() {
                return new TableStyle(
                        fontSize, rowHeight, padding, borderWidth,
                        headerHAlign, headerVAlign, headerBackground,
                        headerBgR, headerBgG, headerBgB,
                        dataHAlign, dataVAlign,
                        dataTextR, dataTextG, dataTextB,
                        wrapText, borderR, borderG, borderB);
            }
        }
    }

    // ---------- 基础表格绘制 ----------

    /**
     * 绘制基础表格（无背景色、边框宽度 1.0、无合并）。
     */
    public static float drawTable(PDPageContentStream cs, PDType0Font font,
                                  String[] headers, float[] colWidths,
                                  List<?> data, float x, float y,
                                  RowMapper mapper, float rowHeight, float fontSize)
            throws IOException {
        return drawTable(cs, font, headers, colWidths, data, x, y, mapper,
                rowHeight, fontSize, false, 1.0f, null);
    }

    /**
     * 绘制基础表格（支持表头背景色）。
     */
    public static float drawTable(PDPageContentStream cs, PDType0Font font,
                                  String[] headers, float[] colWidths,
                                  List<?> data, float x, float y,
                                  RowMapper mapper, float rowHeight, float fontSize,
                                  boolean headerBackground) throws IOException {
        return drawTable(cs, font, headers, colWidths, data, x, y, mapper,
                rowHeight, fontSize, headerBackground, 1.0f, null);
    }

    /**
     * 绘制基础表格（完整版：支持边框宽度、合并单元格）。
     */
    public static float drawTable(PDPageContentStream cs, PDType0Font font,
                                  String[] headers, float[] colWidths,
                                  List<?> data, float x, float y,
                                  RowMapper mapper, float rowHeight, float fontSize,
                                  boolean headerBackground, float borderWidth,
                                  List<MergeCell> merges) throws IOException {
        if (headers == null || headers.length == 0
                || colWidths == null || colWidths.length == 0) {
            log.warn("[drawTable] 表头或列宽为空，跳过绘制");
            return y;
        }
        if (headers.length != colWidths.length) {
            throw new IllegalArgumentException(
                    "表头数量与列宽数量不一致：" + headers.length + " vs " + colWidths.length);
        }

        int colCount = headers.length;
        float currentY = y;
        final float lightGray = 0.92f;

        cs.setLineWidth(borderWidth);

        // ---- 表头 ----
        float currentX = x;
        for (int col = 0; col < colCount; col++) {
            if (isCellMerged(0, col, merges)) {
                continue;
            }
            MergeCell merge = getMergeCell(0, col, merges);
            if (merge != null) {
                float mergeWidth = sumColWidths(colWidths, col, merge.colSpan(), colCount);
                float mergeHeight = rowHeight * merge.rowSpan();
                drawCellRect(cs, currentX, currentY, mergeWidth, mergeHeight,
                        headerBackground, lightGray);
                drawCenteredText(cs, font, merge.text(), currentX, currentY,
                        mergeWidth, mergeHeight, fontSize);
                currentX += mergeWidth;
                col += merge.colSpan() - 1;
                continue;
            }
            drawCellRect(cs, currentX, currentY, colWidths[col], rowHeight,
                    headerBackground, lightGray);
            drawCenteredText(cs, font, headers[col], currentX, currentY,
                    colWidths[col], rowHeight, fontSize);
            currentX += colWidths[col];
        }
        currentY -= rowHeight;

        // ---- 数据行 ----
        if (data != null && !data.isEmpty()) {
            int rowIdx = 1;
            for (Object item : data) {
                String[] rowData = mapper.map(rowIdx, item);
                currentX = x;
                for (int col = 0; col < Math.min(rowData.length, colCount); col++) {
                    if (isCellMerged(rowIdx, col, merges)) {
                        continue;
                    }
                    MergeCell merge = getMergeCell(rowIdx, col, merges);
                    if (merge != null) {
                        float mergeWidth = sumColWidths(colWidths, col, merge.colSpan(), colCount);
                        float mergeHeight = rowHeight * merge.rowSpan();
                        // 复用 drawCellRect（背景关闭），统一绘制入口
                        drawCellRect(cs, currentX, currentY, mergeWidth, mergeHeight, false, 0f);
                        drawCenteredText(cs, font, merge.text(), currentX, currentY,
                                mergeWidth, mergeHeight, fontSize);
                        currentX += mergeWidth;
                        col += merge.colSpan() - 1;
                        continue;
                    }
                    drawCellRect(cs, currentX, currentY, colWidths[col], rowHeight, false, 0f);
                    drawCenteredText(cs, font, rowData[col], currentX, currentY,
                            colWidths[col], rowHeight, fontSize);
                    currentX += colWidths[col];
                }
                currentY -= rowHeight;
                rowIdx++;
            }
        }

        cs.setLineWidth(1.0f);
        return currentY;
    }

    // ---------- 表格绘制辅助 ----------

    private static float sumColWidths(float[] colWidths, int start, int span, int max) {
        float sum = 0;
        for (int i = start; i < start + span && i < max; i++) {
            sum += colWidths[i];
        }
        return sum;
    }

    /**
     * 绘制单元格矩形（可选背景色填充 + 边框）。
     *
     * @param background 是否填充背景
     * @param bgColor    背景灰度值（0.0 ~ 1.0），仅当 {@code background=true} 时生效
     */
    private static void drawCellRect(PDPageContentStream cs, float x, float y,
                                     float width, float height,
                                     boolean background, float bgColor) throws IOException {
        if (background) {
            cs.setNonStrokingColor(bgColor, bgColor, bgColor);
            cs.addRect(x, y - height, width, height);
            cs.fill();
            cs.setNonStrokingColor(0, 0, 0);
        }
        cs.addRect(x, y - height, width, height);
        cs.stroke();
    }

    private static void drawCenteredText(PDPageContentStream cs, PDType0Font font,
                                         String text, float x, float y,
                                         float width, float height, float fontSize)
            throws IOException {
        String safe = text != null ? text : "";
        float textWidth = font.getStringWidth(safe) / 1000f * fontSize;
        float textX = x + (width - textWidth) / 2;
        cs.beginText();
        cs.newLineAtOffset(textX, y - height + (height - fontSize) / 2 + 2);
        cs.showText(safe);
        cs.endText();
    }

    private static boolean isCellMerged(int row, int col, List<MergeCell> merges) {
        if (merges == null) {
            return false;
        }
        for (MergeCell m : merges) {
            if (row >= m.row() && row < m.row() + m.rowSpan()
                    && col >= m.col() && col < m.col() + m.colSpan()
                    && !(row == m.row() && col == m.col())) {
                return true;
            }
        }
        return false;
    }

    private static MergeCell getMergeCell(int row, int col, List<MergeCell> merges) {
        if (merges == null) {
            return null;
        }
        for (MergeCell m : merges) {
            if (row == m.row() && col == m.col()) {
                return m;
            }
        }
        return null;
    }

    // ---------- 增强表格（换行 / 对齐 / 颜色） ----------

    /**
     * 绘制增强表格（支持自动换行、对齐、内边距、颜色）。
     *
     * @param style 样式配置；{@code null} 时使用默认样式
     * @return 表格底部 Y 坐标
     */
    public static float drawTableAdvanced(PDPageContentStream cs, PDType0Font font,
                                          String[] headers, float[] colWidths,
                                          List<?> data, float x, float y,
                                          RowMapper mapper, TableStyle style)
            throws IOException {
        if (headers == null || headers.length == 0
                || colWidths == null || colWidths.length == 0) {
            log.warn("[drawTableAdvanced] 表头或列宽为空，跳过绘制");
            return y;
        }
        if (headers.length != colWidths.length) {
            throw new IllegalArgumentException("表头数量与列宽数量不一致");
        }

        TableStyle s = style != null ? style : TableStyle.defaultStyle();
        int colCount = headers.length;
        float rowHeight = s.rowHeight();
        float fontSize = s.fontSize();
        float padding = s.padding();

        cs.setNonStrokingColor(s.borderR(), s.borderG(), s.borderB());
        cs.setLineWidth(s.borderWidth());

        float currentY = y;

        // 预计算每列起始 X
        float[] colStartX = new float[colCount];
        float acc = x;
        for (int col = 0; col < colCount; col++) {
            colStartX[col] = acc;
            acc += colWidths[col];
        }

        // 计算表头实际高度（启用换行时按内容调整）
        float headerRowHeight = rowHeight;
        if (s.wrapText()) {
            int maxHeaderLines = 1;
            for (int col = 0; col < colCount; col++) {
                String t = headers[col] != null ? headers[col] : "";
                int lines = calculateTextLines(font, t, colWidths[col] - 2 * padding, fontSize);
                maxHeaderLines = Math.max(maxHeaderLines, lines);
            }
            headerRowHeight = Math.max(rowHeight, maxHeaderLines * rowHeight);
        }

        // 绘制表头
        for (int col = 0; col < colCount; col++) {
            float currentX = colStartX[col];
            float cellWidth = colWidths[col];
            if (s.headerBackground()) {
                cs.setNonStrokingColor(s.headerBgR(), s.headerBgG(), s.headerBgB());
                cs.addRect(currentX, currentY - headerRowHeight, cellWidth, headerRowHeight);
                cs.fill();
                cs.setNonStrokingColor(s.borderR(), s.borderG(), s.borderB());
            }
            cs.addRect(currentX, currentY - headerRowHeight, cellWidth, headerRowHeight);
            cs.stroke();

            String headerText = headers[col] != null ? headers[col] : "";
            if (s.wrapText()) {
                drawWrappedText(cs, font, headerText, currentX, currentY,
                        cellWidth, headerRowHeight, fontSize, rowHeight, padding,
                        s.headerHAlign(), s.headerVAlign(), 0, 0, 0);
            } else {
                drawCenteredText(cs, font, headerText, currentX, currentY,
                        cellWidth, headerRowHeight, fontSize);
            }
        }
        currentY -= headerRowHeight;

        // 绘制数据行
        if (data != null && !data.isEmpty()) {
            int rowIdx = 1;
            for (Object item : data) {
                String[] rowData = mapper.map(rowIdx, item);
                // 按内容计算当前行实际高度
                float actualRowHeight = rowHeight;
                if (s.wrapText()) {
                    int maxLines = 1;
                    for (int col = 0; col < Math.min(rowData.length, colCount); col++) {
                        String t = rowData[col] != null ? rowData[col] : "";
                        int lines = calculateTextLines(font, t,
                                colWidths[col] - 2 * padding, fontSize);
                        maxLines = Math.max(maxLines, lines);
                    }
                    actualRowHeight = Math.max(rowHeight, maxLines * rowHeight);
                }

                for (int col = 0; col < Math.min(rowData.length, colCount); col++) {
                    float currentX = colStartX[col];
                    float cellWidth = colWidths[col];
                    cs.addRect(currentX, currentY - actualRowHeight, cellWidth, actualRowHeight);
                    cs.stroke();

                    String cellText = rowData[col] != null ? rowData[col] : "";
                    if (s.wrapText()) {
                        drawWrappedText(cs, font, cellText, currentX, currentY,
                                cellWidth, actualRowHeight, fontSize, rowHeight, padding,
                                s.dataHAlign(), s.dataVAlign(),
                                s.dataTextR(), s.dataTextG(), s.dataTextB());
                    } else {
                        drawCenteredText(cs, font, cellText, currentX, currentY,
                                cellWidth, actualRowHeight, fontSize);
                    }
                }
                currentY -= actualRowHeight;
                rowIdx++;
            }
        }

        cs.setNonStrokingColor(0, 0, 0);
        cs.setLineWidth(1.0f);
        return currentY;
    }

    // ---------- 文本自动换行 ----------

    /**
     * 在指定矩形区域内绘制自动换行文本。
     *
     * @return 实际占用的行数
     */
    public static int drawWrappedText(PDPageContentStream cs, PDType0Font font, String text,
                                      float x, float y, float width, float height,
                                      float fontSize, float lineHeight, float padding,
                                      TextAlignment hAlign, VerticalAlignment vAlign,
                                      float textColorR, float textColorG, float textColorB)
            throws IOException {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        float availableWidth = width - 2 * padding;
        if (availableWidth <= 0) {
            log.warn("[drawWrappedText] 可用宽度 <= 0，跳过绘制");
            return 0;
        }
        List<String> lines = splitTextByWidth(font, text, availableWidth, fontSize);
        if (lines.isEmpty()) {
            return 0;
        }

        float totalTextHeight = lines.size() * lineHeight;
        float startY;
        switch (vAlign) {
            case TOP -> startY = y - padding - lineHeight;
            case BOTTOM -> startY = y - height + padding + totalTextHeight;
            default -> startY = y - (height - totalTextHeight) / 2;
        }

        cs.setNonStrokingColor(textColorR, textColorG, textColorB);
        try {
            cs.setFont(font, fontSize);
            float currentY = startY;
            for (String line : lines) {
                if (currentY < y - height + padding) {
                    break;
                }
                float textWidth = font.getStringWidth(line) / 1000f * fontSize;
                float textX = switch (hAlign) {
                    case LEFT -> x + padding;
                    case RIGHT -> x + width - padding - textWidth;
                    default -> x + (width - textWidth) / 2;
                };
                cs.beginText();
                cs.newLineAtOffset(textX, currentY);
                cs.showText(line);
                cs.endText();
                currentY -= lineHeight;
            }
        } finally {
            // 确保恢复默认颜色
            cs.setNonStrokingColor(0, 0, 0);
        }
        return lines.size();
    }

    /**
     * 计算文本按宽度拆分后的行数。
     */
    public static int calculateTextLines(PDType0Font font, String text,
                                         float maxWidth, float fontSize) throws IOException {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return splitTextByWidth(font, text, maxWidth, fontSize).size();
    }

    /**
     * 按宽度将文本拆分为多行（优先按空格，超长单词按字符截断）。
     */
    private static List<String> splitTextByWidth(PDType0Font font, String text,
                                                 float maxWidth, float fontSize)
            throws IOException {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return result;
        }
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();

        for (String word : words) {
            String testLine = line.length() == 0 ? word : line + " " + word;
            float testWidth = font.getStringWidth(testLine) / 1000f * fontSize;
            if (testWidth <= maxWidth) {
                if (line.length() > 0) {
                    line.append(' ');
                }
                line.append(word);
            } else {
                if (line.length() > 0) {
                    result.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    // 超长单词按字符截断
                    String remaining = word;
                    while (!remaining.isEmpty()) {
                        int cut = 1;
                        while (cut < remaining.length()
                                && font.getStringWidth(remaining.substring(0, cut)) / 1000f * fontSize <= maxWidth) {
                            cut++;
                        }
                        cut = Math.min(cut, remaining.length());
                        cut = Math.max(cut, 1);
                        result.add(remaining.substring(0, cut));
                        remaining = remaining.substring(cut);
                    }
                    line = new StringBuilder();
                }
            }
        }
        if (line.length() > 0) {
            result.add(line.toString());
        }
        return result;
    }

    // ---------- 分页辅助 ----------

    /**
     * 绘制表头（单行，居中，带边框）。
     *
     * @return 表头底部 Y 坐标
     */
    public static float drawTableHeader(PDPageContentStream cs, PDType0Font font,
                                        String[] headers, float[] colWidths,
                                        float x, float y, float rowHeight, float fontSize)
            throws IOException {
        if (headers == null || colWidths == null || headers.length != colWidths.length) {
            log.warn("[drawTableHeader] 入参非法，跳过绘制");
            return y;
        }
        cs.setFont(font, fontSize);
        float currentX = x;
        for (int i = 0; i < headers.length; i++) {
            cs.addRect(currentX, y - rowHeight, colWidths[i], rowHeight);
            cs.stroke();
            drawCenteredText(cs, font, headers[i], currentX, y, colWidths[i], rowHeight, fontSize);
            currentX += colWidths[i];
        }
        return y - rowHeight;
    }

    /**
     * 绘制单行数据。
     *
     * @return 数据行底部 Y 坐标
     */
    public static float drawDataRow(PDPageContentStream cs, PDType0Font font,
                                    String[] rowData, float[] colWidths,
                                    float x, float y, float rowHeight, float fontSize)
            throws IOException {
        float currentX = x;
        for (int i = 0; i < Math.min(rowData.length, colWidths.length); i++) {
            cs.addRect(currentX, y - rowHeight, colWidths[i], rowHeight);
            cs.stroke();
            drawCenteredText(cs, font, rowData[i], currentX, y, colWidths[i], rowHeight, fontSize);
            currentX += colWidths[i];
        }
        return y - rowHeight;
    }

    /**
     * 绘制合计行（与数据行样式相同）。
     */
    public static float drawTotalRow(PDPageContentStream cs, PDType0Font font,
                                     String[] totalRow, float[] colWidths,
                                     float x, float y, float rowHeight, float fontSize)
            throws IOException {
        return drawDataRow(cs, font, totalRow, colWidths, x, y, rowHeight, fontSize);
    }

    /**
     * 计算当前剩余空间可容纳的最大行数。
     */
    public static int calculateMaxRows(float startY, float rowHeight,
                                       float headerHeight, float bottomMargin) {
        float available = startY - bottomMargin - headerHeight;
        if (available <= 0) {
            return 0;
        }
        return (int) (available / rowHeight);
    }

    /**
     * 计算整页可容纳的数据行数。
     */
    public static int calculateRowsPerPage(float pageHeight, float margin,
                                           float topHeight, float tableHeaderHeight,
                                           float bottomHeight, float rowHeight) {
        float available = pageHeight - 2 * margin - topHeight - tableHeaderHeight - bottomHeight;
        if (available <= 0) {
            return 1;
        }
        return Math.max(1, (int) (available / rowHeight));
    }

    /**
     * 计算总页数（至少为 1）。
     */
    public static int getTotalPages(int totalRows, int rowsPerPage) {
        if (rowsPerPage <= 0) {
            return 1;
        }
        return (int) Math.ceil((double) totalRows / rowsPerPage);
    }

    /**
     * 获取指定页的数据子列表（返回新 {@link ArrayList}，不共享原列表视图）。
     */
    public static <T> List<T> getPageData(List<T> allData, int pageIndex, int rowsPerPage) {
        if (allData == null || allData.isEmpty()) {
            return new ArrayList<>();
        }
        int start = pageIndex * rowsPerPage;
        if (start >= allData.size()) {
            return new ArrayList<>();
        }
        int end = Math.min(start + rowsPerPage, allData.size());
        return new ArrayList<>(allData.subList(start, end));
    }

    /**
     * 是否还有更多数据。
     */
    public static boolean hasMoreData(int totalRows, int pageIndex, int rowsPerPage) {
        return (pageIndex + 1) * rowsPerPage < totalRows;
    }

    // ---------- 分页绘制 ----------

    /**
     * 自动分页绘制表格（每页重复表头）。
     *
     * @return 生成的页数
     */
    public static int drawTableWithPagination(PDDocument doc, PDType0Font font,
                                              String[] headers, float[] colWidths,
                                              List<?> allData, float pageWidth,
                                              float margin, float topHeight, float bottomHeight,
                                              TableStyle style, RowMapper mapper,
                                              PDRectangle pageSize) throws IOException {
        if (allData == null || allData.isEmpty()) {
            log.warn("[drawTableWithPagination] 数据为空，跳过绘制");
            return 0;
        }
        Objects.requireNonNull(doc, "doc 不能为 null");
        Objects.requireNonNull(font, "font 不能为 null");

        TableStyle s = style != null ? style : TableStyle.defaultStyle();
        int totalRows = allData.size();
        float pageHeight = pageSize.getHeight();

        float tableHeaderHeight = s.rowHeight();
        if (s.wrapText()) {
            int maxHeaderLines = 1;
            for (int col = 0; col < headers.length; col++) {
                String t = headers[col] != null ? headers[col] : "";
                int lines = calculateTextLines(font, t,
                        colWidths[col] - 2 * s.padding(), s.fontSize());
                maxHeaderLines = Math.max(maxHeaderLines, lines);
            }
            tableHeaderHeight = Math.max(s.rowHeight(), maxHeaderLines * s.rowHeight());
        }

        float availableHeight = pageHeight - 2 * margin - topHeight - bottomHeight - tableHeaderHeight;
        int rowsPerPage = Math.max(1, (int) (availableHeight / s.rowHeight()));

        int totalPages = (int) Math.ceil((double) totalRows / rowsPerPage);
        if (log.isInfoEnabled()) {
            log.info("[drawTableWithPagination] 总行数={}，每页行数={}，总页数={}",
                    totalRows, rowsPerPage, totalPages);
        }

        int pageIndex = 0;
        int startRow = 0;
        while (startRow < totalRows) {
            int endRow = Math.min(startRow + rowsPerPage, totalRows);
            List<?> pageData = allData.subList(startRow, endRow);

            PDPage page = new PDPage(pageSize);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = page.getMediaBox().getHeight() - margin;
                y = drawTableHeader(cs, font, headers, colWidths, margin, y,
                        s.rowHeight(), s.fontSize());

                int rowNum = startRow + 1;
                for (Object item : pageData) {
                    String[] rowData = mapper.map(rowNum, item);
                    y = drawDataRow(cs, font, rowData, colWidths, margin, y,
                            s.rowHeight(), s.fontSize());
                    rowNum++;
                }

                String pageNum = "第 " + (pageIndex + 1) + " / " + totalPages + " 页";
                cs.setFont(font, 8);
                float pageNumWidth = font.getStringWidth(pageNum) / 1000f * 8;
                cs.beginText();
                cs.newLineAtOffset(pageWidth - margin - pageNumWidth, margin / 2);
                cs.showText(pageNum);
                cs.endText();
            }

            startRow = endRow;
            pageIndex++;
        }
        return totalPages;
    }

    /**
     * 自动分页绘制表格（使用默认样式）。
     */
    public static int drawTableWithPagination(PDDocument doc, PDType0Font font,
                                              String[] headers, float[] colWidths,
                                              List<?> allData, float pageWidth,
                                              float margin, float topHeight, float bottomHeight,
                                              RowMapper mapper, PDRectangle pageSize)
            throws IOException {
        return drawTableWithPagination(doc, font, headers, colWidths, allData,
                pageWidth, margin, topHeight, bottomHeight,
                TableStyle.defaultStyle(), mapper, pageSize);
    }

    // ================================================================
    // 第五部分：Web 响应输出
    // ================================================================

    /**
     * 将 PDF 字节数组写入 HTTP 响应（触发浏览器下载）。
     *
     * <p><b>安全：</b>文件名使用 RFC 5987 编码，支持中文。
     *
     * <p><b>流所有权：</b>不关闭 {@code response.getOutputStream()}——由 Servlet 容器管理。
     *
     * <p><b>前置条件：</b>调用前 {@code response} 必须满足：
     * <ul>
     *     <li>尚未调用 {@link HttpServletResponse#getWriter()}</li>
     *     <li>尚未调用 {@link HttpServletResponse#getOutputStream()}</li>
     *     <li>{@link HttpServletResponse#isCommitted()} 返回 {@code false}</li>
     * </ul>
     *
     * @param response HTTP 响应，不允许为 {@code null}
     * @param pdfBytes PDF 字节数组，不允许为 {@code null}
     * @param fileName 下载文件名（含 {@code .pdf} 后缀）
     * @throws IllegalStateException 响应已提交时抛出
     */
    public static void writePdfResponse(HttpServletResponse response,
                                        byte[] pdfBytes,
                                        String fileName) throws IOException {
        Objects.requireNonNull(response, "response 不能为 null");
        Objects.requireNonNull(pdfBytes, "pdfBytes 不能为 null");
        Objects.requireNonNull(fileName, "fileName 不能为 null");

        // 防止响应已提交时静默丢弃 Header（CWE-252）
        if (response.isCommitted()) {
            throw new IllegalStateException(
                    "响应已提交，无法写入 PDF：" + fileName);
        }

        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                .replace("+", "%20");
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition",
                "attachment; filename*=UTF-8''" + encoded);
        response.setContentLengthLong(pdfBytes.length);

        OutputStream os = response.getOutputStream();
        os.write(pdfBytes);
        os.flush();

        if (log.isInfoEnabled()) {
            log.info("[writePdfResponse] PDF 已写入响应，文件名={}，大小={} 字节",
                    fileName, pdfBytes.length);
        }
    }
}

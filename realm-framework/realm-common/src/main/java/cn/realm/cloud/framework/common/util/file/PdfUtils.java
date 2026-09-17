package cn.realm.cloud.framework.common.util.file;

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
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PDFBox 3.0.8 企业级统一工具类
 * <p>
 * 整合了通用 PDF 操作（加载/合并/提取/转图）与业务排版辅助（字体缓存/宽度计算/金额格式化）
 * 支持高级表格绘制（合并单元格、边框样式、分页计算、粗体字体、自动换行）
 * </p>
 *
 * <p><b>使用建议：</b></p>
 * <ul>
 *   <li>对于文件级操作（合并、转图、全文提取），直接调用静态方法，内部自动管理 PDDocument 生命周期</li>
 *   <li>对于复杂报表生成（需要精确排版），调用方自行创建 PDDocument，再配合业务辅助方法（如 loadFont、formatAmount）</li>
 * </ul>
 *
 * <p><b>字体缓存说明：</b></p>
 * <ul>
 *   <li>缓存的是字体文件的字节数据（byte[]），而非 PDType0Font 对象</li>
 *   <li>因为 PDType0Font 与加载它的 PDDocument 强绑定，跨文档使用会抛出异常</li>
 *   <li>每次调用 loadFont() 都会基于缓存的字节数据创建新的字体对象，绑定到当前文档</li>
 *   <li>粗体字体（loadFontBold）使用独立缓存，与常规字体互不干扰</li>
 * </ul>
 *
 * <p><b>功能全景图：</b></p>
 * <pre>
 * PdfUtils 2.0.0
 * ├── 📄 文档操作
 * │   ├── load / loadFromBytes
 * │   ├── toByteArray
 * │   ├── getPageCount / isEncrypted
 * │   └── closeQuietly
 * ├── 📝 文本提取
 * │   └── extractText
 * ├── 🔗 PDF 合并
 * │   ├── merge
 * │   └── mergeFromBytes
 * ├── 🖼️ 转图片
 * │   ├── toImages
 * │   └── toImage
 * ├── 🔤 字体管理
 * │   ├── loadFont（常规）
 * │   ├── loadFontBold（粗体）
 * │   ├── clearFontCache
 * │   └── isFontCached / isFontBoldCached
 * ├── 📊 格式化工具
 * │   ├── formatAmount
 * │   ├── safeString
 * │   ├── splitLongText
 * │   └── getStringWidth
 * ├── 📐 排版辅助
 * │   ├── scaleColWidths
 * │   ├── drawTextLine
 * │   ├── drawTitle
 * │   └── getTotalWidth
 * ├── 📋 表格绘制
 * │   ├── drawTable（3 个重载）
 * │   ├── drawTableHeader
 * │   ├── drawDataRow
 * │   ├── drawTotalRow
 * │   └── drawTableAdvanced（含 TableStyle）
 * ├── 🔄 分页支持
 * │   ├── calculateMaxRows
 * │   ├── calculateRowsPerPage
 * │   ├── getTotalPages
 * │   ├── getPageData
 * │   ├── hasMoreData
 * │   └── drawTableWithPagination（2 个重载）
 * ├── 📝 自动换行
 * │   ├── drawWrappedText
 * │   ├── splitTextByWidth
 * │   └── calculateTextLines
 * ├── 🔗 合并单元格
 * │   └── MergeCell 内部类
 * ├── 🎨 样式配置
 * │   └── TableStyle + Builder
 * └── 🌐 Web 输出
 *     └── writePdfResponse
 * </pre>
 *
 * @author QI Guang
 * @version 2.0.0
 * @since 1.0.0
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public final class PdfUtils {

    /**
     * 日志记录器
     */
    private static final Logger log = LoggerFactory.getLogger(PdfUtils.class);

    /**
     * 常规字体数据缓存
     * <p>key = 字体文件在 classpath 下的路径，value = 字体文件的完整字节数据</p>
     */
    private static final ConcurrentHashMap<String, byte[]> FONT_DATA_CACHE = new ConcurrentHashMap<>();

    /**
     * 粗体字体数据缓存（独立缓存，便于区分）
     */
    private static final ConcurrentHashMap<String, byte[]> FONT_BOLD_DATA_CACHE = new ConcurrentHashMap<>();

    /**
     * 金额格式化器（线程安全）
     * <p>格式：保留两位小数，添加千分位分隔符，使用英文点号作为小数点</p>
     * <p>示例：1234.56 → "1,234.56"</p>
     */
    private static final ThreadLocal<DecimalFormat> DECIMAL_FORMAT = ThreadLocal.withInitial(() -> {
        DecimalFormat df = new DecimalFormat("#,##0.00");
        df.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.US));
        return df;
    });

    /**
     * 私有构造方法，防止实例化
     */
    private PdfUtils() {
        // 工具类私有构造
    }

    // ================================================================
    // 第一部分：通用 PDF 文档操作（加载 / 保存 / 信息）
    // ================================================================

    /**
     * 从文件加载 PDF 文档
     *
     * <p>使用 PDFBox 3.0 标准方式加载，调用方需在使用完毕后关闭 PDDocument</p>
     *
     * @param file PDF 文件对象
     * @return PDDocument 实例，使用完毕后需调用 close()
     * @throws IOException 文件不存在、无法读取或格式错误
     */
    public static PDDocument load(File file) throws IOException {
        log.debug("加载 PDF 文件: {}", file.getAbsolutePath());
        return Loader.loadPDF(file);
    }

    /**
     * 从字节数组加载 PDF 文档
     *
     * @param bytes PDF 文件的字节数组
     * @return PDDocument 实例，使用完毕后需调用 close()
     * @throws IOException 数据格式错误或无法解析
     */
    public static PDDocument load(byte[] bytes) throws IOException {
        log.debug("从字节数组加载 PDF，大小: {} bytes", bytes != null ? bytes.length : 0);
        return Loader.loadPDF(bytes);
    }

    /**
     * 从字节数组加载 PDF 文档（使用 RandomAccessReadBuffer）
     *
     * <p>与 {@link #load(byte[])} 功能相同，使用 RandomAccessReadBuffer 实现</p>
     *
     * @param bytes PDF 文件的字节数组
     * @return PDDocument 实例，使用完毕后需调用 close()
     * @throws IOException 数据格式错误或无法解析
     */
    public static PDDocument loadFromBytes(byte[] bytes) throws IOException {
        log.debug("从字节数组加载 PDF（RandomAccessReadBuffer），大小: {} bytes", bytes != null ? bytes.length : 0);
        return Loader.loadPDF(new RandomAccessReadBuffer(bytes));
    }

    /**
     * 将 PDDocument 保存为字节数组
     *
     * @param document PDDocument 实例
     * @return PDF 文件的字节数组
     * @throws IOException 保存失败
     */
    public static byte[] toByteArray(PDDocument document) throws IOException {
        log.debug("将 PDDocument 转换为字节数组");
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            document.save(baos);
            log.debug("PDDocument 转换完成，大小: {} bytes", baos.size());
            return baos.toByteArray();
        }
    }

    /**
     * 获取 PDF 文档的页数
     *
     * @param document PDDocument 实例
     * @return 总页数
     */
    public static int getPageCount(PDDocument document) {
        return document.getPages().getCount();
    }

    /**
     * 判断 PDF 文档是否已加密
     *
     * @param document PDDocument 实例
     * @return true=已加密，false=未加密
     */
    public static boolean isEncrypted(PDDocument document) {
        return document.isEncrypted();
    }

    /**
     * 安全关闭 PDDocument（忽略关闭时的异常）
     *
     * <p>适用于 finally 块中，避免因关闭异常影响主流程</p>
     *
     * @param document PDDocument 实例，可为 null
     */
    public static void closeQuietly(PDDocument document) {
        if (document != null) {
            try {
                document.close();
                log.trace("PDDocument 已关闭");
            } catch (IOException ignored) {
                // 忽略关闭异常
                log.trace("关闭 PDDocument 时发生异常（已忽略）");
            }
        }
    }

    // ================================================================
    // 第二部分：文本提取
    // ================================================================

    /**
     * 提取 PDF 全文文本
     *
     * @param document PDDocument 实例（调用方负责关闭）
     * @return 提取的完整文本内容
     * @throws IOException 提取失败
     */
    public static String extractText(PDDocument document) throws IOException {
        log.debug("提取 PDF 全文文本");
        PDFTextStripper stripper = new PDFTextStripper();
        return stripper.getText(document);
    }

    /**
     * 提取 PDF 指定页面范围的文本（页码从 1 开始）
     *
     * @param document  PDDocument 实例（调用方负责关闭）
     * @param startPage 起始页码（从 1 开始）
     * @param endPage   结束页码（从 1 开始）
     * @return 指定范围的文本内容
     * @throws IOException 提取失败
     */
    public static String extractText(PDDocument document, int startPage, int endPage) throws IOException {
        log.debug("提取 PDF 文本，页码范围: {} - {}", startPage, endPage);
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(startPage);
        stripper.setEndPage(endPage);
        return stripper.getText(document);
    }

    // ================================================================
    // 第三部分：PDF 合并
    // ================================================================

    /**
     * 合并多个 PDF 文件为一个 PDF 文件
     *
     * <p>按 sources 列表的顺序合并，源文件不会被修改</p>
     *
     * @param sources    源 PDF 文件列表（按顺序合并）
     * @param outputFile 输出文件（不能是源文件之一）
     * @throws IOException 合并失败或文件无法写入
     */
    public static void merge(List<File> sources, File outputFile) throws IOException {
        log.info("开始合并 PDF 文件，源文件数: {}, 输出: {}", sources != null ? sources.size() : 0, outputFile.getAbsolutePath());
        try (PDDocument destination = new PDDocument();
             FileOutputStream outputStream = new FileOutputStream(outputFile)) {

            PDFMergerUtility merger = new PDFMergerUtility();
            merger.setDestinationStream(outputStream);

            for (File sourceFile : sources) {
                try (PDDocument source = Loader.loadPDF(sourceFile)) {
                    merger.appendDocument(destination, source);
                    log.debug("已合并: {}", sourceFile.getName());
                }
            }

            destination.save(outputStream);
            log.info("PDF 合并完成: {}", outputFile.getAbsolutePath());
        }
    }

    /**
     * 合并多个 PDF 字节数组为一个 PDF 文件
     *
     * <p>按 sources 列表的顺序合并</p>
     *
     * @param sources    源 PDF 字节数组列表（按顺序合并）
     * @param outputFile 输出文件
     * @throws IOException 合并失败或文件无法写入
     */
    public static void mergeFromBytes(List<byte[]> sources, File outputFile) throws IOException {
        log.info("开始合并 PDF 字节数组，源文件数: {}, 输出: {}", sources != null ? sources.size() : 0, outputFile.getAbsolutePath());
        try (PDDocument destination = new PDDocument();
             FileOutputStream outputStream = new FileOutputStream(outputFile)) {

            PDFMergerUtility merger = new PDFMergerUtility();
            merger.setDestinationStream(outputStream);

            for (byte[] bytes : sources) {
                try (PDDocument source = Loader.loadPDF(new RandomAccessReadBuffer(bytes))) {
                    merger.appendDocument(destination, source);
                    log.debug("已合并一个 PDF 字节数组，大小: {} bytes", bytes != null ? bytes.length : 0);
                }
            }

            destination.save(outputStream);
            log.info("PDF 合并完成: {}", outputFile.getAbsolutePath());
        }
    }

    // ================================================================
    // 第四部分：PDF 转图片
    // ================================================================

    /**
     * 将 PDF 所有页面转换为图片
     *
     * @param document PDDocument 实例（调用方负责关闭）
     * @param dpi      图片分辨率（如 150、200、300）
     * @return BufferedImage 列表，按页面顺序排列
     * @throws IOException 渲染失败
     */
    public static List<BufferedImage> toImages(PDDocument document, int dpi) throws IOException {
        log.debug("PDF 转图片，DPI: {}, 页数: {}", dpi, document.getPages().getCount());
        PDFRenderer renderer = new PDFRenderer(document);
        List<BufferedImage> images = new ArrayList<>();
        int pageCount = document.getPages().getCount();

        for (int i = 0; i < pageCount; i++) {
            BufferedImage image = renderer.renderImageWithDPI(i, dpi);
            images.add(image);
            log.trace("第 {} 页转换完成", i + 1);
        }

        log.debug("PDF 转图片完成，共 {} 张", images.size());
        return images;
    }

    /**
     * 将 PDF 指定页面转换为图片
     *
     * @param document  PDDocument 实例（调用方负责关闭）
     * @param pageIndex 页码索引（从 0 开始）
     * @param dpi       图片分辨率（如 150、200、300）
     * @return BufferedImage 图片对象
     * @throws IOException 渲染失败
     */
    public static BufferedImage toImage(PDDocument document, int pageIndex, int dpi) throws IOException {
        log.debug("PDF 转图片，页码: {}, DPI: {}", pageIndex, dpi);
        PDFRenderer renderer = new PDFRenderer(document);
        return renderer.renderImageWithDPI(pageIndex, dpi);
    }

    // ================================================================
    // 第五部分：创建 / 写入 PDF（通用 + 业务结合）
    // ================================================================

    /**
     * 创建一个包含简单文本的 PDF 文档（使用标准 Helvetica 字体，不支持中文）
     *
     * <p>适用于纯英文/数字内容的快速创建</p>
     *
     * @param content    文本内容
     * @param outputFile 输出文件
     * @throws IOException 创建失败或文件无法写入
     * @see #createSimplePdfWithChinese(String, File, String)
     */
    public static void createSimplePdf(String content, File outputFile) throws IOException {
        log.info("创建简单 PDF: {}", outputFile.getAbsolutePath());
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(50, 750);
                contentStream.showText(content);
                contentStream.endText();
            }

            document.save(outputFile);
            log.info("简单 PDF 创建完成: {}", outputFile.getAbsolutePath());
        }
    }

    /**
     * 创建一个包含中文文本的 PDF 文档（使用外部字体）
     *
     * <p>适用于中文内容的 PDF 创建，需要提供中文字体文件路径</p>
     *
     * @param content    文本内容
     * @param outputFile 输出文件
     * @param fontPath   常规字体在 classpath 下的路径，如 "/fonts/simsun.ttf"
     * @throws IOException 创建失败、字体加载失败或文件无法写入
     * @see #loadFont(PDDocument, String)
     */
    public static void createSimplePdfWithChinese(String content, File outputFile, String fontPath) throws IOException {
        log.info("创建中文 PDF: {}, 字体: {}", outputFile.getAbsolutePath(), fontPath);
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            PDType0Font font = loadFont(document, fontPath);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(font, 12);
                contentStream.newLineAtOffset(50, 750);
                contentStream.showText(content);
                contentStream.endText();
            }

            document.save(outputFile);
            log.info("中文 PDF 创建完成: {}", outputFile.getAbsolutePath());
        }
    }

    // ================================================================
    // 第六部分：业务排版辅助（字体 / 宽度 / 换行 / 格式化）
    // ================================================================

    /**
     * 从 classpath 加载中文字体并缓存（常规字体）
     *
     * <p><b>缓存策略：</b></p>
     * <ul>
     *   <li>缓存的是字体文件的字节数据（byte[]），而非 PDType0Font 对象</li>
     *   <li>因为 PDType0Font 与加载它的 PDDocument 强绑定，跨文档使用会抛出异常</li>
     *   <li>每次调用都会基于缓存的字节数据创建新的字体对象，绑定到当前 document</li>
     * </ul>
     *
     * <p><b>性能优势：</b></p>
     * <ul>
     *   <li>首次加载：读取字体文件（约 15MB）并缓存字节数据</li>
     *   <li>后续调用：直接从内存获取字节数据，无磁盘 I/O</li>
     * </ul>
     *
     * <p><b>使用建议：</b></p>
     * <ul>
     *   <li>在应用启动时（@PostConstruct）调用一次进行预热</li>
     *   <li>字体文件需放在 src/main/resources/static/fonts/ 目录下</li>
     * </ul>
     *
     * @param document 当前 PDF 文档（用于加载字体，不能为 null）
     * @param fontPath 字体在 classpath 下的路径，如 "/static/fonts/msyh.TTF"
     * @return PDType0Font 对象，已绑定到当前 document
     * @throws IllegalArgumentException 当 document 为 null 时
     * @throws IOException              字体文件不存在、读取失败或加载失败
     */
    public static PDType0Font loadFont(PDDocument document, String fontPath) throws IOException {
        if (document == null) {
            throw new IllegalArgumentException("PDDocument 不能为 null");
        }
        log.debug("加载字体: {}", fontPath);

        // 1. 从缓存获取字体字节数据
        byte[] fontData = FONT_DATA_CACHE.get(fontPath);

        if (fontData == null) {
            // 2. 读取字体文件到字节数组（兼容 Java 8）
            try (InputStream is = PdfUtils.class.getResourceAsStream(fontPath)) {
                if (is == null) {
                    throw new IOException("字体文件未找到: " + fontPath);
                }
                fontData = toByteArray(is);
                if (fontData.length == 0) {
                    throw new IOException("字体文件为空: " + fontPath);
                }
                // 加载成功后才放入缓存
                FONT_DATA_CACHE.putIfAbsent(fontPath, fontData);
                log.info("✅ 字体文件已加载到缓存: {}, 大小: {} bytes", fontPath, fontData.length);
            } catch (IOException e) {
                // 加载失败时，确保缓存中没有损坏的数据
                FONT_DATA_CACHE.remove(fontPath);
                log.error("❌ 字体文件加载失败: {}", fontPath, e);
                throw e;
            }
        }

        // 3. 从缓存的字节数据创建字体对象（绑定到当前 document）
        try (ByteArrayInputStream bais = new ByteArrayInputStream(fontData)) {
            PDType0Font font = PDType0Font.load(document, bais, true);
            log.trace("字体已绑定到当前文档: {}", fontPath);
            return font;
        } catch (IOException e) {
            // 如果创建字体失败，可能是缓存的数据已损坏，清除缓存
            FONT_DATA_CACHE.remove(fontPath);
            log.warn("从缓存创建字体失败，已清除缓存: {}", fontPath, e);
            throw e;
        }
    }

    /**
     * 从 classpath 加载加粗中文字体并缓存（粗体字体）
     *
     * <p><b>使用说明：</b></p>
     * <ul>
     *   <li>需要单独提供粗体字体文件（如 msyhbd.TTF）</li>
     *   <li>缓存机制与 {@link #loadFont(PDDocument, String)} 完全相同，但使用独立缓存</li>
     *   <li>适用于标题、表头等需要突出显示的文本</li>
     * </ul>
     *
     * @param document     当前 PDF 文档（用于加载字体，不能为 null）
     * @param fontBoldPath 粗体字体在 classpath 下的路径，如 "/static/fonts/msyhbd.TTF"
     * @return PDType0Font 粗体对象，已绑定到当前 document
     * @throws IllegalArgumentException 当 document 为 null 时
     * @throws IOException              字体文件不存在、读取失败或加载失败
     */
    public static PDType0Font loadFontBold(PDDocument document, String fontBoldPath) throws IOException {
        if (document == null) {
            throw new IllegalArgumentException("PDDocument 不能为 null");
        }
        log.debug("加载粗体字体: {}", fontBoldPath);

        // 使用独立缓存
        byte[] fontData = FONT_BOLD_DATA_CACHE.get(fontBoldPath);

        if (fontData == null) {
            try (InputStream is = PdfUtils.class.getResourceAsStream(fontBoldPath)) {
                if (is == null) {
                    throw new IOException("粗体字体文件未找到: " + fontBoldPath);
                }
                fontData = toByteArray(is);
                if (fontData.length == 0) {
                    throw new IOException("粗体字体文件为空: " + fontBoldPath);
                }
                FONT_BOLD_DATA_CACHE.putIfAbsent(fontBoldPath, fontData);
                log.info("✅ 粗体字体文件已加载到缓存: {}, 大小: {} bytes", fontBoldPath, fontData.length);
            } catch (IOException e) {
                FONT_BOLD_DATA_CACHE.remove(fontBoldPath);
                log.error("❌ 粗体字体文件加载失败: {}", fontBoldPath, e);
                throw e;
            }
        }

        try (ByteArrayInputStream bais = new ByteArrayInputStream(fontData)) {
            PDType0Font font = PDType0Font.load(document, bais, true);
            log.trace("粗体字体已绑定到当前文档: {}", fontBoldPath);
            return font;
        } catch (IOException e) {
            FONT_BOLD_DATA_CACHE.remove(fontBoldPath);
            log.warn("从缓存创建粗体字体失败，已清除缓存: {}", fontBoldPath, e);
            throw e;
        }
    }

    /**
     * 清除所有字体数据缓存（常规+粗体）
     */
    public static void clearFontCache() {
        FONT_DATA_CACHE.clear();
        FONT_BOLD_DATA_CACHE.clear();
        log.info("所有字体数据缓存已清除");
    }

    /**
     * 检查常规字体是否已被缓存
     *
     * @param fontPath 字体在 classpath 下的路径
     * @return true=已缓存，false=未缓存
     */
    public static boolean isFontCached(String fontPath) {
        return FONT_DATA_CACHE.containsKey(fontPath);
    }

    /**
     * 检查粗体字体是否已被缓存
     *
     * @param fontBoldPath 粗体字体在 classpath 下的路径
     * @return true=已缓存，false=未缓存
     */
    public static boolean isFontBoldCached(String fontBoldPath) {
        return FONT_BOLD_DATA_CACHE.containsKey(fontBoldPath);
    }

    /**
     * 兼容 Java 8 的 InputStream → byte[] 转换工具方法
     *
     * <p>Java 8 没有 InputStream.readAllBytes()，使用 ByteArrayOutputStream 实现</p>
     *
     * @param input 输入流
     * @return 读取到的完整字节数组
     * @throws IOException 读取失败
     */
    private static byte[] toByteArray(InputStream input) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int len;
        while ((len = input.read(buffer)) != -1) {
            baos.write(buffer, 0, len);
        }
        return baos.toByteArray();
    }

    /**
     * 计算指定文本在给定字体和字号下的宽度（单位：点）
     *
     * <p>可用于表格列宽自适应或文本换行计算</p>
     *
     * @param font     字体对象
     * @param text     文本内容
     * @param fontSize 字号
     * @return 文本宽度（单位：点）
     * @throws IOException 计算失败
     */
    public static float getStringWidth(PDType0Font font, String text, float fontSize) throws IOException {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return font.getStringWidth(text) / 1000f * fontSize;
    }

    /**
     * 将长文本按指定最大字符数拆分为多行
     *
     * <p>尽量在空格处断开，避免单词被截断</p>
     *
     * @param text     原始文本
     * @param maxChars 每行最大字符数
     * @return 行列表（非空，至少包含空字符串）
     */
    public static List<String> splitLongText(String text, int maxChars) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            lines.add("");
            return lines;
        }
        if (text.length() <= maxChars) {
            lines.add(text);
            return lines;
        }
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxChars, text.length());
            if (end < text.length()) {
                int spaceIdx = text.lastIndexOf(' ', end);
                if (spaceIdx > start) {
                    end = spaceIdx;
                }
            }
            lines.add(text.substring(start, end).trim());
            start = end;
        }
        return lines;
    }

    /**
     * 格式化金额：保留两位小数，添加千分位分隔符
     *
     * <p>示例：1234.56 → "1,234.56"</p>
     * <p>null 值 → "0.00"</p>
     *
     * @param amount BigDecimal 金额
     * @return 格式化后的金额字符串
     */
    public static String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }
        return DECIMAL_FORMAT.get().format(amount);
    }

    /**
     * 格式化整数金额：保留两位小数
     *
     * <p>示例：1234 → "1,234.00"</p>
     * <p>null 值 → "0.00"</p>
     *
     * @param value Integer 值
     * @return 格式化后的金额字符串
     */
    public static String formatAmount(Integer value) {
        if (value == null) {
            return "0.00";
        }
        return DECIMAL_FORMAT.get().format(value);
    }

    /**
     * 安全地获取对象字符串，null 值返回空字符串
     *
     * @param obj 任意对象
     * @return obj.toString() 或 ""
     */
    public static String safeString(Object obj) {
        return obj == null ? "" : obj.toString();
    }

    /**
     * 计算表格各列总宽度
     *
     * @param colWidths 各列宽度数组
     * @return 总宽度
     */
    public static float getTotalWidth(float[] colWidths) {
        float sum = 0;
        for (float w : colWidths) {
            sum += w;
        }
        return sum;
    }

    // ================================================================
    // 获取标准字体
    // ================================================================

    /**
     * 获取 PDFBox 3.0 标准 14 种字体之一
     *
     * <p>标准字体仅支持英文/数字，不支持中文</p>
     *
     * @param fontName 字体枚举值，如 Standard14Fonts.FontName.HELVETICA
     * @return PDType1Font 实例
     */
    public static PDType1Font getStandardFont(Standard14Fonts.FontName fontName) {
        return new PDType1Font(fontName);
    }

    // ================================================================
    // 业务排版通用辅助（表格绘制、标题绘制、文本绘制）
    // ================================================================

    /**
     * 按比例调整列宽以适应目标总宽度
     *
     * <p>传入的 colWidths 数组会被直接修改</p>
     *
     * @param colWidths   原始列宽数组（会被修改）
     * @param targetWidth 目标总宽度（页面宽度 - 2 * 边距）
     */
    public static void scaleColWidths(float[] colWidths, float targetWidth) {
        if (colWidths == null || colWidths.length == 0 || targetWidth <= 0) {
            return;
        }
        float total = 0;
        for (float w : colWidths) {
            total += w;
        }
        if (total == 0) {
            return;
        }
        float scale = targetWidth / total;
        for (int i = 0; i < colWidths.length; i++) {
            colWidths[i] *= scale;
        }
        log.trace("列宽已按比例调整，目标宽度: {}, 调整后列宽: {}", targetWidth, colWidths);
    }

    /**
     * 绘制单行文本（通用方法）
     *
     * <p>自动换行：调用后返回下一行的 Y 坐标，便于连续绘制多行文本</p>
     *
     * @param cs        内容流
     * @param font      字体对象
     * @param text      文本内容
     * @param x         X 坐标（左对齐）
     * @param y         Y 坐标（基线）
     * @param fontSize  字号
     * @param rowHeight 行高（用于计算下一行 Y）
     * @return 下一行的 Y 坐标（y - rowHeight）
     * @throws IOException 绘制失败
     */
    public static float drawTextLine(PDPageContentStream cs, PDType0Font font, String text,
                                     float x, float y, float fontSize, float rowHeight) throws IOException {
        if (text == null) {
            text = "";
        }
        cs.setFont(font, fontSize);
        cs.beginText();
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
        return y - rowHeight;
    }

    /**
     * 绘制标题（通用方法）
     *
     * <p>标题通常使用较大的字号，调用后自动换行并返回下一行的 Y 坐标</p>
     *
     * @param cs           内容流
     * @param font         字体对象
     * @param title        标题文本
     * @param x            X 坐标（左对齐）
     * @param y            Y 坐标（基线）
     * @param fontSize     标题字号
     * @param marginBottom 标题下方留白间距
     * @return 下一行的 Y 坐标（y - marginBottom）
     * @throws IOException 绘制失败
     */
    public static float drawTitle(PDPageContentStream cs, PDType0Font font, String title,
                                  float x, float y, float fontSize, float marginBottom) throws IOException {
        cs.setFont(font, fontSize);
        cs.beginText();
        cs.newLineAtOffset(x, y);
        cs.showText(title != null ? title : "");
        cs.endText();
        return y - marginBottom;
    }

    // ================================================================
    // 表格绘制（核心方法，支持合并单元格、边框样式、背景色）
    // ================================================================

    /**
     * 绘制通用表格（默认无背景色、边框宽度=1.0、无合并）
     * <p>表头和数据行均居中对齐，带边框</p>
     *
     * @param cs        内容流
     * @param font      字体对象
     * @param headers   表头数组（长度 = 列数）
     * @param colWidths 列宽数组（长度 = 列数）
     * @param data      数据列表（每个元素对应一行数据）
     * @param x         表格左上角 X 坐标
     * @param y         表格左上角 Y 坐标（表头顶部）
     * @param mapper    行映射器（将数据对象转换为字符串数组）
     * @param rowHeight 行高
     * @param fontSize  字号
     * @return 表格底部 Y 坐标
     * @throws IOException 绘制失败
     */
    public static float drawTable(PDPageContentStream cs, PDType0Font font,
                                  String[] headers, float[] colWidths,
                                  List<?> data, float x, float y,
                                  RowMapper mapper, float rowHeight, float fontSize) throws IOException {
        return drawTable(cs, font, headers, colWidths, data, x, y, mapper, rowHeight, fontSize, false);
    }

    /**
     * 绘制通用表格（支持表头背景色，其他默认）
     *
     * @param cs               内容流
     * @param font             字体对象
     * @param headers          表头数组（长度 = 列数）
     * @param colWidths        列宽数组（长度 = 列数）
     * @param data             数据列表（每个元素对应一行数据）
     * @param x                表格左上角 X 坐标
     * @param y                表格左上角 Y 坐标（表头顶部）
     * @param mapper           行映射器（将数据对象转换为字符串数组）
     * @param rowHeight        行高
     * @param fontSize         字号
     * @param headerBackground 是否启用表头灰色背景
     * @return 表格底部 Y 坐标
     * @throws IOException 绘制失败
     */
    public static float drawTable(PDPageContentStream cs, PDType0Font font,
                                  String[] headers, float[] colWidths,
                                  List<?> data, float x, float y,
                                  RowMapper mapper, float rowHeight, float fontSize,
                                  boolean headerBackground) throws IOException {
        // 调用完整版，默认边框宽度=1.0，无合并
        return drawTable(cs, font, headers, colWidths, data, x, y, mapper, rowHeight, fontSize,
                headerBackground, 1.0f, null);
    }

    /**
     * 绘制通用表格（完整版，支持边框宽度、合并单元格）
     * <p>表头和数据行均居中对齐，带边框</p>
     *
     * @param cs               内容流
     * @param font             字体对象
     * @param headers          表头数组（长度 = 列数）
     * @param colWidths        列宽数组（长度 = 列数）
     * @param data             数据列表（每个元素对应一行数据）
     * @param x                表格左上角 X 坐标
     * @param y                表格左上角 Y 坐标（表头顶部）
     * @param mapper           行映射器（将数据对象转换为字符串数组）
     * @param rowHeight        行高
     * @param fontSize         字号
     * @param headerBackground 是否启用表头灰色背景
     * @param borderWidth      边框宽度（单位：点，默认 1.0）
     * @param merges           合并单元格列表（可为 null）
     * @return 表格底部 Y 坐标
     * @throws IOException 绘制失败
     */
    public static float drawTable(PDPageContentStream cs, PDType0Font font,
                                  String[] headers, float[] colWidths,
                                  List<?> data, float x, float y,
                                  RowMapper mapper, float rowHeight, float fontSize,
                                  boolean headerBackground, float borderWidth,
                                  List<MergeCell> merges) throws IOException {
        if (headers == null || headers.length == 0 || colWidths == null || colWidths.length == 0) {
            log.warn("表头或列宽为空，跳过表格绘制");
            return y;
        }
        if (headers.length != colWidths.length) {
            throw new IllegalArgumentException("表头数量与列宽数量不一致");
        }

        int colCount = headers.length;
        float currentY = y;
        float lightGray = 0.92f; // 表头背景色

        log.debug("开始绘制表格，列数: {}, 数据行数: {}", colCount, data != null ? data.size() : 0);

        // 设置边框宽度
        cs.setLineWidth(borderWidth);

        // ---- 1. 绘制表头（第0行） ----
        float currentX = x;
        for (int col = 0; col < colCount; col++) {
            // 检查该单元格是否被合并覆盖
            if (isCellMerged(0, col, merges)) {
                continue; // 被合并覆盖，跳过
            }

            // 检查该单元格是否是合并单元格的起始位置
            MergeCell merge = getMergeCell(0, col, merges);
            if (merge != null) {
                // 绘制合并单元格
                float mergeWidth = 0;
                for (int i = col; i < col + merge.colSpan && i < colCount; i++) {
                    mergeWidth += colWidths[i];
                }
                float mergeHeight = rowHeight * merge.rowSpan;

                // 背景色
                if (headerBackground) {
                    cs.setNonStrokingColor(lightGray, lightGray, lightGray);
                    cs.addRect(currentX, currentY - mergeHeight, mergeWidth, mergeHeight);
                    cs.fill();
                    cs.setNonStrokingColor(0, 0, 0);
                }
                // 边框
                cs.addRect(currentX, currentY - mergeHeight, mergeWidth, mergeHeight);
                cs.stroke();

                // 文本居中
                String text = merge.text != null ? merge.text : "";
                float textWidth = font.getStringWidth(text) / 1000f * fontSize;
                float textX = currentX + (mergeWidth - textWidth) / 2;
                cs.beginText();
                cs.newLineAtOffset(textX, currentY - mergeHeight + (mergeHeight - fontSize) / 2 + 2);
                cs.showText(text);
                cs.endText();

                // 跳过被合并的列
                currentX += mergeWidth;
                col += merge.colSpan - 1;
                continue;
            }

            // 普通单元格（表头）
            // 背景色
            if (headerBackground) {
                cs.setNonStrokingColor(lightGray, lightGray, lightGray);
                cs.addRect(currentX, currentY - rowHeight, colWidths[col], rowHeight);
                cs.fill();
                cs.setNonStrokingColor(0, 0, 0);
            }
            // 边框
            cs.addRect(currentX, currentY - rowHeight, colWidths[col], rowHeight);
            cs.stroke();

            String text = headers[col] != null ? headers[col] : "";
            float textWidth = font.getStringWidth(text) / 1000f * fontSize;
            float textX = currentX + (colWidths[col] - textWidth) / 2;
            cs.beginText();
            cs.newLineAtOffset(textX, currentY - rowHeight + (rowHeight - fontSize) / 2 + 2);
            cs.showText(text);
            cs.endText();

            currentX += colWidths[col];
        }
        currentY -= rowHeight;

        // ---- 2. 绘制数据行 ----
        if (data != null && !data.isEmpty()) {
            int rowIdx = 1; // 数据行从第1行开始
            for (Object item : data) {
                String[] rowData = mapper.map(rowIdx, item);
                currentX = x;

                for (int col = 0; col < Math.min(rowData.length, colCount); col++) {
                    if (isCellMerged(rowIdx, col, merges)) {
                        continue;
                    }
                    MergeCell merge = getMergeCell(rowIdx, col, merges);
                    if (merge != null) {
                        float mergeWidth = 0;
                        for (int i = col; i < col + merge.colSpan && i < colCount; i++) {
                            mergeWidth += colWidths[i];
                        }
                        float mergeHeight = rowHeight * merge.rowSpan;

                        cs.addRect(currentX, currentY - mergeHeight, mergeWidth, mergeHeight);
                        cs.stroke();

                        String text = merge.text != null ? merge.text : "";
                        float textWidth = font.getStringWidth(text) / 1000f * fontSize;
                        float textX = currentX + (mergeWidth - textWidth) / 2;
                        cs.beginText();
                        cs.newLineAtOffset(textX, currentY - mergeHeight + (mergeHeight - fontSize) / 2 + 2);
                        cs.showText(text);
                        cs.endText();

                        currentX += mergeWidth;
                        col += merge.colSpan - 1;
                        continue;
                    }

                    // 普通数据单元格
                    cs.addRect(currentX, currentY - rowHeight, colWidths[col], rowHeight);
                    cs.stroke();

                    String text = rowData[col] != null ? rowData[col] : "";
                    float textWidth = font.getStringWidth(text) / 1000f * fontSize;
                    float textX = currentX + (colWidths[col] - textWidth) / 2;
                    cs.beginText();
                    cs.newLineAtOffset(textX, currentY - rowHeight + (rowHeight - fontSize) / 2 + 2);
                    cs.showText(text);
                    cs.endText();

                    currentX += colWidths[col];
                }

                currentY -= rowHeight;
                rowIdx++;
            }
        }

        // 恢复边框宽度为默认
        cs.setLineWidth(1.0f);
        log.debug("表格绘制完成，底部 Y: {}", currentY);
        return currentY;
    }

    /**
     * 判断某个单元格是否被合并单元格覆盖（即被其他合并单元格包含）
     *
     * @param row    行索引
     * @param col    列索引
     * @param merges 合并单元格列表
     * @return true=被合并覆盖，false=未被覆盖
     */
    private static boolean isCellMerged(int row, int col, List<MergeCell> merges) {
        if (merges == null) {
            return false;
        }
        for (MergeCell m : merges) {
            if (row >= m.row && row < m.row + m.rowSpan &&
                    col >= m.col && col < m.col + m.colSpan &&
                    !(row == m.row && col == m.col)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取某个单元格的合并信息（如果是合并起始单元格）
     *
     * @param row    行索引
     * @param col    列索引
     * @param merges 合并单元格列表
     * @return 合并信息，如果不是合并起始单元格则返回 null
     */
    private static MergeCell getMergeCell(int row, int col, List<MergeCell> merges) {
        if (merges == null) {
            return null;
        }
        for (MergeCell m : merges) {
            if (row == m.row && col == m.col) {
                return m;
            }
        }
        return null;
    }

    // ================================================================
    // 分页绘制辅助（表头/数据行/合计行）
    // ================================================================

    /**
     * 绘制表头（单行，居中，带边框）
     *
     * @param cs        内容流
     * @param font      字体对象
     * @param headers   表头数组
     * @param colWidths 列宽数组
     * @param x         起始 X 坐标
     * @param y         起始 Y 坐标（表头顶部）
     * @param rowHeight 行高
     * @param fontSize  字号
     * @return 表头底部 Y 坐标
     * @throws IOException 绘制失败
     */
    public static float drawTableHeader(PDPageContentStream cs, PDType0Font font, String[] headers,
                                        float[] colWidths, float x, float y, float rowHeight, float fontSize) throws IOException {
        if (headers == null || colWidths == null || headers.length != colWidths.length) {
            log.warn("表头或列宽为空，跳过表头绘制");
            return y;
        }

        log.trace("绘制表头，列数: {}", headers.length);
        cs.setFont(font, fontSize);
        float currentX = x;
        for (int i = 0; i < headers.length; i++) {
            // 边框
            cs.addRect(currentX, y - rowHeight, colWidths[i], rowHeight);
            cs.stroke();
            // 文本居中
            String text = headers[i] != null ? headers[i] : "";
            float textWidth = font.getStringWidth(text) / 1000f * fontSize;
            float textX = currentX + (colWidths[i] - textWidth) / 2;
            cs.beginText();
            cs.newLineAtOffset(textX, y - rowHeight + (rowHeight - fontSize) / 2 + 2);
            cs.showText(text);
            cs.endText();
            currentX += colWidths[i];
        }
        return y - rowHeight;
    }

    /**
     * 绘制单行数据（居中，带边框）
     *
     * @param cs        内容流
     * @param font      字体对象
     * @param rowData   行数据数组
     * @param colWidths 列宽数组
     * @param x         起始 X 坐标
     * @param y         起始 Y 坐标
     * @param rowHeight 行高
     * @param fontSize  字号
     * @return 数据行底部 Y 坐标
     * @throws IOException 绘制失败
     */
    public static float drawDataRow(PDPageContentStream cs, PDType0Font font, String[] rowData,
                                    float[] colWidths, float x, float y, float rowHeight, float fontSize) throws IOException {
        float currentX = x;
        for (int i = 0; i < Math.min(rowData.length, colWidths.length); i++) {
            cs.addRect(currentX, y - rowHeight, colWidths[i], rowHeight);
            cs.stroke();
            String text = rowData[i] != null ? rowData[i] : "";
            float textWidth = font.getStringWidth(text) / 1000f * fontSize;
            float textX = currentX + (colWidths[i] - textWidth) / 2;
            cs.beginText();
            cs.newLineAtOffset(textX, y - rowHeight + (rowHeight - fontSize) / 2 + 2);
            cs.showText(text);
            cs.endText();
            currentX += colWidths[i];
        }
        return y - rowHeight;
    }

    /**
     * 绘制合计行（居中，带边框）
     *
     * @param cs        内容流
     * @param font      字体对象
     * @param totalRow  合计行数据数组
     * @param colWidths 列宽数组
     * @param x         起始 X 坐标
     * @param y         起始 Y 坐标
     * @param rowHeight 行高
     * @param fontSize  字号
     * @return 合计行底部 Y 坐标
     * @throws IOException 绘制失败
     */
    public static float drawTotalRow(PDPageContentStream cs, PDType0Font font, String[] totalRow,
                                     float[] colWidths, float x, float y, float rowHeight, float fontSize) throws IOException {
        float currentX = x;
        for (int i = 0; i < Math.min(totalRow.length, colWidths.length); i++) {
            cs.addRect(currentX, y - rowHeight, colWidths[i], rowHeight);
            cs.stroke();
            String text = totalRow[i] != null ? totalRow[i] : "";
            float textWidth = font.getStringWidth(text) / 1000f * fontSize;
            float textX = currentX + (colWidths[i] - textWidth) / 2;
            cs.beginText();
            cs.newLineAtOffset(textX, y - rowHeight + (rowHeight - fontSize) / 2 + 2);
            cs.showText(text);
            cs.endText();
            currentX += colWidths[i];
        }
        return y - rowHeight;
    }

    // ================================================================
    // 分页计算辅助
    // ================================================================

    /**
     * <p><b>注意：</b>此方法基于当前 Y 坐标和剩余空间计算，适用于已在页面上绘制了部分内容的情况。</p>
     * <p>如需计算整页可容纳的行数，请使用 {@link #calculateRowsPerPage(float, float, float, float, float, float)}</p>
     *
     * @param startY       表格起始 Y 坐标（表头顶部 Y）
     * @param rowHeight    每行高度
     * @param headerHeight 表头高度（通常与行高一致）
     * @param bottomMargin 底部预留边距
     * @return 最大行数（至少为 0）
     */
    public static int calculateMaxRows(float startY, float rowHeight, float headerHeight, float bottomMargin) {
        float available = startY - bottomMargin - headerHeight;
        if (available <= 0) {
            return 0;
        }
        return (int) (available / rowHeight);
    }

    /**
     * 计算每页可容纳的数据行数（基于整页高度计算）
     * <p><b>适用场景：</b>在开始绘制页面内容前，预先计算整页可容纳的行数。</p>
     * <p><b>与 {@link #calculateMaxRows} 的区别：</b></p>
     * <ul>
     *   <li>{@code calculateMaxRows}：基于当前已绘制的剩余空间计算</li>
     *   <li>{@code calculateRowsPerPage}：基于整页高度从头开始计算</li>
     * </ul>
     *
     * @param pageHeight        页面高度（点）
     * @param margin            上下边距（点）
     * @param topHeight         顶部占位高度（标题+头部信息）
     * @param tableHeaderHeight 表头行高度（点）
     * @param bottomHeight      底部占位高度（汇总行+签字）
     * @param rowHeight         每行数据的高度（点）
     * @return 每页最大行数（至少为 1）
     */
    public static int calculateRowsPerPage(float pageHeight, float margin,
                                           float topHeight, float tableHeaderHeight,
                                           float bottomHeight, float rowHeight) {
        float available = pageHeight - 2 * margin - topHeight - tableHeaderHeight - bottomHeight;
        if (available <= 0) {
            return 1;
        }
        int rows = (int) (available / rowHeight);
        return Math.max(1, rows);
    }

    /**
     * 计算总页数
     *
     * @param totalRows   总行数
     * @param rowsPerPage 每页行数
     * @return 总页数（至少为 1）
     */
    public static int getTotalPages(int totalRows, int rowsPerPage) {
        if (rowsPerPage <= 0) {
            return 1;
        }
        return (int) Math.ceil((double) totalRows / rowsPerPage);
    }

    /**
     * 获取当前页需要绘制的数据子列表
     *
     * @param allData     完整数据列表
     * @param pageIndex   页码（从 0 开始）
     * @param rowsPerPage 每页行数
     * @param <T>         数据类型
     * @return 当前页的数据子列表
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
        return allData.subList(start, end);
    }

    /**
     * 检查是否还有更多数据需要绘制
     *
     * @param totalRows   总行数
     * @param pageIndex   当前页码（从 0 开始）
     * @param rowsPerPage 每页行数
     * @return true=还有更多数据
     */
    public static boolean hasMoreData(int totalRows, int pageIndex, int rowsPerPage) {
        return (pageIndex + 1) * rowsPerPage < totalRows;
    }

    // ================================================================
    // 响应输出（Web 下载）
    // ================================================================

    /**
     * 将 PDF 字节数组写入 HttpServletResponse（Web 下载通用方法）
     *
     * <p>自动设置 Content-Type、Content-Disposition 和 Content-Length</p>
     *
     * @param response HTTP 响应对象
     * @param pdfBytes PDF 字节数组
     * @param fileName 下载文件名（不含路径，含后缀 .pdf）
     * @throws IOException IO 异常
     */
    public static void writePdfResponse(HttpServletResponse response, byte[] pdfBytes, String fileName) throws IOException {
        log.info("📄 下载 PDF: {}, 大小: {} bytes", fileName, pdfBytes != null ? pdfBytes.length : 0);
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition",
                "attachment;filename=" + URLEncoder.encode(fileName, "UTF-8"));
        response.setContentLength(pdfBytes.length);
        try (OutputStream os = response.getOutputStream()) {
            os.write(pdfBytes);
            os.flush();
        }
        log.info("✅ PDF 下载完成: {}", fileName);
    }

    // ================================================================
    // ===== 新增功能 1：文本自动换行 =====
    // ================================================================

    /**
     * 水平对齐方式
     */
    public enum TextAlignment {
        /**
         * 左对齐
         */
        LEFT,
        /**
         * 居中对齐
         */
        CENTER,
        /**
         * 右对齐
         */
        RIGHT
    }

    /**
     * 垂直对齐方式
     */
    public enum VerticalAlignment {
        /**
         * 顶部对齐
         */
        TOP,
        /**
         * 居中对齐
         */
        MIDDLE,
        /**
         * 底部对齐
         */
        BOTTOM
    }

    /**
     * 在指定矩形区域内绘制自动换行文本
     * <p>支持水平对齐（左/中/右）和垂直对齐（上/中/下），支持自定义内边距和文字颜色</p>
     *
     * @param cs         内容流
     * @param font       字体对象
     * @param text       文本内容
     * @param x          矩形区域左上角 X 坐标
     * @param y          矩形区域左上角 Y 坐标
     * @param width      矩形区域宽度
     * @param height     矩形区域高度
     * @param fontSize   字号
     * @param lineHeight 行高
     * @param padding    内边距（四边统一）
     * @param hAlign     水平对齐方式
     * @param vAlign     垂直对齐方式
     * @param textColorR 文字颜色 R 分量（0-1）
     * @param textColorG 文字颜色 G 分量（0-1）
     * @param textColorB 文字颜色 B 分量（0-1）
     * @return 实际占用的行数
     * @throws IOException 绘制失败
     */
    public static int drawWrappedText(PDPageContentStream cs, PDType0Font font, String text,
                                      float x, float y, float width, float height,
                                      float fontSize, float lineHeight, float padding,
                                      TextAlignment hAlign, VerticalAlignment vAlign,
                                      float textColorR, float textColorG, float textColorB) throws IOException {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        log.trace("绘制换行文本: {}, 宽度: {}, 高度: {}", text.length() > 20 ? text.substring(0, 20) + "..." : text, width, height);

        // 1. 计算可用宽度（减去左右内边距）
        float availableWidth = width - 2 * padding;
        if (availableWidth <= 0) {
            log.warn("可用宽度 <= 0，无法绘制文本");
            return 0;
        }

        // 2. 按宽度拆分文本
        List<String> lines = splitTextByWidth(font, text, availableWidth, fontSize);
        if (lines.isEmpty()) {
            return 0;
        }

        // 3. 计算总文本高度
        float totalTextHeight = lines.size() * lineHeight;
        float availableHeight = height - 2 * padding;

        // 4. 计算垂直起始位置
        float startY;
        switch (vAlign) {
            case TOP:
                startY = y - padding - lineHeight;
                break;
            case BOTTOM:
                startY = y - height + padding + totalTextHeight;
                break;
            case MIDDLE:
            default:
                startY = y - (height - totalTextHeight) / 2;
                break;
        }

        // 5. 设置文字颜色（使用 try-finally 确保恢复）
        cs.setNonStrokingColor(textColorR, textColorG, textColorB);

        try {
            // 6. 逐行绘制
            cs.setFont(font, fontSize);
            float currentY = startY;
            for (String line : lines) {
                if (currentY < y - height + padding) {
                    break; // 超出底部，停止绘制
                }
                float textWidth = font.getStringWidth(line) / 1000f * fontSize;
                float textX;
                switch (hAlign) {
                    case LEFT:
                        textX = x + padding;
                        break;
                    case RIGHT:
                        textX = x + width - padding - textWidth;
                        break;
                    case CENTER:
                    default:
                        textX = x + (width - textWidth) / 2;
                        break;
                }
                cs.beginText();
                cs.newLineAtOffset(textX, currentY);
                cs.showText(line);
                cs.endText();
                currentY -= lineHeight;
            }
        } finally {
            // ✅ 确保颜色恢复
            cs.setNonStrokingColor(0, 0, 0);
        }

        log.trace("换行文本绘制完成，共 {} 行", lines.size());
        return lines.size();
    }

    /**
     * 将文本按指定宽度拆分为多行（优先按空格，否则按字符截断）
     * <p><b>截断策略：</b></p>
     * <ul>
     *   <li>优先按空格分割，保持单词完整</li>
     *   <li>如果单个单词宽度超过最大宽度，则按字符截断</li>
     *   <li>使用二分递增方式确定每段最大可容纳字符数，确保不产生死循环</li>
     * </ul>
     *
     * @param font     字体对象
     * @param text     原始文本
     * @param maxWidth 最大宽度（点）
     * @param fontSize 字号
     * @return 行列表
     * @throws IOException 计算失败
     */
    private static List<String> splitTextByWidth(PDType0Font font, String text, float maxWidth, float fontSize) throws IOException {
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
                    line.append(" ");
                }
                line.append(word);
            } else {
                if (line.length() > 0) {
                    result.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    // ✅ 单个单词太长（如 "ABCDEFGHIJKLMNOPQRSTUVWXYZ"），按字符截断
                    // 使用二分递增方式找出每段最大可容纳字符数，确保不产生死循环
                    String remaining = word;
                    while (!remaining.isEmpty()) {
                        int cut = 1;
                        while (cut < remaining.length() &&
                                font.getStringWidth(remaining.substring(0, cut)) / 1000f * fontSize <= maxWidth) {
                            cut++;
                        }
                        // ✅ 安全处理：确保至少截取 1 个字符
                        if (cut > remaining.length()) {
                            cut = remaining.length();
                        }
                        if (cut <= 0) {
                            cut = 1;
                        }
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

    /**
     * 计算文本按宽度拆分后的行数
     *
     * @param font     字体对象
     * @param text     原始文本
     * @param maxWidth 最大宽度（点）
     * @param fontSize 字号
     * @return 行数
     * @throws IOException 计算失败
     */
    public static int calculateTextLines(PDType0Font font, String text, float maxWidth, float fontSize) throws IOException {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return splitTextByWidth(font, text, maxWidth, fontSize).size();
    }

    // ================================================================
    // ===== 新增功能 2：增强表格绘制（支持换行/对齐/内边距/颜色） =====
    // ================================================================

    /**
     * 表格样式配置类
     * <p>用于 {@link #drawTableAdvanced} 方法，控制表格的排版样式</p>
     */
    public static class TableStyle {
        /**
         * 字体大小（默认 10）
         */
        public float fontSize = 10f;
        /**
         * 行高（最小行高，默认 22）
         */
        public float rowHeight = 22f;
        /**
         * 内边距（默认 3）
         */
        public float padding = 3f;
        /**
         * 边框宽度（默认 1.0）
         */
        public float borderWidth = 1f;

        /**
         * 表头水平对齐（默认居中）
         */
        public TextAlignment headerHAlign = TextAlignment.CENTER;
        /**
         * 表头垂直对齐（默认居中）
         */
        public VerticalAlignment headerVAlign = VerticalAlignment.MIDDLE;
        /**
         * 是否启用表头背景色（默认 true）
         */
        public boolean headerBackground = true;
        /**
         * 表头背景色 R 分量（默认 0.92 浅灰）
         */
        public float headerBgR = 0.92f;
        /**
         * 表头背景色 G 分量（默认 0.92 浅灰）
         */
        public float headerBgG = 0.92f;
        /**
         * 表头背景色 B 分量（默认 0.92 浅灰）
         */
        public float headerBgB = 0.92f;

        /**
         * 数据行水平对齐（默认居中）
         */
        public TextAlignment dataHAlign = TextAlignment.CENTER;
        /**
         * 数据行垂直对齐（默认居中）
         */
        public VerticalAlignment dataVAlign = VerticalAlignment.MIDDLE;
        /**
         * 数据行文字颜色 R 分量（默认 0 黑色）
         */
        public float dataTextColorR = 0f;
        /**
         * 数据行文字颜色 G 分量（默认 0 黑色）
         */
        public float dataTextColorG = 0f;
        /**
         * 数据行文字颜色 B 分量（默认 0 黑色）
         */
        public float dataTextColorB = 0f;

        /**
         * 是否自动换行（默认 true）
         */
        public boolean wrapText = true;
        /**
         * 边框颜色 R 分量（默认 0 黑色）
         */
        public float borderR = 0f;
        /**
         * 边框颜色 G 分量（默认 0 黑色）
         */
        public float borderG = 0f;
        /**
         * 边框颜色 B 分量（默认 0 黑色）
         */
        public float borderB = 0f;

        /**
         * 列级水平对齐方式（数组长度应与列数一致，null 表示使用默认值）
         * <p>如果某列为 null，则使用 dataHAlign</p>
         */
        public TextAlignment[] columnAligns;

        /**
         * 创建默认样式
         *
         * @return 默认样式实例
         */
        public static TableStyle defaultStyle() {
            return new TableStyle();
        }

        /**
         * 创建表头有背景色的样式（适合报表）
         *
         * @return 报表样式实例
         */
        public static TableStyle reportStyle() {
            TableStyle style = new TableStyle();
            style.headerBackground = true;
            style.wrapText = true;
            style.padding = 4f;
            return style;
        }

        /**
         * 创建 Builder 进行链式配置
         *
         * @return Builder 实例
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * TableStyle Builder 模式（链式调用）
         */
        public static class Builder {
            private final TableStyle style = new TableStyle();

            public Builder fontSize(float fontSize) {
                style.fontSize = fontSize;
                return this;
            }

            public Builder rowHeight(float rowHeight) {
                style.rowHeight = rowHeight;
                return this;
            }

            public Builder padding(float padding) {
                style.padding = padding;
                return this;
            }

            public Builder borderWidth(float borderWidth) {
                style.borderWidth = borderWidth;
                return this;
            }

            public Builder headerHAlign(TextAlignment align) {
                style.headerHAlign = align;
                return this;
            }

            public Builder headerVAlign(VerticalAlignment align) {
                style.headerVAlign = align;
                return this;
            }

            public Builder headerBackground(boolean enabled) {
                style.headerBackground = enabled;
                return this;
            }

            public Builder headerBgColor(float r, float g, float b) {
                style.headerBgR = r;
                style.headerBgG = g;
                style.headerBgB = b;
                return this;
            }

            public Builder dataHAlign(TextAlignment align) {
                style.dataHAlign = align;
                return this;
            }

            public Builder dataVAlign(VerticalAlignment align) {
                style.dataVAlign = align;
                return this;
            }

            public Builder dataTextColor(float r, float g, float b) {
                style.dataTextColorR = r;
                style.dataTextColorG = g;
                style.dataTextColorB = b;
                return this;
            }

            public Builder wrapText(boolean enabled) {
                style.wrapText = enabled;
                return this;
            }

            public Builder borderColor(float r, float g, float b) {
                style.borderR = r;
                style.borderG = g;
                style.borderB = b;
                return this;
            }

            public Builder columnAligns(TextAlignment... aligns) {
                style.columnAligns = aligns;
                return this;
            }

            public TableStyle build() {
                return style;
            }
        }
    }

    /**
     * 绘制增强表格（支持自动换行、对齐、内边距、颜色）
     * <p>表头和数据行均支持独立样式配置，行高根据内容自动调整</p>
     *
     * @param cs        内容流
     * @param font      字体对象
     * @param headers   表头数组
     * @param colWidths 列宽数组
     * @param data      数据列表
     * @param x         表格左上角 X 坐标
     * @param y         表格左上角 Y 坐标（表头顶部）
     * @param mapper    行映射器
     * @param style     样式配置（若为 null，使用默认样式）
     * @return 表格底部 Y 坐标
     * @throws IOException 绘制失败
     */
    public static float drawTableAdvanced(PDPageContentStream cs, PDType0Font font,
                                          String[] headers, float[] colWidths,
                                          List<?> data, float x, float y,
                                          RowMapper mapper, TableStyle style) throws IOException {
        if (headers == null || headers.length == 0 || colWidths == null || colWidths.length == 0) {
            log.warn("表头或列宽为空，跳过增强表格绘制");
            return y;
        }
        if (headers.length != colWidths.length) {
            throw new IllegalArgumentException("表头数量与列宽数量不一致");
        }

        // 使用默认样式
        if (style == null) {
            style = TableStyle.defaultStyle();
        }

        int colCount = headers.length;
        float rowHeight = style.rowHeight;
        float fontSize = style.fontSize;
        float padding = style.padding;
        float borderWidth = style.borderWidth;

        log.debug("开始绘制增强表格，列数: {}, 数据行数: {}, 自动换行: {}",
                colCount, data != null ? data.size() : 0, style.wrapText);

        // 设置边框颜色和宽度
        cs.setNonStrokingColor(style.borderR, style.borderG, style.borderB);
        cs.setLineWidth(borderWidth);

        float currentY = y;

        // ✅ 优化：预先计算每列的起始 X 偏移，避免重复调用 getColOffset
        float[] colStartX = new float[colCount];
        for (int col = 0; col < colCount; col++) {
            float offset = 0;
            for (int i = 0; i < col; i++) {
                offset += colWidths[i];
            }
            colStartX[col] = x + offset;
        }

        // ---- 1. 计算表头实际高度（如果启用换行） ----
        float headerRowHeight = rowHeight;
        if (style.wrapText) {
            int maxHeaderLines = 1;
            for (int col = 0; col < colCount; col++) {
                String headerText = headers[col] != null ? headers[col] : "";
                int lines = calculateTextLines(font, headerText, colWidths[col] - 2 * padding, fontSize);
                maxHeaderLines = Math.max(maxHeaderLines, lines);
            }
            headerRowHeight = Math.max(rowHeight, maxHeaderLines * rowHeight);
        }

        // ---- 2. 绘制表头 ----
        for (int col = 0; col < colCount; col++) {
            float currentX = colStartX[col];
            float cellWidth = colWidths[col];

            // 表头背景色
            if (style.headerBackground) {
                cs.setNonStrokingColor(style.headerBgR, style.headerBgG, style.headerBgB);
                cs.addRect(currentX, currentY - headerRowHeight, cellWidth, headerRowHeight);
                cs.fill();
                cs.setNonStrokingColor(style.borderR, style.borderG, style.borderB);
            }

            // 边框
            cs.addRect(currentX, currentY - headerRowHeight, cellWidth, headerRowHeight);
            cs.stroke();

            // 绘制表头文本
            String headerText = headers[col] != null ? headers[col] : "";
            if (style.wrapText) {
                drawWrappedText(cs, font, headerText,
                        currentX, currentY, cellWidth, headerRowHeight,
                        fontSize, rowHeight, padding,
                        style.headerHAlign, style.headerVAlign,
                        0, 0, 0); // 黑色文字
            } else {
                // 简单绘制（单行居中）
                float textWidth = font.getStringWidth(headerText) / 1000f * fontSize;
                float textX = currentX + (cellWidth - textWidth) / 2;
                cs.beginText();
                cs.newLineAtOffset(textX, currentY - headerRowHeight + (headerRowHeight - fontSize) / 2 + 2);
                cs.showText(headerText);
                cs.endText();
            }
        }
        currentY -= headerRowHeight;

        // ---- 3. 绘制数据行 ----
        if (data != null && !data.isEmpty()) {
            int rowIdx = 1;
            for (Object item : data) {
                String[] rowData = mapper.map(rowIdx, item);

                // ---- 3.1 计算当前行实际高度 ----
                float actualRowHeight = rowHeight;
                if (style.wrapText) {
                    int maxLines = 1;
                    for (int col = 0; col < Math.min(rowData.length, colCount); col++) {
                        String cellText = rowData[col] != null ? rowData[col] : "";
                        int lines = calculateTextLines(font, cellText, colWidths[col] - 2 * padding, fontSize);
                        maxLines = Math.max(maxLines, lines);
                    }
                    actualRowHeight = Math.max(rowHeight, maxLines * rowHeight);
                }

                // ---- 3.2 绘制该行的每个单元格 ----
                for (int col = 0; col < Math.min(rowData.length, colCount); col++) {
                    float currentX = colStartX[col];
                    float cellWidth = colWidths[col];

                    // 边框
                    cs.addRect(currentX, currentY - actualRowHeight, cellWidth, actualRowHeight);
                    cs.stroke();

                    // 获取列级对齐方式
                    TextAlignment colHAlign = style.dataHAlign;
                    if (style.columnAligns != null && col < style.columnAligns.length && style.columnAligns[col] != null) {
                        colHAlign = style.columnAligns[col];
                    }

                    String cellText = rowData[col] != null ? rowData[col] : "";
                    if (style.wrapText) {
                        drawWrappedText(cs, font, cellText,
                                currentX, currentY, cellWidth, actualRowHeight,
                                fontSize, rowHeight, padding,
                                colHAlign, style.dataVAlign,
                                style.dataTextColorR, style.dataTextColorG, style.dataTextColorB);
                    } else {
                        // 简单绘制（单行居中）
                        float textWidth = font.getStringWidth(cellText) / 1000f * fontSize;
                        float textX = currentX + (cellWidth - textWidth) / 2;
                        cs.beginText();
                        cs.newLineAtOffset(textX, currentY - actualRowHeight + (actualRowHeight - fontSize) / 2 + 2);
                        cs.showText(cellText);
                        cs.endText();
                    }
                }
                currentY -= actualRowHeight;
                rowIdx++;
            }
        }

        // 恢复边框颜色和宽度为默认
        cs.setNonStrokingColor(0, 0, 0);
        cs.setLineWidth(1.0f);

        log.debug("增强表格绘制完成，底部 Y: {}", currentY);
        return currentY;
    }

    // ================================================================
    // ===== 新增功能 3：自动分页绘制（带表头重复） =====
    // ================================================================

    /**
     * 自动分页绘制表格（每页重复表头）
     * <p>自动计算每页可容纳的行数，创建多个页面，每个页面包含表头</p>
     *
     * @param doc          PDF 文档
     * @param font         字体对象
     * @param headers      表头数组
     * @param colWidths    列宽数组
     * @param allData      所有数据
     * @param pageWidth    页面宽度
     * @param margin       页面边距
     * @param topHeight    顶部占位高度（标题+头部信息，仅第一页）
     * @param bottomHeight 底部占位高度（汇总行+签字，仅最后一页）
     * @param style        表格样式
     * @param mapper       行映射器
     * @param pageSize     页面尺寸
     * @return 生成的页数
     * @throws IOException 绘制失败
     */
    public static int drawTableWithPagination(PDDocument doc, PDType0Font font,
                                              String[] headers, float[] colWidths,
                                              List<?> allData, float pageWidth,
                                              float margin, float topHeight, float bottomHeight,
                                              TableStyle style, RowMapper mapper,
                                              PDRectangle pageSize) throws IOException {
        if (allData == null || allData.isEmpty()) {
            log.warn("数据为空，跳过分页绘制");
            return 0;
        }

        if (style == null) {
            style = TableStyle.defaultStyle();
        }

        int totalRows = allData.size();
        float pageHeight = pageSize.getHeight();

        // 计算每页可容纳的行数（减去表头行本身）
        float tableHeaderHeight = style.rowHeight;
        if (style.wrapText) {
            int maxHeaderLines = 1;
            for (int col = 0; col < headers.length; col++) {
                String headerText = headers[col] != null ? headers[col] : "";
                int lines = calculateTextLines(font, headerText, colWidths[col] - 2 * style.padding, style.fontSize);
                maxHeaderLines = Math.max(maxHeaderLines, lines);
            }
            tableHeaderHeight = Math.max(style.rowHeight, maxHeaderLines * style.rowHeight);
        }

        // 可用高度 = 页面高度 - 上下边距 - 顶部占位 - 底部占位 - 表头高度
        float availableHeight = pageHeight - 2 * margin - topHeight - bottomHeight - tableHeaderHeight;
        int rowsPerPage = (int) (availableHeight / style.rowHeight);
        if (rowsPerPage < 1) {
            rowsPerPage = 1;
            log.warn("每页行数小于1，强制设为1");
        }

        int totalPages = (int) Math.ceil((double) totalRows / rowsPerPage);
        log.info("📄 分页绘制：总行数={}, 每页行数={}, 总页数={}", totalRows, rowsPerPage, totalPages);

        int pageIndex = 0;
        int startRow = 0;

        while (startRow < totalRows) {
            int endRow = Math.min(startRow + rowsPerPage, totalRows);
            List<?> pageData = allData.subList(startRow, endRow);

            // 创建新页面
            PDPage page = new PDPage(pageSize);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = page.getMediaBox().getHeight() - margin;

                // 第一页的顶部占位由调用方自行处理（标题、头部信息）
                // 工具类只负责表格部分，顶部和底部由调用方控制

                // 绘制表头（每页重复）
                y = drawTableHeader(cs, font, headers, colWidths, margin, y, style.rowHeight, style.fontSize);

                // 绘制数据行
                int rowNum = startRow + 1;
                for (Object item : pageData) {
                    String[] rowData = mapper.map(rowNum, item);
                    y = drawDataRow(cs, font, rowData, colWidths, margin, y, style.rowHeight, style.fontSize);
                    rowNum++;
                }

                // 最后一页的底部占位（汇总行、签字）由调用方自行处理

                // 页码
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

        log.info("✅ 分页绘制完成，共 {} 页", totalPages);
        return totalPages;
    }

    /**
     * 自动分页绘制表格（简化版，使用默认样式）
     *
     * @param doc          PDF 文档
     * @param font         字体对象
     * @param headers      表头数组
     * @param colWidths    列宽数组
     * @param allData      所有数据
     * @param pageWidth    页面宽度
     * @param margin       页面边距
     * @param topHeight    顶部占位高度
     * @param bottomHeight 底部占位高度
     * @param mapper       行映射器
     * @param pageSize     页面尺寸
     * @return 生成的页数
     * @throws IOException 绘制失败
     */
    public static int drawTableWithPagination(PDDocument doc, PDType0Font font,
                                              String[] headers, float[] colWidths,
                                              List<?> allData, float pageWidth,
                                              float margin, float topHeight, float bottomHeight,
                                              RowMapper mapper, PDRectangle pageSize) throws IOException {
        return drawTableWithPagination(doc, font, headers, colWidths, allData,
                pageWidth, margin, topHeight, bottomHeight,
                TableStyle.defaultStyle(), mapper, pageSize);
    }

    // ================================================================
    // 内部类：合并单元格配置
    // ================================================================

    /**
     * 合并单元格配置类
     * <p>用于 {@link #drawTable(PDPageContentStream, PDType0Font, String[], float[], List, float, float, RowMapper, float, float, boolean, float, List)} 中指定合并区域</p>
     */
    public static class MergeCell {
        /**
         * 起始行索引（从 0 开始，表头行算第 0 行，数据行从 1 开始）
         */
        public final int row;
        /**
         * 起始列索引（从 0 开始）
         */
        public final int col;
        /**
         * 合并的行数（至少为 1）
         */
        public final int rowSpan;
        /**
         * 合并的列数（至少为 1）
         */
        public final int colSpan;
        /**
         * 合并单元格显示的文本（若为 null，则从数据中获取或使用空字符串）
         */
        public final String text;

        /**
         * 构造合并单元格
         *
         * @param row     起始行索引（0 基）
         * @param col     起始列索引（0 基）
         * @param rowSpan 合并行数（≥1）
         * @param colSpan 合并列数（≥1）
         * @param text    显示的文本
         */
        public MergeCell(int row, int col, int rowSpan, int colSpan, String text) {
            if (row < 0 || col < 0 || rowSpan < 1 || colSpan < 1) {
                throw new IllegalArgumentException("行、列索引必须 >=0，行/列合并数必须 >=1");
            }
            this.row = row;
            this.col = col;
            this.rowSpan = rowSpan;
            this.colSpan = colSpan;
            this.text = text;
        }
    }

    // ================================================================
    // 函数式接口
    // ================================================================

    /**
     * 表格行映射器函数式接口
     *
     * <p>用于 {@link #drawTable} 方法中，将数据对象转换为字符串数组</p>
     */
    @FunctionalInterface
    public interface RowMapper {
        /**
         * 将数据对象映射为字符串数组（与列顺序对应）
         *
         * <p>返回的字符串数组长度需与表头列数一致</p>
         *
         * @param rowIdx 行号（从 1 开始），可用于生成序号列
         * @param item   数据对象（由调用方传入的业务实体）
         * @return 字符串数组，每个元素对应一列的数据
         */
        String[] map(int rowIdx, Object item);
    }
}

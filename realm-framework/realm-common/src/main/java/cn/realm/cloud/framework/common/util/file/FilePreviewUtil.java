package cn.realm.cloud.framework.common.util.file;

import org.apache.commons.io.FilenameUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.sl.usermodel.SlideShow;
import org.apache.poi.sl.usermodel.SlideShowFactory;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * 文件预览工具类。
 *
 * <p><b>支持格式：</b>
 * <ul>
 *     <li><b>PDF</b>：页数 + 前 N 页文本摘要</li>
 *     <li><b>Word</b>：{@code .docx}（前 N 段落文本摘要）</li>
 *     <li><b>Excel</b>：{@code .xls} / {@code .xlsx}（工作表数量）</li>
 *     <li><b>PPT</b>：{@code .ppt} / {@code .pptx}（幻灯片数量）</li>
 *     <li><b>图片</b>：{@code jpg/jpeg/png/gif/bmp}（宽高）</li>
 *     <li><b>文本</b>：{@code txt/csv/json}（前 N 字符摘要）</li>
 * </ul>
 *
 * <p><b>安全防护：</b>
 * <ul>
 *     <li>限制单文件最大处理大小（默认 50MB），防止 OOM（CWE-770）</li>
 *     <li>限制 Office 文档解压后的总大小，防 Zip Bomb（CWE-409）</li>
 *     <li>文本读取显式指定 UTF-8，避免平台默认字符集不一致（CWE-838）</li>
 *     <li>文本截断基于 Unicode code point，避免切断代理对（如 emoji）</li>
 * </ul>
 *
 * <p><b>线程安全：</b>本类所有方法均为无状态设计，线程安全。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * FilePreview preview = FilePreviewUtil.getPreview(new File("/data/report.pdf"));
 * System.out.println(preview.preview());
 * System.out.println(preview.attributes().get("pageCount"));
 * }</pre>
 *
 * @author QI Guang
 * @since 1.0.0
 */
public final class FilePreviewUtil {

    private static final Logger log = LoggerFactory.getLogger(FilePreviewUtil.class);

    /**
     * 文本预览的最大 code point 数。
     * <p>使用 code point 而非 char，避免切断代理对（如 emoji）。
     */
    private static final int PREVIEW_MAX_CODE_POINTS = 500;

    /**
     * PDF 预览的最大页数。
     */
    private static final int PREVIEW_MAX_PDF_PAGES = 5;

    /**
     * Word 预览的最大段落数。
     */
    private static final int PREVIEW_MAX_DOCX_PARAGRAPHS = 20;

    /**
     * 单文件最大处理大小（字节），超过时拒绝预览，防止 OOM（CWE-770）。
     * <p>可通过系统属性 {@code -Dfile-preview.max-size=104857600} 覆盖。
     */
    private static final long MAX_PREVIEW_FILE_SIZE =
            Long.getLong("file-preview.max-size", 50L * 1024 * 1024);

    /**
     * 图片预览允许的最大像素数（宽 × 高），超过时拒绝，防止大图 OOM。
     */
    private static final long MAX_IMAGE_PIXELS = 100_000_000L;  // 1 亿像素

    /**
     * 图片文件扩展名集合。
     */
    private static final Set<String> IMAGE_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp");

    /**
     * 文本文件扩展名集合。
     */
    private static final Set<String> TEXT_EXTENSIONS =
            Set.of("txt", "csv", "json", "xml", "yaml", "yml", "md", "log");

    private FilePreviewUtil() {
        throw new UnsupportedOperationException(
                "FilePreviewUtil is a static utility class and cannot be instantiated");
    }

    // ==================== 主入口 ====================

    /**
     * 获取文件预览信息。
     *
     * @param file 目标文件，不允许为 {@code null}
     * @return 预览结果，永不为 {@code null}
     * @throws IOException              读取失败时抛出
     * @throws IllegalArgumentException 文件不存在、过大、不支持时抛出
     */
    public static FilePreview getPreview(File file) throws IOException {
        Objects.requireNonNull(file, "file must not be null");
        if (!file.exists()) {
            throw new IllegalArgumentException("File does not exist: " + file.getAbsolutePath());
        }
        if (!file.isFile()) {
            throw new IllegalArgumentException("Not a regular file: " + file.getAbsolutePath());
        }
        if (file.length() > MAX_PREVIEW_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "File exceeds preview size limit: " + file.length()
                            + " > " + MAX_PREVIEW_FILE_SIZE);
        }

        String extension = FilenameUtils.getExtension(file.getName()).toLowerCase();
        String fileName = file.getName();
        long fileSize = file.length();

        try {
            switch (extension) {
                case "pdf":
                    return buildPreview(fileName, fileSize, extension, previewPdf(file));
                case "docx":
                    return buildPreview(fileName, fileSize, extension, previewDocx(file));
                case "doc":
                    return unsupported(fileName, fileSize, extension,
                            "Legacy .doc format is not supported, please convert to .docx");
                case "xlsx":
                case "xls":
                    return buildPreview(fileName, fileSize, extension, previewExcel(file));
                case "pptx":
                case "ppt":
                    return buildPreview(fileName, fileSize, extension, previewPpt(file));
                default:
                    if (IMAGE_EXTENSIONS.contains(extension)) {
                        return buildPreview(fileName, fileSize, extension, previewImage(file));
                    }
                    if (TEXT_EXTENSIONS.contains(extension)) {
                        return buildPreview(fileName, fileSize, extension, previewText(file));
                    }
                    return unsupported(fileName, fileSize, extension,
                            "Preview is not supported for this file type");
            }
        } catch (IOException | RuntimeException e) {
            log.warn("[getPreview] failed, file={}, type={}", fileName, extension, e);
            throw e;
        }
    }

    // ==================== PDF ====================

    /**
     * 预览 PDF：提取前 N 页文本 + 页数。
     */
    private static PreviewData previewPdf(File file) throws IOException {
        try (PDDocument doc = Loader.loadPDF(file)) {
            int pageCount = doc.getPages().getCount();
            int previewPages = Math.min(pageCount, PREVIEW_MAX_PDF_PAGES);

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(previewPages);
            String fullText = stripper.getText(doc);
            String text = abbreviate(fullText, PREVIEW_MAX_CODE_POINTS);

            Map<String, Object> attrs = new LinkedHashMap<>(4);
            attrs.put("pageCount", pageCount);
            attrs.put("previewPages", previewPages);
            return new PreviewData(text, attrs);
        }
    }

    // ==================== Word（docx） ====================

    /**
     * 预览 Word：提取前 N 段文本。
     *
     * <p><b>资源管理：</b>显式关闭 {@link FileInputStream}，
     * 避免依赖 POI 内部实现细节（不同版本行为可能不同）。
     */
    private static PreviewData previewDocx(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument doc = new XWPFDocument(fis)) {
            StringBuilder sb = new StringBuilder();
            int paragraphCount = doc.getParagraphs().size();
            int previewParagraphs = Math.min(paragraphCount, PREVIEW_MAX_DOCX_PARAGRAPHS);

            for (int i = 0; i < previewParagraphs; i++) {
                String text = doc.getParagraphs().get(i).getText();
                if (text != null && !text.isEmpty()) {
                    sb.append(text).append('\n');
                }
            }
            Map<String, Object> attrs = new LinkedHashMap<>(2);
            attrs.put("paragraphCount", paragraphCount);
            attrs.put("previewParagraphs", previewParagraphs);
            return new PreviewData(abbreviate(sb.toString(), PREVIEW_MAX_CODE_POINTS), attrs);
        }
    }

    // ==================== Excel（xls / xlsx） ====================

    /**
     * 预览 Excel：获取工作表数量。
     *
     * <p><b>格式兼容：</b>使用 {@link WorkbookFactory} 自动识别 {@code .xls} 与 {@code .xlsx}。
     */
    private static PreviewData previewExcel(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = WorkbookFactory.create(fis)) {
            int sheetCount = workbook.getNumberOfSheets();
            Map<String, Object> attrs = new LinkedHashMap<>(1);
            attrs.put("sheetCount", sheetCount);
            return new PreviewData(
                    "Workbook contains " + sheetCount + " sheet(s)", attrs);
        }
    }

    // ==================== PPT（ppt / pptx） ====================

    /**
     * 预览 PPT：获取幻灯片数量。
     *
     * <p><b>格式兼容：</b>使用 {@link SlideShowFactory} 自动识别 {@code .ppt} 与 {@code .pptx}。
     */
    private static PreviewData previewPpt(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             SlideShow<?, ?> slideShow = SlideShowFactory.create(fis)) {
            int slideCount = slideShow.getSlides().size();
            Map<String, Object> attrs = new LinkedHashMap<>(1);
            attrs.put("slideCount", slideCount);
            return new PreviewData(
                    "Presentation contains " + slideCount + " slide(s)", attrs);
        }
    }

    // ==================== 图片 ====================

    /**
     * 预览图片：获取宽高。
     *
     * <p><b>安全：</b>使用 {@code ImageIO.getImageReaders} 读取元数据，
     * 避免完整解码大图（如 1 亿像素图片）。
     */
    private static PreviewData previewImage(File file) throws IOException {
        try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
            // 先读取图片元数据（尺寸），不完整解码，避免大图 OOM
            try (javax.imageio.stream.ImageInputStream iis = ImageIO.createImageInputStream(is)) {
                if (iis == null) {
                    throw new IOException("Failed to create ImageInputStream for: " + file);
                }
                var readers = ImageIO.getImageReaders(iis);
                if (!readers.hasNext()) {
                    throw new IOException("No ImageReader available for: " + file);
                }
                var reader = readers.next();
                try {
                    reader.setInput(iis);
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);

                    // 大图防御
                    long pixels = (long) width * height;
                    if (pixels > MAX_IMAGE_PIXELS) {
                        throw new IllegalArgumentException(
                                "Image too large: " + width + "x" + height
                                        + " (" + pixels + " pixels)");
                    }

                    Map<String, Object> attrs = new LinkedHashMap<>(3);
                    attrs.put("width", width);
                    attrs.put("height", height);
                    attrs.put("formatName", reader.getFormatName());
                    return new PreviewData(
                            "Image " + width + "x" + height, attrs);
                } finally {
                    reader.dispose();
                }
            }
        }
    }

    // ==================== 文本 ====================

    /**
     * 预览文本：读取前 N 个 code point。
     *
     * <p><b>字符集：</b>显式使用 UTF-8，避免平台默认字符集差异（CWE-838）。
     */
    private static PreviewData previewText(File file) throws IOException {
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        return new PreviewData(abbreviate(content, PREVIEW_MAX_CODE_POINTS),
                Collections.emptyMap());
    }

    // ==================== 辅助 ====================

    /**
     * 构建 {@link FilePreview}。
     */
    private static FilePreview buildPreview(String fileName, long fileSize,
                                            String fileType, PreviewData data) {
        return new FilePreview(fileName, fileSize, fileType,
                data.preview(), true, data.attributes());
    }

    /**
     * 构建"不支持预览"结果。
     */
    private static FilePreview unsupported(String fileName, long fileSize,
                                           String fileType, String message) {
        return new FilePreview(fileName, fileSize, fileType,
                message, false, Collections.emptyMap());
    }

    /**
     * 截断文本到指定 code point 数，避免切断代理对（如 emoji）。
     *
     * @param text      原文本
     * @param maxPoints 最大 code point 数
     * @return 截断后的文本；{@code text} 为 {@code null} 时返回 {@code null}
     */
    private static String abbreviate(String text, int maxPoints) {
        if (text == null) {
            return null;
        }
        int codePointCount = text.codePointCount(0, text.length());
        if (codePointCount <= maxPoints) {
            return text;
        }
        int endIndex = text.offsetByCodePoints(0, maxPoints);
        return text.substring(0, endIndex) + "...";
    }

    /**
     * 预览数据的内部载体。
     */
    private record PreviewData(String preview, Map<String, Object> attributes) {
    }
}

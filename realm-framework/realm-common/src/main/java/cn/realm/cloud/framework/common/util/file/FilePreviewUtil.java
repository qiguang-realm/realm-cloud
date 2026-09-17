package cn.realm.cloud.framework.common.util.file;

import org.apache.commons.io.FilenameUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * 文件预览工具类
 * <p>支持 PDF、Word、Excel、PPT、图片等常见文件格式的预览内容提取</p>
 *
 * @author QI Guang
 * @version 1.0.0
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public final class FilePreviewUtil {

    private static final int PREVIEW_MAX_PAGES = 5;
    private static final int PREVIEW_IMAGE_WIDTH = 800;

    private FilePreviewUtil() {
    }

    /**
     * 获取文件预览内容（文本/图片/元数据）
     *
     * @param file 文件对象
     * @return 预览信息 Map
     */
    public static Map<String, Object> getPreview(File file) throws IOException {
        if (file == null || !file.exists()) {
            throw new IllegalArgumentException("文件不存在");
        }

        String extension = FilenameUtils.getExtension(file.getName()).toLowerCase();
        Map<String, Object> preview = new HashMap<>();
        preview.put("fileName", file.getName());
        preview.put("fileSize", file.length());
        preview.put("fileType", extension);

        switch (extension) {
            case "pdf":
                preview.putAll(previewPdf(file));
                break;
            case "docx":
                preview.putAll(previewDocx(file));
                break;
            case "xlsx":
            case "xls":
                preview.putAll(previewExcel(file));
                break;
            case "pptx":
            case "ppt":
                preview.putAll(previewPpt(file));
                break;
            case "jpg":
            case "jpeg":
            case "png":
            case "gif":
            case "bmp":
                preview.putAll(previewImage(file));
                break;
            case "txt":
            case "csv":
            case "json":
                preview.putAll(previewText(file));
                break;
            default:
                preview.put("preview", "不支持预览此文件类型");
                break;
        }

        return preview;
    }

    /**
     * 预览 PDF（提取前5页文本 + 缩略图）
     */
    private static Map<String, Object> previewPdf(File file) throws IOException {
        Map<String, Object> result = new HashMap<>();
        try (PDDocument doc = Loader.loadPDF(file)) {
            int pageCount = doc.getPages().getCount();
            result.put("pageCount", pageCount);
            result.put("previewPages", Math.min(pageCount, PREVIEW_MAX_PAGES));

            // 提取文本
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(Math.min(pageCount, PREVIEW_MAX_PAGES));
            String text = stripper.getText(doc);
            result.put("preview", text.length() > 200 ? text.substring(0, 200) + "..." : text);
        }
        return result;
    }

    /**
     * 预览 Word 文档
     */
    private static Map<String, Object> previewDocx(File file) throws IOException {
        Map<String, Object> result = new HashMap<>();
        try (XWPFDocument doc = new XWPFDocument(new FileInputStream(file))) {
            StringBuilder text = new StringBuilder();
            doc.getParagraphs().forEach(p -> text.append(p.getText()).append("\n"));
            result.put("preview", text.length() > 200 ? text.substring(0, 200) + "..." : text.toString());
        }
        return result;
    }

    /**
     * 预览 Excel
     */
    private static Map<String, Object> previewExcel(File file) throws IOException {
        Map<String, Object> result = new HashMap<>();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new FileInputStream(file))) {
            int sheetCount = workbook.getNumberOfSheets();
            result.put("sheetCount", sheetCount);
            result.put("preview", "共 " + sheetCount + " 个工作表，数据量较大建议下载查看");
        }
        return result;
    }

    /**
     * 预览 PPT
     */
    private static Map<String, Object> previewPpt(File file) throws IOException {
        Map<String, Object> result = new HashMap<>();
        try (XMLSlideShow ppt = new XMLSlideShow(new FileInputStream(file))) {
            int slideCount = ppt.getSlides().size();
            result.put("slideCount", slideCount);
            result.put("preview", "共 " + slideCount + " 页幻灯片");
        }
        return result;
    }

    private static Map<String, Object> previewImage(File file) throws IOException {
        Map<String, Object> result = new HashMap<>();
        BufferedImage image = ImageIO.read(file);
        result.put("width", image.getWidth());
        result.put("height", image.getHeight());
        result.put("preview", "图片尺寸: " + image.getWidth() + " x " + image.getHeight());
        return result;
    }

    private static Map<String, Object> previewText(File file) throws IOException {
        Map<String, Object> result = new HashMap<>();
        String content = new String(Files.readAllBytes(file.toPath()));
        result.put("preview", content.length() > 500 ? content.substring(0, 500) + "..." : content);
        return result;
    }
}

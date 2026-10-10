package cn.realm.cloud.framework.common.util.file;

import cn.idev.excel.FastExcel;
import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.event.AnalysisEventListener;
import cn.realm.cloud.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 文件内容读取工具类（支持 Excel / CSV / TXT / JSON）。
 *
 * <p><b>两种读取模式：</b>
 * <ul>
 *     <li><b>同步读取</b>（{@link #readAllLines}）：一次性返回所有行，适用于小文件（&lt; 20MB）。</li>
 *     <li><b>流式处理</b>（{@link #processLines}）：逐行回调，适用于大文件（百万行级别）。</li>
 * </ul>
 *
 * <p><b>安全防护：</b>
 * <ul>
 *     <li>文件大小限制（默认 100MB），防止 OOM（CWE-770）</li>
 *     <li>文本读取显式指定 UTF-8（CWE-838）</li>
 *     <li>CSV 列数上限 5000，防止畸形文件耗尽内存</li>
 *     <li>资源统一由 try-with-resources 管理（CWE-404）</li>
 * </ul>
 *
 * <p><b>线程安全：</b>本类所有方法均为无状态设计，线程安全。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 同步读取（小文件）
 * List<String> lines = FileReaderUtil.readAllLines(file, FileType.AUTO);
 *
 * // 流式处理（大文件）
 * FileReaderUtil.processLines(file, FileType.AUTO, line -> {
 *     // 逐行处理，如批量入库
 *     repository.save(line);
 * });
 *
 * // 自定义行转换（过滤空行、指定分隔符）
 * FileReaderUtil.processLines(file, FileType.CSV, rows -> {
 *     if (rows.isEmpty()) return null;
 *     return String.join(",", rows);
 * }, System.out::println);
 * }</pre>
 *
 * @author QI Guang
 * @since 1.0.0
 */
public final class FileReaderUtil {

    private static final Logger log = LoggerFactory.getLogger(FileReaderUtil.class);

    /**
     * 复用 {@link JsonUtils} 的全局 {@link ObjectMapper}，避免重复配置。
     */
    private static final ObjectMapper OBJECT_MAPPER = JsonUtils.getObjectMapper();

    /**
     * 单文件最大处理大小（默认 100MB），防止 OOM（CWE-770）。
     * <p>可通过系统属性 {@code -Dfile-reader.max-size=...} 覆盖。
     */
    private static final long MAX_FILE_SIZE =
            Long.getLong("file-reader.max-size", 100L * 1024 * 1024);

    /**
     * CSV 最大列数限制，防止畸形文件。
     */
    private static final int MAX_CSV_COLUMNS = 5000;

    /**
     * 单元格之间的默认分隔符（Excel / CSV 行扁平化时使用）。
     */
    private static final String CELL_SEPARATOR = "\t";

    private FileReaderUtil() {
        throw new UnsupportedOperationException(
                "FileReaderUtil is a static utility class and cannot be instantiated");
    }

    // ======================== 类型枚举 ========================

    /**
     * 文件类型。
     */
    public enum FileType {
        /**
         * Excel（.xlsx / .xls）
         */
        EXCEL,
        /**
         * CSV
         */
        CSV,
        /**
         * 纯文本
         */
        TXT,
        /**
         * JSON（顶层必须是数组）
         */
        JSON,
        /**
         * 根据扩展名自动识别
         */
        AUTO
    }

    // ======================== 同步读取 ========================

    /**
     * 同步读取文件所有行。
     *
     * <p><b>适用场景：</b>文件小于 20MB。大文件请使用 {@link #processLines}。
     *
     * @param file 上传文件，不允许为 {@code null}
     * @param type 文件类型；{@link FileType#AUTO} 表示按扩展名自动识别
     * @return 每行内容（Excel / CSV 行内以制表符分隔单元格）
     * @throws IllegalArgumentException 参数非法、文件过大、类型不支持时抛出
     * @throws UncheckedIOException     读取失败时抛出
     */
    public static List<String> readAllLines(MultipartFile file, FileType type) {
        Objects.requireNonNull(file, "file must not be null");
        Objects.requireNonNull(type, "type must not be null");
        checkFileSize(file);

        FileType resolvedType = resolveType(file, type);
        try (InputStream is = file.getInputStream()) {
            return switch (resolvedType) {
                case EXCEL -> readExcelAll(is);
                case CSV -> readCsvAll(is);
                case TXT -> readTxtAll(is);
                case JSON -> readJsonAll(is);
                default -> throw new IllegalArgumentException(
                        "Unsupported file type: " + resolvedType);
            };
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to read file: " + file.getOriginalFilename(), e);
        }
    }

    // ======================== 流式处理 ========================

    /**
     * 流式处理文件，每解析到一行就回调 {@code consumer}，不在内存累积。
     *
     * <p>适用于百万行级别的 Excel / CSV 文件。
     *
     * @param file     上传文件，不允许为 {@code null}
     * @param type     文件类型
     * @param consumer 行处理函数，不允许为 {@code null}
     */
    public static void processLines(MultipartFile file, FileType type,
                                    Consumer<String> consumer) {
        processLines(file, type, cells -> String.join(CELL_SEPARATOR, cells), consumer);
    }

    /**
     * 流式处理文件，支持自定义行转换器。
     *
     * <p>{@code rowConverter} 返回 {@code null} 时该行会被跳过（如过滤空行）。
     *
     * @param file         上传文件
     * @param type         文件类型
     * @param rowConverter 行转换器：接收单元格列表，返回最终字符串；返回 {@code null} 跳过该行
     * @param consumer     行处理函数
     */
    public static void processLines(MultipartFile file, FileType type,
                                    RowConverter rowConverter,
                                    Consumer<String> consumer) {
        Objects.requireNonNull(file, "file must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(rowConverter, "rowConverter must not be null");
        Objects.requireNonNull(consumer, "consumer must not be null");
        checkFileSize(file);

        FileType resolvedType = resolveType(file, type);
        try (InputStream is = file.getInputStream()) {
            switch (resolvedType) {
                case EXCEL -> processExcelLines(is, rowConverter, consumer);
                case CSV -> processCsvLines(is, rowConverter, consumer);
                case TXT -> processTxtLines(is, consumer);
                case JSON -> processJsonLines(is, consumer);
                default -> throw new IllegalArgumentException(
                        "Unsupported file type: " + resolvedType);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to process file: " + file.getOriginalFilename(), e);
        }
    }

    // ======================== Excel 处理 ========================

    private static List<String> readExcelAll(InputStream is) {
        List<Map<Integer, String>> rows = FastExcel.read(is)
                .sheet()
                .doReadSync();
        return rows.stream()
                .map(FileReaderUtil::flattenRow)
                .collect(Collectors.toList());
    }

    private static void processExcelLines(InputStream is, RowConverter converter,
                                          Consumer<String> consumer) {
        FastExcel.read(is)
                .registerReadListener(new AnalysisEventListener<Map<Integer, String>>() {
                    @Override
                    public void invoke(Map<Integer, String> rowMap, AnalysisContext context) {
                        List<String> cells = sortCells(rowMap);
                        String line = converter.convert(cells);
                        if (line != null && !line.isEmpty()) {
                            consumer.accept(line);
                        }
                    }

                    @Override
                    public void doAfterAllAnalysed(AnalysisContext context) {
                        if (log.isDebugEnabled()) {
                            log.debug("[processExcelLines] excel read completed");
                        }
                    }
                })
                .sheet()
                .doRead();
    }

    // ======================== CSV 处理 ========================

    private static List<String> readCsvAll(InputStream is) throws IOException {
        CsvParserSettings settings = buildCsvSettings();
        CsvParser parser = new CsvParser(settings);
        try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            List<String[]> rows = parser.parseAll(reader);
            return rows.stream()
                    .map(row -> String.join(CELL_SEPARATOR, row))
                    .collect(Collectors.toList());
        }
    }

    private static void processCsvLines(InputStream is, RowConverter converter,
                                        Consumer<String> consumer) throws IOException {
        CsvParserSettings settings = buildCsvSettings();
        CsvParser parser = new CsvParser(settings);
        try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            parser.beginParsing(reader);
            try {
                String[] row;
                while ((row = parser.parseNext()) != null) {
                    String line = converter.convert(List.of(row));
                    if (line != null && !line.isEmpty()) {
                        consumer.accept(line);
                    }
                }
            } finally {
                parser.stopParsing();
            }
        }
    }

    private static CsvParserSettings buildCsvSettings() {
        CsvParserSettings settings = new CsvParserSettings();
        settings.setLineSeparatorDetectionEnabled(true);
        settings.setMaxColumns(MAX_CSV_COLUMNS);
        return settings;
    }

    // ======================== TXT 处理 ========================

    private static List<String> readTxtAll(InputStream is) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.toList());
        }
    }

    private static void processTxtLines(InputStream is, Consumer<String> consumer)
            throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {
            reader.lines().forEach(consumer);
        }
    }

    // ======================== JSON 处理 ========================

    private static List<String> readJsonAll(InputStream is) throws IOException {
        List<Object> list = OBJECT_MAPPER.readValue(is, new com.fasterxml.jackson.core.type.TypeReference<>() {
        });
        List<String> result = new ArrayList<>(list.size());
        for (Object obj : list) {
            result.add(OBJECT_MAPPER.writeValueAsString(obj));
        }
        return result;
    }

    private static void processJsonLines(InputStream is, Consumer<String> consumer)
            throws IOException {
        // try-with-resources 保证 JsonParser 在任何路径下关闭（CWE-404）
        try (JsonParser parser = OBJECT_MAPPER.getFactory().createParser(is)) {
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new IllegalArgumentException(
                        "JSON top-level element must be an array");
            }
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                Object value = OBJECT_MAPPER.readValue(parser, Object.class);
                consumer.accept(OBJECT_MAPPER.writeValueAsString(value));
            }
        }
    }

    // ======================== 行转换器 ========================

    /**
     * 行转换器：接收单元格列表，返回最终行字符串。
     *
     * <p>返回 {@code null} 或空串时，该行会被 {@link #processLines} 跳过。
     */
    @FunctionalInterface
    public interface RowConverter {
        /**
         * 转换一行。
         *
         * @param cells 单元格列表
         * @return 转换后的行字符串；{@code null} 表示跳过该行
         */
        String convert(List<String> cells);
    }

    // ======================== 私有辅助 ========================

    /**
     * 根据扩展名解析文件类型。
     *
     * <p>使用 {@link FilenameUtils#getExtension(String)} 避免无扩展名文件
     * 被错误解析（如 {@code "README"}）。
     */
    private static FileType resolveType(MultipartFile file, FileType userType) {
        if (userType != FileType.AUTO) {
            return userType;
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot auto-detect file type: original filename is empty");
        }
        String ext = FilenameUtils.getExtension(originalFilename).toLowerCase();
        if (ext.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot auto-detect file type: no extension in " + originalFilename);
        }
        return switch (ext) {
            case "xlsx", "xls" -> FileType.EXCEL;
            case "csv" -> FileType.CSV;
            case "txt" -> FileType.TXT;
            case "json" -> FileType.JSON;
            default -> throw new IllegalArgumentException(
                    "Unsupported file extension: " + ext);
        };
    }

    /**
     * 校验文件大小。
     */
    private static void checkFileSize(MultipartFile file) {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "File size exceeds limit: " + file.getSize()
                            + " > " + MAX_FILE_SIZE);
        }
    }

    /**
     * 将 {@code Map<Integer, String>} 行按列索引排序，转换为 {@code List<String>}。
     */
    private static List<String> sortCells(Map<Integer, String> rowMap) {
        if (rowMap == null || rowMap.isEmpty()) {
            return List.of();
        }
        int maxCol = rowMap.keySet().stream().max(Integer::compareTo).orElse(-1);
        List<String> cells = new ArrayList<>(maxCol + 1);
        for (int i = 0; i <= maxCol; i++) {
            String value = rowMap.get(i);
            cells.add(value != null ? value : "");
        }
        return cells;
    }

    /**
     * 将 {@code Map<Integer, String>} 行扁平化为单个字符串。
     */
    private static String flattenRow(Map<Integer, String> rowMap) {
        return String.join(CELL_SEPARATOR, sortCells(rowMap));
    }
}

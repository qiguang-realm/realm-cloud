package cn.realm.cloud.framework.file.reader;

import cn.realm.cloud.framework.utils.file.FileConvertUtil;
import cn.realm.cloud.framework.utils.json.JsonUtils;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import org.apache.fesod.sheet.FesodSheet;
import org.apache.fesod.sheet.context.AnalysisContext;
import org.apache.fesod.sheet.event.AnalysisEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * <p><b>依赖边界：</b>本类<b>不依赖 Spring Web</b>，只接收 {@link InputStream} +
 * 文件名。{@code MultipartFile} 的适配位于 realm-web 的
 * {@code WebFileReaderUtil}，依赖方向为 {@code realm-web → realm-file}（单向）。
 *
 * <p><b>两种读取模式：</b>
 * <ul>
 *     <li><b>同步读取</b>（{@link #readAllLines}）：一次性返回所有行，适用于小文件。</li>
 *     <li><b>流式处理</b>（{@link #processLines}）：逐行回调，适用于大文件（百万行级别）。</li>
 * </ul>
 *
 * <p><b>流所有权：</b>本类所有方法<b>不关闭</b>传入的 {@link InputStream}，
 * 由调用方负责关闭（遵循"谁打开，谁关闭"原则，Effective Java Item 9）。
 *
 * <p><b>行语义：</b>
 * <ul>
 *     <li>Excel / CSV：每个单元格以制表符拼接为一行；可通过 {@link RowConverter} 自定义。</li>
 *     <li>TXT：每行文本作为单元素列表交给 {@link RowConverter}（便于统一过滤空行）。</li>
 *     <li>JSON：顶层数组的每个元素序列化为 JSON 字符串后直接回调，不经过按列转换。</li>
 * </ul>
 *
 * <p><b>安全防护：</b>
 * <ul>
 *     <li>文本读取显式指定 UTF-8（CWE-838）</li>
 *     <li>CSV 列数上限 5000，防止畸形文件耗尽内存</li>
 *     <li>资源统一由 try-with-resources 管理（CWE-404）</li>
 * </ul>
 *
 * <p><b>文件大小校验：</b>本类不做文件大小检查——{@code InputStream} 无法预知长度。
 * Web 场景的文件大小校验由 {@code WebFileReaderUtil} 在调用前完成
 * （{@code MultipartFile.getSize()}）；非 Web 场景由调用方自行控制。
 * {@link #readAllLines} 会一次性加载到内存，调用方应确保文件大小在可接受范围内。
 *
 * <p><b>线程安全：</b>本类所有方法均为无状态设计，线程安全。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 同步读取（小文件）
 * try (InputStream is = new FileInputStream("data.xlsx")) {
 *     List<String> lines = FileReaderUtil.readAllLines(is, "data.xlsx", FileType.AUTO);
 * }
 *
 * // 流式处理（大文件）
 * try (InputStream is = new FileInputStream("data.csv")) {
 *     FileReaderUtil.processLines(is, "data.csv", FileType.AUTO, line -> {
 *         repository.save(line);
 *     });
 * }
 * }</pre>
 *
 * <p><b>Excel 引擎：</b>基于 Apache Fesod（{@code org.apache.fesod:fesod-sheet}），
 * 由 FastExcel 迁移而来，API 保持兼容。
 *
 * @author QI Guang
 */
public final class FileReaderUtil {

    private static final Logger log = LoggerFactory.getLogger(FileReaderUtil.class);

    /**
     * 复用 {@link JsonUtils} 的全局 {@link ObjectMapper}，避免重复配置。
     */
    private static final ObjectMapper OBJECT_MAPPER = JsonUtils.getObjectMapper();

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
     * <p><b>适用场景：</b>小文件（建议 &lt; 20MB）。大文件请使用 {@link #processLines}。
     *
     * <p><b>流所有权：</b>不关闭 {@code is}，由调用方负责关闭。
     *
     * @param is       输入流，不允许为 {@code null}
     * @param fileName 文件名（用于 {@link FileType#AUTO} 时按扩展名推断；其他类型可传 {@code null}）
     * @param type     文件类型；{@link FileType#AUTO} 表示按扩展名自动识别
     * @return 每行内容（Excel / CSV 行内以制表符分隔单元格）
     * @throws IllegalArgumentException 参数非法、类型不支持时抛出
     * @throws UncheckedIOException     读取失败时抛出
     */
    public static List<String> readAllLines(InputStream is, String fileName, FileType type) {
        Objects.requireNonNull(is, "is must not be null");
        Objects.requireNonNull(type, "type must not be null");

        FileType resolvedType = resolveType(fileName, type);
        try {
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
                    "Failed to read file: " + fileName, e);
        }
    }

    // ======================== 流式处理 ========================

    /**
     * 流式处理文件，每解析到一行就回调 {@code consumer}，不在内存累积。
     *
     * <p>适用于百万行级别的 Excel / CSV 文件。
     *
     * <p><b>流所有权：</b>不关闭 {@code is}，由调用方负责关闭。
     *
     * @param is       输入流，不允许为 {@code null}
     * @param fileName 文件名（用于 {@link FileType#AUTO} 时按扩展名推断）
     * @param type     文件类型
     * @param consumer 行处理函数，不允许为 {@code null}
     */
    public static void processLines(InputStream is, String fileName, FileType type,
                                    Consumer<String> consumer) {
        processLines(is, fileName, type, cells -> String.join(CELL_SEPARATOR, cells), consumer);
    }

    /**
     * 流式处理文件，支持自定义行转换器。
     *
     * <p>{@code rowConverter} 返回 {@code null} 或空串时该行会被跳过（如过滤空行）。
     *
     * <p><b>不同格式传入的 {@code cells}：</b>
     * <ul>
     *     <li>Excel / CSV：按列顺序排列的单元格列表</li>
     *     <li>TXT：单元素列表（原始行文本）</li>
     *     <li>JSON：不经过转换器，直接回调序列化后的 JSON 字符串</li>
     * </ul>
     *
     * <p><b>流所有权：</b>不关闭 {@code is}，由调用方负责关闭。
     *
     * @param is           输入流，不允许为 {@code null}
     * @param fileName     文件名（用于 {@link FileType#AUTO} 时按扩展名推断）
     * @param type         文件类型
     * @param rowConverter 行转换器：接收单元格列表，返回最终字符串；返回 {@code null} 跳过该行
     * @param consumer     行处理函数
     */
    public static void processLines(InputStream is, String fileName, FileType type,
                                    RowConverter rowConverter,
                                    Consumer<String> consumer) {
        Objects.requireNonNull(is, "is must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(rowConverter, "rowConverter must not be null");
        Objects.requireNonNull(consumer, "consumer must not be null");

        FileType resolvedType = resolveType(fileName, type);
        try {
            switch (resolvedType) {
                case EXCEL -> processExcelLines(is, rowConverter, consumer);
                case CSV -> processCsvLines(is, rowConverter, consumer);
                case TXT -> processTxtLines(is, rowConverter, consumer);
                case JSON -> processJsonLines(is, consumer);
                default -> throw new IllegalArgumentException(
                        "Unsupported file type: " + resolvedType);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to process file: " + fileName, e);
        }
    }

    // ======================== Excel 处理 ========================

    private static List<String> readExcelAll(InputStream is) {
        List<Map<Integer, String>> rows = FesodSheet.read(is)
                .sheet()
                .doReadSync();
        return rows.stream()
                .map(FileReaderUtil::flattenRow)
                .collect(Collectors.toList());
    }

    private static void processExcelLines(InputStream is, RowConverter converter,
                                          Consumer<String> consumer) {
        FesodSheet.read(is)
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
                    .map(row -> String.join(CELL_SEPARATOR, normalize(row)))
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
                    String line = converter.convert(normalize(row));
                    if (line != null && !line.isEmpty()) {
                        consumer.accept(line);
                    }
                }
            } finally {
                parser.stopParsing();
            }
        }
    }

    /**
     * 将 CSV 单元格数组规整为列表：{@code null} 单元格替换为空串，
     * 避免 {@link String#join(CharSequence, Iterable)} 或自定义转换器抛 NPE。
     */
    private static List<String> normalize(String[] row) {
        if (row == null || row.length == 0) {
            return List.of();
        }
        List<String> cells = new ArrayList<>(row.length);
        for (String cell : row) {
            cells.add(cell != null ? cell : "");
        }
        return cells;
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

    private static void processTxtLines(InputStream is, RowConverter converter,
                                        Consumer<String> consumer) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {
            reader.lines().forEach(raw -> {
                // 单元素列表：与 Excel / CSV 保持统一的转换接口
                String line = converter.convert(List.of(raw));
                if (line != null && !line.isEmpty()) {
                    consumer.accept(line);
                }
            });
        }
    }

    // ======================== JSON 处理 ========================

    private static List<String> readJsonAll(InputStream is) throws IOException {
        List<Object> list = OBJECT_MAPPER.readValue(is, new TypeReference<List<Object>>() {
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
         * @param cells 单元格列表；TXT 格式下为单元素列表
         * @return 转换后的行字符串；{@code null} 表示跳过该行
         */
        String convert(List<String> cells);
    }

    // ======================== 私有辅助 ========================

    /**
     * 根据扩展名解析文件类型。
     *
     * <p>使用 {@link FileConvertUtil#getExtension(String)}（已返回小写、不含点号），
     * 避免无扩展名文件被错误解析（如 {@code "README"}）。
     */
    private static FileType resolveType(String fileName, FileType userType) {
        if (userType != FileType.AUTO) {
            return userType;
        }
        if (fileName == null || fileName.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot auto-detect file type: file name is empty");
        }
        String ext = FileConvertUtil.getExtension(fileName);
        if (ext.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot auto-detect file type: no extension in " + fileName);
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

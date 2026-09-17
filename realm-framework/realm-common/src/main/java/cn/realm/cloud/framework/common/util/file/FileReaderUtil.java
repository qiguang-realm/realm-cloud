package cn.realm.cloud.framework.common.util.file;

import cn.idev.excel.FastExcel;
import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.event.AnalysisEventListener;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 专注内容读取（支持 Excel、CSV、TXT、JSON）
 * <p>推荐使用流式处理 {@link #processLines} 应对大文件，避免 OOM。
 * 小文件可直接调用 {@link #readAllLines} 获取完整字符串列表。
 *
 * @author QI Guang
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
public class FileReaderUtil {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 文件类型枚举
     */
    public enum FileType {
        EXCEL,   // .xlsx / .xls
        CSV,
        TXT,
        JSON,
        AUTO     // 根据文件扩展名自动识别
    }

    // ======================== 同步读取（小文件专用） ========================

    /**
     * 同步读取文件所有行，返回字符串列表（每行对应一个字符串）
     * <p><b>注意：</b> 适用于小文件（< 20MB），大文件请使用 {@link #processLines}
     *
     * @param file MultipartFile
     * @param type 文件类型，可传 AUTO 自动识别
     * @return 每行内容的字符串列表
     * @throws RuntimeException 读取或解析失败时抛出
     */
    public static List<String> readAllLines(MultipartFile file, FileType type) {
        FileType resolvedType = resolveType(file, type);
        try (InputStream is = file.getInputStream()) {
            switch (resolvedType) {
                case EXCEL:
                    return readExcelAll(is);
                case CSV:
                    return readCsvAll(is);
                case TXT:
                    return readTxtAll(is);
                case JSON:
                    return readJsonAll(is);
                default:
                    throw new IllegalArgumentException("Unsupported file type: " + resolvedType);
            }
        } catch (IOException e) {
            log.error("文件读取失败: {}", e.getMessage(), e);
            throw new RuntimeException("文件读取失败: " + e.getMessage(), e);
        }
    }

    // ------------------ Excel 同步读取（FastExcel） ------------------
    private static List<String> readExcelAll(InputStream is) {
        // 同步读取，返回 List<Map<Integer, String>>
        List<Map<Integer, String>> rows = FastExcel.read(is)
                .sheet()
                .doReadSync();
        // 将每行 Map 按列索引顺序转换为字符串，用制表符连接各列
        return rows.stream()
                .map(rowMap -> {
                    // 获取最大列索引
                    int maxCol = rowMap.keySet().stream().max(Integer::compareTo).orElse(-1);
                    List<String> cells = new ArrayList<>();
                    for (int i = 0; i <= maxCol; i++) {
                        cells.add(rowMap.getOrDefault(i, ""));
                    }
                    return String.join("\t", cells);
                })
                .collect(Collectors.toList());
    }

    // ------------------ CSV 同步读取（univocity） ------------------
    private static List<String> readCsvAll(InputStream is) {
        CsvParserSettings settings = new CsvParserSettings();
        settings.setLineSeparatorDetectionEnabled(true);
        settings.setMaxColumns(5000);
        CsvParser parser = new CsvParser(settings);
        try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            List<String[]> rows = parser.parseAll(reader);
            return rows.stream()
                    .map(row -> String.join("\t", row))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException("CSV解析失败: " + e.getMessage(), e);
        }
    }

    // ------------------ TXT 同步读取 ------------------
    private static List<String> readTxtAll(InputStream is) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.toList());
        }
    }

    // ------------------ JSON 同步读取（Jackson） ------------------
    private static List<String> readJsonAll(InputStream is) throws IOException {
        List<Object> list = OBJECT_MAPPER.readValue(is, new TypeReference<List<Object>>() {
        });
        return list.stream()
                .map(obj -> {
                    try {
                        return OBJECT_MAPPER.writeValueAsString(obj);
                    } catch (Exception e) {
                        log.warn("对象转JSON字符串失败: {}", e.getMessage());
                        return obj.toString();
                    }
                })
                .collect(Collectors.toList());
    }

    // ======================== 流式逐行处理（大文件推荐） ========================

    /**
     * 流式处理文件，每解析到一行就回调 consumer，不全部加载到内存。
     * <p>特别适用于 Excel/CSV 等可能包含百万行数据的场景。
     *
     * @param file     MultipartFile
     * @param type     文件类型
     * @param consumer 每行字符串的处理函数（如批量入库、打印等）
     */
    public static void processLines(MultipartFile file, FileType type, Consumer<String> consumer) {
        FileType resolvedType = resolveType(file, type);
        try (InputStream is = file.getInputStream()) {
            switch (resolvedType) {
                case EXCEL:
                    processExcelLines(is, consumer);
                    break;
                case CSV:
                    processCsvLines(is, consumer);
                    break;
                case TXT:
                    processTxtLines(is, consumer);
                    break;
                case JSON:
                    processJsonLines(is, consumer);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported file type: " + resolvedType);
            }
        } catch (IOException e) {
            log.error("流式处理文件失败: {}", e.getMessage(), e);
            throw new RuntimeException("文件处理失败: " + e.getMessage(), e);
        }
    }

    // ------------------ Excel 流式处理（FastExcel 监听器） ------------------
    private static void processExcelLines(InputStream is, Consumer<String> consumer) {
        FastExcel.read(is)
                .registerReadListener(new AnalysisEventListener<Map<Integer, String>>() {
                    @Override
                    public void invoke(Map<Integer, String> rowMap, AnalysisContext context) {
                        // 将 Map 按列索引排序并转为 List<String>
                        List<String> row = rowMap.entrySet().stream()
                                .sorted(Map.Entry.comparingByKey())
                                .map(entry -> entry.getValue() == null ? "" : entry.getValue())
                                .collect(Collectors.toList());
                        String line = String.join("\t", row);
                        consumer.accept(line);
                    }

                    @Override
                    public void doAfterAllAnalysed(AnalysisContext context) {
                        log.info("Excel 文件解析完成");
                    }
                })
                .sheet()
                .doRead();
    }

    // ------------------ CSV 流式处理（univocity 逐行迭代） ------------------
    private static void processCsvLines(InputStream is, Consumer<String> consumer) {
        CsvParserSettings settings = new CsvParserSettings();
        settings.setLineSeparatorDetectionEnabled(true);
        settings.setMaxColumns(5000);
        CsvParser parser = new CsvParser(settings);
        try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            parser.beginParsing(reader);
            String[] row;
            while ((row = parser.parseNext()) != null) {
                String line = String.join("\t", row);
                consumer.accept(line);
            }
        } catch (IOException e) {
            throw new RuntimeException("CSV流式解析失败: " + e.getMessage(), e);
        } finally {
            parser.stopParsing();
        }
    }

    // ------------------ TXT 流式处理（BufferedReader.lines） ------------------
    private static void processTxtLines(InputStream is, Consumer<String> consumer) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            reader.lines().forEach(consumer);
        }
    }

    // ------------------ JSON 数组流式处理（Jackson 流式 API） ------------------
    private static void processJsonLines(InputStream is, Consumer<String> consumer) throws IOException {
        com.fasterxml.jackson.core.JsonParser parser = OBJECT_MAPPER.getFactory().createParser(is);
        if (parser.nextToken() != com.fasterxml.jackson.core.JsonToken.START_ARRAY) {
            throw new RuntimeException("JSON 文件必须以数组开头");
        }
        while (parser.nextToken() != com.fasterxml.jackson.core.JsonToken.END_ARRAY) {
            Object value = OBJECT_MAPPER.readValue(parser, Object.class);
            String line = OBJECT_MAPPER.writeValueAsString(value);
            consumer.accept(line);
        }
        parser.close();
    }

    // ======================== 辅助方法 ========================

    /**
     * 根据文件扩展名自动识别类型（AUTO 模式使用）
     */
    private static FileType resolveType(MultipartFile file, FileType userType) {
        if (userType != FileType.AUTO) {
            return userType;
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new IllegalArgumentException("文件名为空，无法自动识别类型");
        }
        String ext = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
        switch (ext) {
            case "xlsx":
            case "xls":
                return FileType.EXCEL;
            case "csv":
                return FileType.CSV;
            case "txt":
                return FileType.TXT;
            case "json":
                return FileType.JSON;
            default:
                throw new IllegalArgumentException("不支持的文件扩展名: " + ext);
        }
    }

    // ======================== 可选：自定义行转换器 ========================

    /**
     * 提供更灵活的行转换策略（例如自定义分隔符、过滤空行等）
     * 使用方式类似 processLines，但支持自定义转换器。
     */
    public interface RowConverter {
        String convert(List<String> cells);
    }

    // ------------------ 自定义转换器 ------------------
    public static void processExcelWithConverter(MultipartFile file, RowConverter converter, Consumer<String> consumer) {
        try (InputStream is = file.getInputStream()) {
            FastExcel.read(is)
                    .registerReadListener(new AnalysisEventListener<Map<Integer, String>>() {
                        @Override
                        public void invoke(Map<Integer, String> rowMap, AnalysisContext context) {
                            List<String> row = rowMap.entrySet().stream()
                                    .sorted(Map.Entry.comparingByKey())
                                    .map(entry -> entry.getValue() == null ? "" : entry.getValue())
                                    .collect(Collectors.toList());
                            String line = converter.convert(row);
                            if (line != null && !line.isEmpty()) {
                                consumer.accept(line);
                            }
                        }

                        @Override
                        public void doAfterAllAnalysed(AnalysisContext context) {
                            log.info("Excel with converter 解析完成");
                        }
                    })
                    .sheet()
                    .doRead();
        } catch (Exception e) {
            throw new RuntimeException("Excel处理失败", e);
        }
    }
}

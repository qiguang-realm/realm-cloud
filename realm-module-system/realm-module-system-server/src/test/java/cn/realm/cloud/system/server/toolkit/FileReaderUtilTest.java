package cn.realm.cloud.system.server.toolkit;

import cn.realm.cloud.framework.common.util.file.FileReaderUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FileReaderUtil 统一工具类的测试
 *
 * @author QI Guang
 */
public class FileReaderUtilTest {

    private static final Logger log = LoggerFactory.getLogger(FileReaderUtilTest.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // ==================== 准备测试数据文件（从 classpath 读取） ====================

    /**
     * 从 classpath（包括 main/resources）加载文件。
     *
     * <p>{@link ClassPathResource} 会自动处理前导 {@code /}，
     * 因此 {@code "/static/test.txt"} 与 {@code "static/test.txt"} 等价。
     */
    private static MultipartFile getTestFile(String classpathLocation, String filename,
                                             String contentType) throws IOException {
        ClassPathResource resource = new ClassPathResource(classpathLocation);
        if (!resource.exists()) {
            throw new IllegalArgumentException("Resource not found: " + classpathLocation);
        }
        byte[] content;
        try (var is = resource.getInputStream()) {
            content = is.readAllBytes();
        }
        return new MockMultipartFile(filename, filename, contentType, content);
    }

    /**
     * 从文件系统绝对路径加载。
     */
    private static MultipartFile getFileSystemFile(String absolutePath, String filename,
                                                   String contentType) throws IOException {
        Path path = Paths.get(absolutePath);
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("File not found: " + absolutePath);
        }
        byte[] content = Files.readAllBytes(path);
        return new MockMultipartFile(filename, filename, contentType, content);
    }

    // ==================== TXT 文件测试 ====================

    @Test
    public void testReadTxtFile() throws IOException {
        log.info("=== 测试读取 TXT 文件 ===");
        MultipartFile txtFile = getTestFile("/static/test.txt", "test.txt", "text/plain");

        // 1. 同步全量读取：保留原始行（含空行）
        List<String> lines = FileReaderUtil.readAllLines(txtFile, FileReaderUtil.FileType.TXT);
        log.info("TXT 文件行数: {}", lines.size());
        lines.forEach(line -> log.info("行内容: {}", line));
        assertNotNull(lines, "readAllLines 不应返回 null");

        // 2. 流式处理（默认转换器）：空行会被跳过，因此行数可能少于同步读取
        List<String> processedLines = new ArrayList<>();
        FileReaderUtil.processLines(txtFile, FileReaderUtil.FileType.TXT, processedLines::add);
        log.info("流式处理行数（默认过滤空行）: {}", processedLines.size());
        assertTrue(processedLines.size() <= lines.size(),
                "流式处理行数不应超过同步读取行数");

        // 3. 流式处理（自定义转换器）：原样保留空行，行数与同步读取一致
        List<String> rawLines = new ArrayList<>();
        FileReaderUtil.processLines(txtFile, FileReaderUtil.FileType.TXT,
                cells -> cells.get(0), rawLines::add);
        log.info("流式处理行数（保留空行）: {}", rawLines.size());
        assertEquals(lines.size(), rawLines.size(),
                "使用恒等转换器时，流式处理行数应与同步读取一致");
    }

    // ==================== CSV 文件测试 ====================

    @Test
    public void testReadCsvFile() throws IOException {
        log.info("=== 测试读取 CSV 文件 ===");
        MultipartFile csvFile = getTestFile("/static/test.csv", "test.csv", "text/csv");

        // 同步读取（注意：每行各列会用制表符连接）
        List<String> lines = FileReaderUtil.readAllLines(csvFile, FileReaderUtil.FileType.CSV);
        log.info("CSV 行数: {}", lines.size());
        for (int i = 0; i < Math.min(3, lines.size()); i++) {
            log.info("第{}行: {}", i + 1, lines.get(i));
        }
        assertNotNull(lines, "readAllLines 不应返回 null");
    }

    /**
     * 验证 CSV 中的 {@code null} 单元格被规整为空串，不会触发 NPE。
     *
     * <p>这是从 FastExcel 迁移到 Fesod 时顺带修复的健壮性问题：
     * 旧版 {@code String.join(..., List.of(row))} 遇到 {@code null} 元素会抛 NPE。
     */
    @Test
    public void testCsvWithNullCell() throws IOException {
        log.info("=== 测试 CSV 空单元格 ===");
        String csv = "a,,c\n1,2,\n";
        MultipartFile file = new MockMultipartFile(
                "null.csv", "null.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        List<String> lines = FileReaderUtil.readAllLines(file, FileReaderUtil.FileType.CSV);
        assertEquals(2, lines.size(), "应读取到 2 行数据");
        assertEquals("a\t\tc", lines.get(0), "中间空单元格应规整为空串");
        assertEquals("1\t2\t", lines.get(1), "末尾空单元格应规整为空串");
    }

    // ==================== Excel 文件测试 ====================

    @Test
    public void testReadExcelFile() throws IOException {
        log.info("=== 测试读取 Excel 文件 ===");
        MultipartFile excelFile = getTestFile("/static/test.xlsx", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        // 同步读取（每一行各列按制表符拼接）
        List<String> excelRows = FileReaderUtil.readAllLines(excelFile, FileReaderUtil.FileType.EXCEL);
        log.info("Excel 行数: {}", excelRows.size());
        for (int i = 0; i < Math.min(3, excelRows.size()); i++) {
            log.info("第{}行数据: {}", i + 1, excelRows.get(i));
        }

        // 流式处理（适合大文件）
        List<String> streamRows = new ArrayList<>();
        FileReaderUtil.processLines(excelFile, FileReaderUtil.FileType.EXCEL, streamRows::add);
        log.info("流式处理总行数: {}", streamRows.size());
        assertEquals(excelRows.size(), streamRows.size(),
                "同步读取与流式处理的行数应一致（均为非空行）");
    }

    // ==================== JSON 文件测试 ====================

    @Test
    public void testReadJsonFile() throws IOException {
        log.info("=== 测试读取 JSON 文件 ===");
        // 假设 JSON 是一个数组 [{"name":"Alice"}, {"name":"Bob"}]
        MultipartFile jsonFile = getTestFile("/static/data.json", "data.json", "application/json");

        // 同步读取：返回 List<String>，每个元素是一个 JSON 对象字符串
        List<String> lines = FileReaderUtil.readAllLines(jsonFile, FileReaderUtil.FileType.JSON);
        log.info("JSON 数组元素个数: {}", lines.size());
        for (String line : lines) {
            log.info("对象内容: {}", line);
            // 可以进一步解析为 JsonNode
            JsonNode node = OBJECT_MAPPER.readTree(line);
            log.info("  解析后: {}", node);
        }

        // 流式处理
        List<String> streamLines = new ArrayList<>();
        FileReaderUtil.processLines(jsonFile, FileReaderUtil.FileType.JSON, streamLines::add);
        log.info("流式处理元素个数: {}", streamLines.size());
        assertEquals(lines.size(), streamLines.size(),
                "JSON 同步读取与流式处理的元素个数应一致");
    }

    // ==================== 自动识别文件类型测试 ====================

    @Test
    public void testAutoDetectType() throws IOException {
        log.info("=== 测试自动识别文件类型 ===");
        MultipartFile txtFile = getTestFile("/static/test.txt", "test.txt", "text/plain");
        List<String> lines = FileReaderUtil.readAllLines(txtFile, FileReaderUtil.FileType.AUTO);
        log.info("自动识别为 TXT，读取行数: {}", lines.size());
        assertNotNull(lines, "自动识别后读取结果不应为 null");
    }

    // ==================== 错误处理测试 ====================

    @Test
    public void testErrorHandling() {
        log.info("=== 测试错误处理 ===");
        // 不支持的扩展名，应抛 IllegalArgumentException
        byte[] empty = new byte[0];
        MultipartFile fakeFile = new MockMultipartFile(
                "unknown.xyz", "unknown.xyz", "application/octet-stream", empty);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> FileReaderUtil.readAllLines(fakeFile, FileReaderUtil.FileType.AUTO));
        log.info("正确捕获不支持的文件类型异常: {}", ex.getMessage());
    }

    @Test
    public void testNullArguments() {
        log.info("=== 测试参数校验 ===");
        assertThrows(NullPointerException.class,
                () -> FileReaderUtil.readAllLines(null, FileReaderUtil.FileType.TXT));
        assertThrows(NullPointerException.class,
                () -> FileReaderUtil.readAllLines(
                        new MockMultipartFile("a.txt", "a.txt", "text/plain", new byte[0]),
                        null));
    }

    // ==================== 性能测试（小文件多次读取） ====================

    @Test
    public void testPerformance() throws IOException {
        log.info("=== 性能测试（同步读取） ===");
        MultipartFile txtFile = getTestFile("/static/test.txt", "test.txt", "text/plain");
        int iterations = 5;
        long total = 0;
        for (int i = 0; i < iterations; i++) {
            long start = System.currentTimeMillis();
            List<String> lines = FileReaderUtil.readAllLines(txtFile, FileReaderUtil.FileType.TXT);
            long elapsed = System.currentTimeMillis() - start;
            total += elapsed;
            log.info("第{}次读取耗时: {}ms, 行数: {}", i + 1, elapsed, lines.size());
        }
        log.info("平均耗时: {}ms", total / (double) iterations);
    }

    // ==================== 生成 SQL 语句的示例（使用新工具类） ====================

    /**
     * 从 JSON 文件中读取数据并生成 INSERT 语句。
     *
     * <p>注意：原方法使用 File 路径，新工具类基于 MultipartFile，这里改为从 resources 读取。
     */
    @Test
    public void generateSqlStatementsTest() throws IOException {
        log.info("=== 从 JSON 生成 SQL 语句 ===");
        MultipartFile jsonFile = getTestFile("/static/data.json", "data.json", "application/json");

        // 直接用 Jackson 读整个 JSON 数组，比按行读更稳
        List<JsonNode> nodes;
        try (var is = jsonFile.getInputStream()) {
            nodes = OBJECT_MAPPER.readValue(is, new TypeReference<List<JsonNode>>() {
            });
        }

        List<String> sqlStatements = new ArrayList<>();
        for (JsonNode node : nodes) {
            String code = text(node, "code");
            String name = text(node, "name");
            String pinyin = text(node, "pinyin");
            String zipCode = text(node, "zip_code");
            String firstLetter = text(node, "first_letter");
            int type = node.path("type").asInt();

            String parentCode = node.hasNonNull("parent_code")
                    ? "'" + sqlEscape(node.get("parent_code").asText()) + "'"
                    : "NULL";

            String sql = String.format(
                    "INSERT INTO `region` (`code`, `name`, `pinyin`, `zip_code`, `parent_code`, `type`, `first_letter`) " +
                            "VALUES ('%s', '%s', '%s', '%s', %s, %d, '%s');",
                    sqlEscape(code), sqlEscape(name), sqlEscape(pinyin), sqlEscape(zipCode),
                    parentCode, type, sqlEscape(firstLetter)
            );
            sqlStatements.add(sql);
        }

        Path outputPath = Paths.get(System.getProperty("user.dir"),
                "src", "main", "resources", "static", "output.txt");
        Files.createDirectories(outputPath.getParent());
        Files.write(outputPath, sqlStatements, StandardCharsets.UTF_8);
        log.info("成功生成 {} 条 SQL 语句，输出文件: {}", sqlStatements.size(), outputPath.toAbsolutePath());
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    private static String sqlEscape(String s) {
        if (s == null) return null;
        return s.replace("\\", "\\\\").replace("'", "''");
    }
}

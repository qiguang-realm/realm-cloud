package cn.realm.cloud.framework.excel.core.util;

import cn.realm.cloud.framework.excel.core.annotation.ExcelColumn;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Consumer;

/**
 * Apache POI Excel 工具类，封装读取映射、流式写入、多 Sheet、模板填充与 Web 导出。
 *
 * <p><b>流式写入资源清理：</b>{@link SXSSFWorkbook} 会在磁盘上创建临时文件。
 * 本工具类在所有写入路径中通过 {@link #closeWorkbook(Workbook)} 保证
 * {@link SXSSFWorkbook#dispose()} 被调用（POI 5.x 中 {@code close()} 虽已会清理临时文件，
 * 但显式 {@code dispose()} 在部分版本/场景下仍是可靠兜底）。{@code dispose()} 的调用
 * 被包裹在 {@code finally} 中，确保写入中途抛异常时临时文件也不会残留。</p>
 *
 * <p><b>流式写入的列宽策略：</b>{@code autoSizeColumn} 在 {@link SXSSFWorkbook} 下
 * 需要先调用 {@code trackAllColumnsForAutoSizing()} 才有效，且逐单元格计算宽度的开销
 * 在数据量较大时会显著拖慢写入。因此本工具类将列宽自适应设为<b>显式开关</b>：
 * 流式写入默认关闭，普通写入默认关闭；调用方按需开启，并自行承担性能代价。</p>
 *
 * @author QI Guang
 */
public final class PoiExcelUtils {

    private static final int DEFAULT_WINDOW_SIZE = 500;

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** 数值解析使用的格式化器，容忍千分位分隔符与常见货币符号 */
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private PoiExcelUtils() {
    }

    // ==================== 读取：反射映射 ====================

    /**
     * 读取第一个 Sheet，通过 {@link ExcelColumn} 注解将表头映射到实体字段。
     * 默认第一行为表头，自动兼容字符串、数值、布尔及公式单元格。
     *
     * <p><b>适用场景：</b>中小文件。{@link WorkbookFactory#create(InputStream)}
     * 会将整个工作簿加载到内存，大文件请使用 {@link #read(InputStream, Class, int, Consumer)}
     * 分批模式（其内部同样依赖全量加载，超大文件建议改用 Fesod 流式读取）。</p>
     */
    public static <T> List<T> read(InputStream in, Class<T> clazz) {
        return read(in, clazz, 0, 1);
    }

    /**
     * @param sheetNo       Sheet 下标，从 0 开始
     * @param headRowNumber 表头所在行号（从 0 开始），无表头传 -1
     */
    public static <T> List<T> read(InputStream in, Class<T> clazz, int sheetNo, int headRowNumber) {
        requireNonNull(in, "in");
        requireNonNull(clazz, "clazz");
        try (Workbook workbook = WorkbookFactory.create(in)) {
            return doRead(workbook, clazz, sheetNo, headRowNumber);
        } catch (ExcelException e) {
            throw e;
        } catch (Exception e) {
            throw new ExcelException("读取 Excel 失败", e);
        }
    }

    public static <T> List<T> read(MultipartFile file, Class<T> clazz) {
        requireNonNull(file, "file");
        try (InputStream in = file.getInputStream()) {
            return read(in, clazz);
        } catch (IOException e) {
            throw new ExcelException("读取上传文件失败", e);
        }
    }

    /**
     * 分批消费读取，每累积 {@code batchSize} 条回调一次，适合大数据量入库。
     *
     * <p>每次回调传入的是独立的 {@code ArrayList} 副本，消费者可以安全地异步处理或长期持有。</p>
     *
     * <p><b>注意：</b>本方法仍通过 {@link WorkbookFactory#create(InputStream)} 全量加载
     * 工作簿，分批只降低下游处理的内存峰值，不降低 Excel 解析本身的内存占用。</p>
     */
    public static <T> void read(InputStream in, Class<T> clazz, int batchSize,
                                Consumer<List<T>> consumer) {
        requireNonNull(in, "in");
        requireNonNull(clazz, "clazz");
        requireNonNull(consumer, "consumer");
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize 必须大于 0");
        }
        try (Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            List<ColumnMapping> mappings = resolveMappings(clazz, sheet.getRow(0));
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            DataFormatter formatter = new DataFormatter();

            List<T> batch = new ArrayList<>(batchSize);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                T entity = mapRow(row, clazz, mappings, evaluator, formatter);
                if (entity != null) {
                    batch.add(entity);
                }
                if (batch.size() >= batchSize) {
                    consumer.accept(new ArrayList<>(batch));
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                consumer.accept(new ArrayList<>(batch));
            }
        } catch (ExcelException e) {
            throw e;
        } catch (Exception e) {
            throw new ExcelException("分批读取 Excel 失败", e);
        }
    }

    // ==================== 写入：普通 XSSF / 流式 SXSSF ====================

    /**
     * 普通写入（全内存），适用于小数据量。列宽自适应默认关闭。
     */
    public static <T> void write(OutputStream out, List<T> data, Class<T> clazz) {
        write(out, "Sheet1", data, clazz, false);
    }

    /**
     * 流式写入，适用于大数据量，默认窗口 500 行。列宽自适应默认关闭。
     */
    public static <T> void writeStreaming(OutputStream out, List<T> data, Class<T> clazz) {
        write(out, "Sheet1", data, clazz, true);
    }

    /**
     * @param streaming {@code true} 使用 {@link SXSSFWorkbook} 流式写入，
     *                  {@code false} 使用 {@link XSSFWorkbook} 全内存写入
     */
    public static <T> void write(OutputStream out, String sheetName, List<T> data,
                                 Class<T> clazz, boolean streaming) {
        write(out, sheetName, data, clazz, streaming, false);
    }

    /**
     * @param autoSizeColumn 是否启用列宽自适应。
     *                       <b>在 {@code streaming=true} 时开启会显著拖慢写入</b>，
     *                       因为 {@link SXSSFSheet} 需要追踪所有行才能计算最佳宽度。
     *                       大数据量建议保持 {@code false}，或改用固定列宽。
     */
    public static <T> void write(OutputStream out, String sheetName, List<T> data,
                                 Class<T> clazz, boolean streaming, boolean autoSizeColumn) {
        requireNonNull(out, "out");
        requireNonNull(clazz, "clazz");
        Workbook workbook = streaming
                ? new SXSSFWorkbook(DEFAULT_WINDOW_SIZE)
                : new XSSFWorkbook();
        try {
            doWriteToSheet(workbook, sheetName, data, clazz, autoSizeColumn);
            workbook.write(out);
        } catch (ExcelException e) {
            throw e;
        } catch (Exception e) {
            throw new ExcelException("写入 Excel 失败", e);
        } finally {
            closeWorkbook(workbook);
        }
    }

    public static <T> void write(File file, List<T> data, Class<T> clazz, boolean streaming) {
        requireNonNull(file, "file");
        try (OutputStream out = new FileOutputStream(file)) {
            write(out, "Sheet1", data, clazz, streaming);
        } catch (IOException e) {
            throw new ExcelException("写入 Excel 文件失败", e);
        }
    }

    // ==================== 多 Sheet ====================

    /**
     * 多 Sheet 写入，所有 Sheet 共享流式/普通模式，列宽自适应默认关闭。
     */
    public static void writeMultiSheet(OutputStream out, List<SheetData<?>> sheets,
                                       boolean streaming) {
        writeMultiSheet(out, sheets, streaming, false);
    }

    /**
     * 多 Sheet 写入，支持逐 Sheet 选择流式或普通模式。
     *
     * @param autoSizeColumn 是否启用列宽自适应；流式模式下开启的性能代价见
     *                       {@link #write(OutputStream, String, List, Class, boolean, boolean)}
     */
    public static void writeMultiSheet(OutputStream out, List<SheetData<?>> sheets,
                                       boolean streaming, boolean autoSizeColumn) {
        requireNonNull(out, "out");
        if (sheets == null || sheets.isEmpty()) {
            throw new IllegalArgumentException("sheets 不能为空");
        }
        Workbook workbook = streaming
                ? new SXSSFWorkbook(DEFAULT_WINDOW_SIZE)
                : new XSSFWorkbook();
        try {
            for (SheetData<?> sheetData : sheets) {
                doWriteToSheet(workbook, sheetData.getSheetName(),
                        sheetData.getData(), sheetData.getClazz(), autoSizeColumn);
            }
            workbook.write(out);
        } catch (ExcelException e) {
            throw e;
        } catch (Exception e) {
            throw new ExcelException("多 Sheet 写入失败", e);
        } finally {
            closeWorkbook(workbook);
        }
    }

    // ==================== 模板填充 ====================

    /**
     * 基于模板填充普通对象（{@code ${name}} 形式）。
     *
     * <p><b>支持范围：</b>仅顶层简单字段的 {@code ${}} 替换，不支持嵌套对象
     * （{@code ${user.name}}）与列表循环。模板必须是 {@code .xlsx} 格式。</p>
     *
     * <p>模板填充场景使用 {@link WorkbookFactory#create(InputStream)}，返回的
     * {@link Workbook} 可能是 {@link XSSFWorkbook}，不涉及 SXSSF 临时文件；
     * 若未来扩展流式模板填充，需确保经过 {@link #closeWorkbook(Workbook)} 清理。</p>
     */
    public static void fill(InputStream templateIn, OutputStream out, Object data) {
        requireNonNull(templateIn, "templateIn");
        requireNonNull(out, "out");
        requireNonNull(data, "data");
        Workbook workbook = null;
        try {
            workbook = WorkbookFactory.create(templateIn);
            Sheet sheet = workbook.getSheetAt(0);
            fillSheet(sheet, data);
            workbook.write(out);
        } catch (ExcelException e) {
            throw e;
        } catch (Exception e) {
            throw new ExcelException("模板填充失败", e);
        } finally {
            closeWorkbook(workbook);
        }
    }

    // ==================== Web 导出 ====================

    /**
     * 导出到 HttpServletResponse，默认使用流式写入，列宽自适应关闭。
     */
    public static <T> void export(HttpServletResponse response, String fileName,
                                  List<T> data, Class<T> clazz) {
        export(response, fileName, "Sheet1", data, clazz, true);
    }

    public static <T> void export(HttpServletResponse response, String fileName,
                                  String sheetName, List<T> data, Class<T> clazz,
                                  boolean streaming) {
        export(response, fileName, sheetName, data, clazz, streaming, false);
    }

    /**
     * 导出到 HttpServletResponse。
     *
     * @param autoSizeColumn 是否启用列宽自适应；流式导出大数据量时建议保持 {@code false}
     */
    public static <T> void export(HttpServletResponse response, String fileName,
                                  String sheetName, List<T> data, Class<T> clazz,
                                  boolean streaming, boolean autoSizeColumn) {
        requireNonNull(response, "response");
        requireNonNull(clazz, "clazz");
        try {
            prepareResponse(response, fileName);
            write(response.getOutputStream(), sheetName, data, clazz, streaming, autoSizeColumn);
        } catch (ExcelException e) {
            throw e;
        } catch (IOException e) {
            throw new ExcelException("导出 Excel 失败", e);
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

    private static String toAsciiFallbackName(String fullName) {
        boolean asciiSafe = fullName.chars().allMatch(c ->
                c >= 0x20 && c < 0x7F && c != '"' && c != '\\' && c != '/');
        return asciiSafe ? fullName : "export.xlsx";
    }

    // ==================== 内部实现：读取 ====================

    private static <T> List<T> doRead(Workbook workbook, Class<T> clazz,
                                      int sheetNo, int headRowNumber) {
        Sheet sheet = workbook.getSheetAt(sheetNo);
        if (sheet.getPhysicalNumberOfRows() == 0) {
            return Collections.emptyList();
        }
        Row headRow = headRowNumber >= 0 ? sheet.getRow(headRowNumber) : null;
        List<ColumnMapping> mappings = resolveMappings(clazz, headRow);
        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
        DataFormatter formatter = new DataFormatter();

        List<T> result = new ArrayList<>();
        int startRow = headRowNumber >= 0 ? headRowNumber + 1 : 0;
        for (int i = startRow; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            T entity = mapRow(row, clazz, mappings, evaluator, formatter);
            if (entity != null) {
                result.add(entity);
            }
        }
        return result;
    }

    private static <T> T mapRow(Row row, Class<T> clazz, List<ColumnMapping> mappings,
                                FormulaEvaluator evaluator, DataFormatter formatter) {
        T instance;
        try {
            instance = clazz.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new ExcelException("无法实例化 " + clazz.getName() + "，请确认存在无参构造器", e);
        }

        boolean allBlank = true;
        for (ColumnMapping mapping : mappings) {
            Cell cell = row.getCell(mapping.columnIndex);
            if (cell == null) {
                continue;
            }
            String value = getCellStringValue(cell, evaluator, formatter);
            if (value == null || value.isBlank()) {
                continue;
            }
            allBlank = false;
            try {
                mapping.field.setAccessible(true);
                Object converted = convertValue(value, mapping.field.getType());
                mapping.field.set(instance, converted);
            } catch (IllegalAccessException e) {
                throw new ExcelException("字段赋值失败: " + mapping.field.getName(), e);
            } catch (NumberFormatException | DateTimeParseException e) {
                throw new ExcelException("值转换失败，字段: " + mapping.field.getName()
                        + "，目标类型: " + mapping.field.getType().getSimpleName()
                        + "，原始值: " + value, e);
            } catch (IllegalArgumentException e) {
                throw new ExcelException("字段类型不匹配: " + mapping.field.getName()
                        + "，期望类型: " + mapping.field.getType().getSimpleName()
                        + "，实际值: " + value, e);
            }
        }
        return allBlank ? null : instance;
    }

    /**
     * 统一将单元格转为字符串，兼容公式求值。
     * 对于公式单元格，DataFormatter 直接格式化会返回原始公式串，
     * 必须传入 FormulaEvaluator 才能拿到计算后的显示值。
     */
    private static String getCellStringValue(Cell cell, FormulaEvaluator evaluator,
                                             DataFormatter formatter) {
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }
        return switch (type) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> formatter.formatCellValue(cell, evaluator);
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case BLANK, ERROR -> null;
            default -> formatter.formatCellValue(cell, evaluator);
        };
    }

    /**
     * 将单元格字符串转换为目标类型。
     *
     * <p>数值类型使用 {@link BigDecimal} 解析，容忍千分位分隔符并避免
     * {@code Double.parseDouble} 在超大数值上的精度丢失；超出目标类型范围时
     * 抛出明确异常而非静默溢出。</p>
     */
    private static Object convertValue(String value, Class<?> targetType) {
        if (targetType == String.class || targetType == Object.class) {
            return value;
        }
        if (targetType == Integer.class || targetType == int.class) {
            BigDecimal decimal = parseBigDecimal(value);
            try {
                return decimal.intValueExact();
            } catch (ArithmeticException e) {
                throw new ExcelException("数值超出 int 范围: " + value, e);
            }
        }
        if (targetType == Long.class || targetType == long.class) {
            BigDecimal decimal = parseBigDecimal(value);
            try {
                return decimal.longValueExact();
            } catch (ArithmeticException e) {
                throw new ExcelException("数值超出 long 范围: " + value, e);
            }
        }
        if (targetType == Double.class || targetType == double.class) {
            return parseBigDecimal(value).doubleValue();
        }
        if (targetType == BigDecimal.class) {
            return parseBigDecimal(value);
        }
        if (targetType == Boolean.class || targetType == boolean.class) {
            return Boolean.parseBoolean(value.trim());
        }
        if (targetType == LocalDate.class) {
            return LocalDate.parse(value.trim(), DATE_FORMATTER);
        }
        if (targetType == LocalDateTime.class) {
            return LocalDateTime.parse(value.trim(), DATE_TIME_FORMATTER);
        }
        if (targetType == Date.class) {
            LocalDateTime dateTime = LocalDateTime.parse(value.trim(), DATE_TIME_FORMATTER);
            return Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
        }
        return value;
    }

    /**
     * 解析数值字符串，去除千分位分隔符与首尾空白，并兼容常见货币符号。
     */
    private static BigDecimal parseBigDecimal(String value) {
        String cleaned = value.trim()
                .replace(",", "")
                .replace("¥", "")
                .replace("$", "")
                .replace("€", "");
        return new BigDecimal(cleaned);
    }

    private static List<ColumnMapping> resolveMappings(Class<?> clazz, Row headRow) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            fields.addAll(Arrays.asList(current.getDeclaredFields()));
            current = current.getSuperclass();
        }

        List<ColumnMapping> mappings = new ArrayList<>();
        if (headRow == null) {
            fields.stream()
                    .filter(f -> f.isAnnotationPresent(ExcelColumn.class))
                    .sorted(Comparator.comparingInt(f -> f.getAnnotation(ExcelColumn.class).order()))
                    .forEach(f -> mappings.add(new ColumnMapping(f, mappings.size())));
            return mappings;
        }

        Map<String, Integer> headIndexMap = new HashMap<>();
        for (Cell cell : headRow) {
            if (cell.getCellType() == CellType.STRING) {
                headIndexMap.put(cell.getStringCellValue().trim(), cell.getColumnIndex());
            }
        }
        for (Field field : fields) {
            ExcelColumn annotation = field.getAnnotation(ExcelColumn.class);
            if (annotation == null) {
                continue;
            }
            Integer columnIndex = headIndexMap.get(annotation.value());
            if (columnIndex != null) {
                mappings.add(new ColumnMapping(field, columnIndex));
            }
        }
        return mappings;
    }

    // ==================== 内部实现：写入 ====================

    private static void doWriteToSheet(Workbook workbook, String sheetName,
                                       List<?> data, Class<?> clazz,
                                       boolean autoSizeColumn) {
        Sheet sheet = workbook.createSheet(
                (sheetName == null || sheetName.isBlank()) ? "Sheet1" : sheetName);
        List<Field> fields = resolveOrderedFields(clazz);
        if (fields.isEmpty()) {
            return;
        }

        // 流式模式下若需要列宽自适应，必须在写入任何行之前开启追踪
        if (autoSizeColumn && sheet instanceof SXSSFSheet sxssfSheet) {
            sxssfSheet.trackAllColumnsForAutoSizing();
        }

        Row headRow = sheet.createRow(0);
        CellStyle headStyle = createHeadStyle(workbook);
        for (int i = 0; i < fields.size(); i++) {
            Cell cell = headRow.createCell(i);
            cell.setCellValue(fields.get(i).getAnnotation(ExcelColumn.class).value());
            cell.setCellStyle(headStyle);
        }

        List<?> actualData = (data == null) ? Collections.emptyList() : data;
        for (int r = 0; r < actualData.size(); r++) {
            Row row = sheet.createRow(r + 1);
            Object item = actualData.get(r);
            for (int c = 0; c < fields.size(); c++) {
                Cell cell = row.createCell(c);
                try {
                    fields.get(c).setAccessible(true);
                    Object value = fields.get(c).get(item);
                    if (value != null) {
                        cell.setCellValue(String.valueOf(value));
                    }
                } catch (IllegalAccessException e) {
                    throw new ExcelException("读取字段值失败: " + fields.get(c).getName(), e);
                }
            }
        }

        if (autoSizeColumn) {
            for (int i = 0; i < fields.size(); i++) {
                sheet.autoSizeColumn(i);
            }
        }
    }

    private static List<Field> resolveOrderedFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (field.isAnnotationPresent(ExcelColumn.class)) {
                    fields.add(field);
                }
            }
            current = current.getSuperclass();
        }
        fields.sort(Comparator.comparingInt(f -> f.getAnnotation(ExcelColumn.class).order()));
        return fields;
    }

    private static CellStyle createHeadStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private static void fillSheet(Sheet sheet, Object data) {
        Map<String, String> values = new HashMap<>();
        for (Field field : data.getClass().getDeclaredFields()) {
            try {
                field.setAccessible(true);
                Object value = field.get(data);
                if (value != null) {
                    values.put(field.getName(), String.valueOf(value));
                }
            } catch (IllegalAccessException e) {
                throw new ExcelException("模板填充读取字段失败: " + field.getName(), e);
            }
        }
        for (Row row : sheet) {
            for (Cell cell : row) {
                if (cell.getCellType() == CellType.STRING) {
                    String text = cell.getStringCellValue();
                    for (Map.Entry<String, String> entry : values.entrySet()) {
                        text = text.replace("${" + entry.getKey() + "}", entry.getValue());
                    }
                    cell.setCellValue(text);
                }
            }
        }
    }

    // ==================== 资源清理 ====================

    /**
     * 关闭工作簿。{@link SXSSFWorkbook#dispose()} 在 {@code finally} 中调用，
     * 确保即使写入阶段抛出异常，磁盘临时文件也会被清理。
     */
    private static void closeWorkbook(Workbook workbook) {
        if (workbook == null) {
            return;
        }
        try {
            if (workbook instanceof SXSSFWorkbook sxssf) {
                sxssf.dispose();
            }
        } finally {
            try {
                workbook.close();
            } catch (IOException ignored) {
                // 关闭阶段的 IO 异常不覆盖主流程结果
            }
        }
    }

    private static void requireNonNull(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
    }

    // ==================== 辅助类型 ====================

    private static final class ColumnMapping {
        private final Field field;
        private final int columnIndex;

        private ColumnMapping(Field field, int columnIndex) {
            this.field = field;
            this.columnIndex = columnIndex;
        }
    }

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

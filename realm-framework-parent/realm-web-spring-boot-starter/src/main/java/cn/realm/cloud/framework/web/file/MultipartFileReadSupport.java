package cn.realm.cloud.framework.web.file;

import cn.realm.cloud.framework.file.reader.FileReaderUtil;
import cn.realm.cloud.framework.file.reader.FileReaderUtil.FileType;
import cn.realm.cloud.framework.file.reader.FileReaderUtil.RowConverter;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * {@link MultipartFile} 内容读取支撑类。
 *
 * <p><b>职责：</b>从 {@link MultipartFile} 取出 {@link InputStream} 与原始文件名，
 * 校验文件大小后委托 {@code realm-file} 的 {@link FileReaderUtil} 完成
 * Excel / CSV / TXT / JSON 的解析。本类只负责 Web 类型到核心类型的桥接，
 * <b>不包含任何解析实现</b>。
 *
 * <p><b>为什么以 {@code Support} 结尾而非 {@code Util：</b>
 * 本类不是通用的 {@code StringUtils} 式工具，而是<b>为特定技术（Spring MVC 的
 * {@link MultipartFile}）提供适配支撑</b>的类。{@code Support} 是 Spring 框架自身
 * 常用的命名惯例，用于表达"为特定上下文提供辅助功能"的语义；{@code Util} 则会
 * 掩盖本类作为适配器的本质。
 *
 * <p><b>依赖方向：</b>{@code realm-web → realm-file}（单向）。
 * {@code realm-file} 不依赖 Spring Web，纯后台场景可以直接使用
 * {@link FileReaderUtil} 配合 {@link InputStream}。
 *
 * <p><b>文件大小校验的位置：</b>本类在委托前通过 {@link MultipartFile#getSize()}
 * 校验文件大小（默认 100MB，可通过系统属性 {@code -Dfile-reader.max-size=...} 覆盖）。
 * 核心 {@link FileReaderUtil} 接收 {@link InputStream}，无法预知长度，因此
 * 大小校验属于 Web 层的职责。
 *
 * <p><b>与 {@link MultipartFileConvertSupport} 的分工：</b>
 * <ul>
 *     <li>{@link MultipartFileConvertSupport}：{@link MultipartFile} 的字节转换、MIME 推断</li>
 *     <li>{@code MultipartFileReadSupport}：{@link MultipartFile} 的内容读取
 *         （Excel / CSV / TXT / JSON）</li>
 * </ul>
 *
 * <p><b>流所有权：</b>本类从 {@link MultipartFile} 打开的 {@link InputStream}
 * 由本类负责关闭（try-with-resources），不泄漏给调用方。
 *
 * <p><b>解析引擎：</b>Excel 依赖 {@code fesod-sheet}（{@code realm-file} 的 compile 依赖，
 * 会传递）；CSV 依赖 {@code univocity-parsers}（{@code realm-file} 的 optional 依赖，
 * 不会传递）。使用 CSV 读取时，调用方需显式引入 {@code univocity-parsers}。
 *
 * <p><b>线程安全：</b>本类所有方法均为无状态设计，线程安全。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // Controller 中同步读取上传文件
 * @PostMapping("/import")
 * public List<String> importData(@RequestParam("file") MultipartFile file) {
 *     return MultipartFileReadSupport.readAllLines(file, FileType.AUTO);
 * }
 *
 * // Controller 中流式处理大文件
 * @PostMapping("/import-large")
 * public void importLarge(@RequestParam("file") MultipartFile file) {
 *     MultipartFileReadSupport.processLines(file, FileType.AUTO, line -> {
 *         repository.save(line);
 *     });
 * }
 * }</pre>
 *
 * @author QI Guang
 * @see FileReaderUtil
 */
public final class MultipartFileReadSupport {

    /**
     * 单文件最大处理大小（默认 100MB），防止 OOM（CWE-770）。
     *
     * <p>可通过系统属性 {@code -Dfile-reader.max-size=...} 覆盖。
     * 仅在 Web 适配层校验——核心 {@link FileReaderUtil} 不预知 {@link InputStream} 长度。
     */
    private static final long MAX_FILE_SIZE =
            Long.getLong("file-reader.max-size", 100L * 1024 * 1024);

    private MultipartFileReadSupport() {
        throw new UnsupportedOperationException(
                "MultipartFileReadSupport is a static support class and cannot be instantiated");
    }

    // ================================================================
    // 同步读取
    // ================================================================

    /**
     * 同步读取上传文件的所有行。
     *
     * <p><b>适用场景：</b>小文件（建议 &lt; 20MB）。大文件请使用 {@link #processLines}。
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

        try (InputStream is = file.getInputStream()) {
            return FileReaderUtil.readAllLines(is, file.getOriginalFilename(), type);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to read file: " + file.getOriginalFilename(), e);
        }
    }

    // ================================================================
    // 流式处理
    // ================================================================

    /**
     * 流式处理上传文件，每解析到一行就回调 {@code consumer}，不在内存累积。
     *
     * <p>适用于百万行级别的 Excel / CSV 文件。
     *
     * @param file     上传文件，不允许为 {@code null}
     * @param type     文件类型
     * @param consumer 行处理函数，不允许为 {@code null}
     */
    public static void processLines(MultipartFile file, FileType type,
                                    Consumer<String> consumer) {
        processLines(file, type, cells -> String.join("\t", cells), consumer);
    }

    /**
     * 流式处理上传文件，支持自定义行转换器。
     *
     * <p>{@code rowConverter} 返回 {@code null} 或空串时该行会被跳过（如过滤空行）。
     *
     * @param file         上传文件，不允许为 {@code null}
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

        try (InputStream is = file.getInputStream()) {
            FileReaderUtil.processLines(is, file.getOriginalFilename(), type,
                    rowConverter, consumer);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to process file: " + file.getOriginalFilename(), e);
        }
    }

    // ================================================================
    // 私有辅助
    // ================================================================

    /**
     * 校验上传文件大小，防止 OOM（CWE-770）。
     *
     * <p>仅 Web 层可做——{@link MultipartFile#getSize()} 提供了文件长度；
     * 核心 {@link FileReaderUtil} 接收 {@link InputStream}，无法预知长度。
     */
    private static void checkFileSize(MultipartFile file) {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "File size exceeds limit: " + file.getSize()
                            + " > " + MAX_FILE_SIZE);
        }
    }
}

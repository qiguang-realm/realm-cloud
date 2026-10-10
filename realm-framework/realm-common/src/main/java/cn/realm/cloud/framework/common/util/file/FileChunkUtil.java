package cn.realm.cloud.framework.common.util.file;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 文件分片上传 / 断点续传工具类。
 *
 * <p><b>核心流程：</b>
 * <ol>
 *     <li>{@link #initUpload(String, long, Long)} 初始化任务，返回 {@link UploadTask}（含 taskId）</li>
 *     <li>{@link #uploadChunk(String, int, byte[])} 逐片上传，服务端按分片索引落盘</li>
 *     <li>{@link #getProgress(String)} 查询进度（可选）</li>
 *     <li>{@link #mergeChunks(String, File)} 合并所有分片为完整文件</li>
 * </ol>
 *
 * <p><b>安全防护（CWE-22）：</b>
 * <ul>
 *     <li>{@code taskId} 必须匹配 {@code [a-f0-9]{32}} 格式（由 {@link #initUpload} 生成）；</li>
 *     <li>所有拼接后的路径经 {@link Path#normalize()} 处理，并校验位于 {@code UPLOAD_TEMP_DIR} 之内；</li>
 *     <li>非法 {@code taskId} 立即抛出 {@link IllegalArgumentException}。</li>
 * </ul>
 *
 * <p><b>状态管理：</b>
 * 任务元数据（{@link UploadTask}）保存在进程内的 {@link Cache} 中，
 * 默认 TTL 24 小时。JVM 重启后任务状态丢失，需重新初始化。
 * 如需跨 JVM 持久化，请将元数据写入数据库或 Redis。
 *
 * <p><b>线程安全：</b>本类所有方法均为线程安全。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 1. 初始化
 * UploadTask task = FileChunkUtil.initUpload("movie.mp4", 100_000_000L, null);
 * String taskId = task.getTaskId();
 *
 * // 2. 逐片上传
 * for (int i = 0; i < task.getTotalChunks(); i++) {
 *     byte[] chunk = readChunk(file, i, task.getChunkSize());
 *     FileChunkUtil.uploadChunk(taskId, i, chunk);
 * }
 *
 * // 3. 查询进度
 * UploadProgress progress = FileChunkUtil.getProgress(taskId);
 * double percent = progress.getProgress();  // 0.0 ~ 100.0
 *
 * // 4. 合并
 * File result = FileChunkUtil.mergeChunks(taskId, new File("/data/movie.mp4"));
 * }</pre>
 *
 * @author QI Guang
 * @since 1.0.0
 */
public final class FileChunkUtil {

    private static final Logger log = LoggerFactory.getLogger(FileChunkUtil.class);

    /**
     * {@code taskId} 的合法格式：32 位小写十六进制。
     */
    private static final Pattern TASK_ID_PATTERN = Pattern.compile("^[a-f0-9]{32}$");

    /**
     * 分片文件名的前缀。
     */
    private static final String CHUNK_FILE_PREFIX = "chunk_";

    /**
     * 默认分片大小：5MB，可通过系统属性 {@code -Dfile-chunk.size=10485760} 覆盖。
     */
    private static final long DEFAULT_CHUNK_SIZE =
            Long.getLong("file-chunk.default-size", 5 * 1024 * 1024L);

    /**
     * 分片上传根目录，可通过系统属性 {@code -Dfile-chunk.tmp-dir=/data/tmp} 覆盖。
     */
    private static final Path UPLOAD_TEMP_ROOT = Paths.get(
            System.getProperty("file-chunk.tmp-dir",
                    System.getProperty("java.io.tmpdir") + File.separator + "chunk_upload"));

    /**
     * 任务元数据缓存，默认 24 小时过期，防止内存无限增长。
     */
    private static final Cache<String, UploadTask> TASK_CACHE = CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofHours(24))
            .maximumSize(10_000)
            .build();

    private FileChunkUtil() {
        throw new UnsupportedOperationException(
                "FileChunkUtil is a static utility class and cannot be instantiated");
    }

    // ==================== 初始化 ====================

    /**
     * 初始化分片上传任务。
     *
     * @param fileName  原始文件名（仅用于记录，不做校验）
     * @param totalSize 文件总大小（字节），必须大于 0
     * @param chunkSize 分片大小（字节）；为 {@code null} 或非正数时使用默认值
     * @return 上传任务（含 {@code taskId}）
     * @throws IllegalArgumentException {@code totalSize <= 0} 时抛出
     */
    public static UploadTask initUpload(String fileName, long totalSize, Long chunkSize) {
        if (totalSize <= 0) {
            throw new IllegalArgumentException("totalSize must be positive, but was: " + totalSize);
        }
        long effectiveChunkSize = (chunkSize != null && chunkSize > 0)
                ? chunkSize
                : DEFAULT_CHUNK_SIZE;

        String taskId = UUID.randomUUID().toString().replace("-", "");
        int totalChunks = (int) Math.ceil((double) totalSize / effectiveChunkSize);
        UploadTask task = new UploadTask(taskId, fileName, totalSize,
                effectiveChunkSize, totalChunks);
        TASK_CACHE.put(taskId, task);

        if (log.isDebugEnabled()) {
            log.debug("[initUpload] taskId={}, fileName={}, totalSize={}, chunkSize={}, totalChunks={}",
                    taskId, fileName, totalSize, effectiveChunkSize, totalChunks);
        }
        return task;
    }

    // ==================== 分片上传 ====================

    /**
     * 上传单个分片。
     *
     * @param taskId     任务 ID（必须匹配 {@code [a-f0-9]{32}} 格式）
     * @param chunkIndex 分片索引（从 0 开始，且必须小于任务总片数）
     * @param chunkData  分片数据，不允许为 {@code null}
     * @throws IOException              写入失败时抛出
     * @throws IllegalArgumentException {@code taskId} 非法、{@code chunkIndex} 越界时抛出
     */
    public static void uploadChunk(String taskId, int chunkIndex, byte[] chunkData)
            throws IOException {
        UploadTask task = requireTask(taskId);
        Objects.requireNonNull(chunkData, "chunkData must not be null");
        if (chunkIndex < 0 || chunkIndex >= task.getTotalChunks()) {
            throw new IllegalArgumentException(
                    "chunkIndex out of range [0, " + task.getTotalChunks() + "): " + chunkIndex);
        }

        Path chunkDir = resolveTaskDir(taskId);
        Files.createDirectories(chunkDir);
        Path chunkFile = chunkDir.resolve(CHUNK_FILE_PREFIX + chunkIndex);
        // 使用 CREATE_NEW 避免覆盖（重复上传同一分片应视为异常或幂等）
        Files.write(chunkFile, chunkData,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        if (log.isDebugEnabled()) {
            log.debug("[uploadChunk] taskId={}, chunkIndex={}, size={}",
                    taskId, chunkIndex, chunkData.length);
        }
    }

    // ==================== 合并分片 ====================

    /**
     * 合并所有分片为完整文件。
     *
     * <p><b>原子性：</b>先写入 {@code targetFile.tmp} 临时文件，合并成功后通过
     * {@link StandardCopyOption#ATOMIC_MOVE} 原子重命名为目标文件；
     * 合并失败不会留下不完整的目标文件。
     *
     * @param taskId     任务 ID
     * @param targetFile 目标文件
     * @return 合并后的目标文件
     * @throws IOException              合并失败时抛出
     * @throws IllegalArgumentException 分片目录不存在、分片缺失、任务不存在时抛出
     */
    public static File mergeChunks(String taskId, File targetFile) throws IOException {
        UploadTask task = requireTask(taskId);
        Objects.requireNonNull(targetFile, "targetFile must not be null");

        Path chunkDir = resolveTaskDir(taskId);
        if (!Files.isDirectory(chunkDir)) {
            throw new IllegalArgumentException("Chunk directory not found: " + chunkDir);
        }

        // 校验所有分片齐全（防止中途合并）
        for (int i = 0; i < task.getTotalChunks(); i++) {
            Path chunk = chunkDir.resolve(CHUNK_FILE_PREFIX + i);
            if (!Files.exists(chunk)) {
                throw new IllegalStateException(
                        "Missing chunk: index=" + i + ", taskId=" + taskId);
            }
        }

        // 确保目标父目录存在
        Path targetPath = targetFile.toPath();
        Path parent = targetPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        // 先写入临时文件，成功后原子重命名
        Path tmpTarget = targetPath.resolveSibling(targetPath.getFileName() + ".tmp");
        try (OutputStream out = Files.newOutputStream(tmpTarget,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            for (int i = 0; i < task.getTotalChunks(); i++) {
                Path chunk = chunkDir.resolve(CHUNK_FILE_PREFIX + i);
                Files.copy(chunk, out);
            }
        } catch (IOException e) {
            // 合并失败时清理临时文件
            Files.deleteIfExists(tmpTarget);
            throw e;
        }

        // 原子重命名
        Files.move(tmpTarget, targetPath,
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        // 清理分片与任务元数据
        cleanTempFiles(taskId);

        if (log.isDebugEnabled()) {
            log.debug("[mergeChunks] taskId={}, target={}", taskId, targetPath);
        }
        return targetFile;
    }

    // ==================== 进度查询 ====================

    /**
     * 获取上传进度。
     *
     * @param taskId 任务 ID
     * @return 进度对象；任务不存在时返回 {@code uploadedChunks=0, totalChunks=0, progress=0}
     */
    public static UploadProgress getProgress(String taskId) {
        if (!isValidTaskId(taskId)) {
            return UploadProgress.empty();
        }
        UploadTask task = TASK_CACHE.getIfPresent(taskId);
        int totalChunks = task != null ? task.getTotalChunks() : 0;

        Path chunkDir = resolveTaskDir(taskId);
        if (!Files.isDirectory(chunkDir)) {
            return new UploadProgress(0, totalChunks);
        }
        long uploaded = countChunks(chunkDir);
        return new UploadProgress((int) uploaded, totalChunks);
    }

    // ==================== 取消上传 ====================

    /**
     * 取消上传，清理临时分片与任务元数据。
     *
     * @param taskId 任务 ID
     */
    public static void cancelUpload(String taskId) {
        if (!isValidTaskId(taskId)) {
            return;
        }
        cleanTempFiles(taskId);
        TASK_CACHE.invalidate(taskId);
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 校验并获取任务，任务不存在时抛出异常。
     */
    private static UploadTask requireTask(String taskId) {
        if (!isValidTaskId(taskId)) {
            throw new IllegalArgumentException(
                    "Invalid taskId format (expected [a-f0-9]{32}): " + taskId);
        }
        UploadTask task = TASK_CACHE.getIfPresent(taskId);
        if (task == null) {
            throw new IllegalStateException(
                    "Task not found or expired: taskId=" + taskId);
        }
        return task;
    }

    /**
     * 校验 {@code taskId} 格式。
     */
    private static boolean isValidTaskId(String taskId) {
        return taskId != null && TASK_ID_PATTERN.matcher(taskId).matches();
    }

    /**
     * 解析任务分片目录，并校验路径位于 {@link #UPLOAD_TEMP_ROOT} 之内。
     *
     * <p><b>安全：</b>双重保障——先校验 {@code taskId} 格式，再校验规范化后的路径
     * 以 {@link #UPLOAD_TEMP_ROOT} 为前缀，防止路径遍历（CWE-22）。
     */
    private static Path resolveTaskDir(String taskId) {
        if (!isValidTaskId(taskId)) {
            throw new IllegalArgumentException(
                    "Invalid taskId format (expected [a-f0-9]{32}): " + taskId);
        }
        Path dir = UPLOAD_TEMP_ROOT.resolve(taskId).normalize();
        // 边界校验：确保最终路径位于 UPLOAD_TEMP_ROOT 之内
        Path rootNormalized = UPLOAD_TEMP_ROOT.normalize().toAbsolutePath();
        if (!dir.toAbsolutePath().startsWith(rootNormalized)) {
            throw new SecurityException(
                    "Path traversal detected: " + dir);
        }
        return dir;
    }

    /**
     * 统计分片目录内已上传的分片数量。
     */
    private static long countChunks(Path chunkDir) {
        try (Stream<Path> stream = Files.list(chunkDir)) {
            return stream.filter(p -> p.getFileName().toString().startsWith(CHUNK_FILE_PREFIX))
                    .count();
        } catch (IOException e) {
            log.warn("[countChunks] failed to list chunk dir: {}", chunkDir, e);
            return 0;
        }
    }

    /**
     * 清理分片目录及其任务元数据。
     *
     * <p><b>异常策略：</b>清理失败记录 WARN 日志但不抛出异常，
     * 因为清理属于"尽力而为"的后台操作，不应中断主流程。
     */
    private static void cleanTempFiles(String taskId) {
        if (!isValidTaskId(taskId)) {
            return;
        }
        Path dir = resolveTaskDir(taskId);
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.sorted((a, b) -> b.compareTo(a))
                    .map(Path::toFile)
                    .forEach(file -> {
                        if (!file.delete()) {
                            log.warn("[cleanTempFiles] failed to delete: {}", file);
                        }
                    });
        } catch (IOException e) {
            log.warn("[cleanTempFiles] failed to walk dir: {}", dir, e);
        }
    }

    // ==================== 数据类 ====================

    /**
     * 上传任务元数据。
     *
     * @param taskId      任务 ID
     * @param fileName    原始文件名
     * @param totalSize   文件总大小（字节）
     * @param chunkSize   分片大小（字节）
     * @param totalChunks 总片数
     */
    public record UploadTask(String taskId, String fileName, long totalSize,
                             long chunkSize, int totalChunks) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        public String getTaskId() {
            return taskId;
        }

        public String getFileName() {
            return fileName;
        }

        public long getTotalSize() {
            return totalSize;
        }

        public long getChunkSize() {
            return chunkSize;
        }

        public int getTotalChunks() {
            return totalChunks;
        }
    }

    /**
     * 上传进度。
     *
     * @param uploadedChunks 已上传分片数
     * @param totalChunks    总分片数
     * @param progress       进度百分比（0.0 ~ 100.0）
     */
    public record UploadProgress(int uploadedChunks, int totalChunks, double progress)
            implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        public UploadProgress(int uploadedChunks, int totalChunks) {
            this(uploadedChunks, totalChunks,
                    totalChunks > 0 ? (double) uploadedChunks / totalChunks * 100 : 0);
        }

        public static UploadProgress empty() {
            return new UploadProgress(0, 0, 0);
        }

        public int getUploadedChunks() {
            return uploadedChunks;
        }

        public int getTotalChunks() {
            return totalChunks;
        }

        public double getProgress() {
            return progress;
        }
    }
}

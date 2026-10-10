package cn.realm.cloud.framework.common.util.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文件版本管理工具类。
 *
 * <p><b>能力：</b>
 * <ul>
 *     <li>{@link #saveVersion(Path, Path)}：保存新版本（自动递增版本号）</li>
 *     <li>{@link #getVersions(String, Path)}：查询文件的所有历史版本</li>
 *     <li>{@link #restoreVersion(String, int, Path, Path)}：还原到指定版本</li>
 * </ul>
 *
 * <p><b>版本文件命名约定：</b>{@code {baseName}_v{version}{extension}}，
 * 例如 {@code report_v1.pdf}、{@code report_v2.pdf}。
 *
 * <p><b>并发安全：</b>
 * {@link #saveVersion} 通过文件锁保证多线程 / 多进程下版本号不冲突。
 * 锁文件位于版本目录内（{@code .version.lock}）。
 *
 * <p><b>自动清理：</b>每个文件保留最近 {@value # DEFAULT_MAX_VERSIONS} 个版本，
 * 超出部分的最旧版本被自动删除。可通过系统属性
 * {@code -Dfile-version.max-versions=20} 覆盖。
 *
 * <p><b>线程安全：</b>本类所有方法线程安全。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 1. 保存新版本
 * Path file = Paths.get("/data/report.pdf");
 * Path versionDir = Paths.get("/data/versions");
 * int version = FileVersionUtil.saveVersion(file, versionDir);
 *
 * // 2. 查询历史版本（按版本号倒序）
 * List<FileVersion> versions = FileVersionUtil.getVersions("report.pdf", versionDir);
 *
 * // 3. 还原到指定版本
 * FileVersionUtil.restoreVersion("report.pdf", 2, versionDir, file);
 * }</pre>
 *
 * @author QI Guang
 * @since 1.0.0
 */
public final class FileVersionUtil {

    private static final Logger log = LoggerFactory.getLogger(FileVersionUtil.class);

    /**
     * 版本文件名中版本号的前缀分隔符。
     */
    private static final String VERSION_MARKER = "_v";

    /**
     * 默认保留的最大版本数。
     * <p>可通过系统属性 {@code -Dfile-version.max-versions=20} 覆盖。
     */
    private static final int DEFAULT_MAX_VERSIONS =
            Integer.getInteger("file-version.max-versions", 10);

    /**
     * 版本锁文件的文件名。
     */
    private static final String LOCK_FILE_NAME = ".version.lock";

    private FileVersionUtil() {
        throw new UnsupportedOperationException(
                "FileVersionUtil is a static utility class and cannot be instantiated");
    }

    // ==================== 保存版本 ====================

    /**
     * 保存文件的新版本（自动递增版本号）。
     *
     * <p><b>并发安全：</b>通过文件锁保证版本号不冲突。
     *
     * @param file       源文件，不允许为 {@code null}，必须存在且为普通文件
     * @param versionDir 版本存储目录，不允许为 {@code null}（不存在时自动创建）
     * @return 新版本号（从 1 开始）
     * @throws IllegalArgumentException 参数非法时抛出
     * @throws UncheckedIOException     文件操作失败时抛出
     */
    public static int saveVersion(Path file, Path versionDir) {
        Objects.requireNonNull(file, "file must not be null");
        Objects.requireNonNull(versionDir, "versionDir must not be null");
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException(
                    "Source is not a regular file: " + file);
        }

        try {
            Files.createDirectories(versionDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create version dir: " + versionDir, e);
        }

        String fileName = file.getFileName().toString();
        String baseName = getBaseName(fileName);
        String extension = getExtension(fileName);

        // 使用文件锁保证版本号读取 + 递增的原子性
        Path lockFile = versionDir.resolve(LOCK_FILE_NAME);
        try (FileChannel channel = FileChannel.open(lockFile,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {

            int newVersion = findMaxVersion(versionDir, baseName, extension) + 1;
            String versionFileName = buildVersionFileName(baseName, newVersion, extension);
            Path target = versionDir.resolve(versionFileName);

            Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);

            if (log.isDebugEnabled()) {
                log.debug("[saveVersion] saved version, file={}, version={}, target={}",
                        fileName, newVersion, target);
            }

            cleanOldVersions(versionDir, baseName, extension);
            return newVersion;
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to save version for: " + fileName, e);
        }
    }

    // ==================== 查询版本 ====================

    /**
     * 查询文件的所有历史版本（按版本号倒序，最新的在前）。
     *
     * @param fileName   文件名（含扩展名），不允许为 {@code null} 或空白
     * @param versionDir 版本存储目录
     * @return 版本列表；目录不存在或无版本时返回空列表
     */
    public static List<FileVersion> getVersions(String fileName, Path versionDir) {
        Objects.requireNonNull(fileName, "fileName must not be null");
        Objects.requireNonNull(versionDir, "versionDir must not be null");
        if (fileName.isEmpty()) {
            throw new IllegalArgumentException("fileName must not be empty");
        }
        if (!Files.isDirectory(versionDir)) {
            return List.of();
        }

        String baseName = getBaseName(fileName);
        String extension = getExtension(fileName);
        Pattern pattern = buildVersionPattern(baseName, extension);
        List<FileVersion> versions = new ArrayList<>();

        try (var stream = Files.list(versionDir)) {
            stream.forEach(path -> {
                Matcher matcher = pattern.matcher(path.getFileName().toString());
                if (!matcher.matches()) {
                    return;
                }
                try {
                    int version = Integer.parseInt(matcher.group(1));
                    versions.add(new FileVersion(
                            version,
                            path,
                            Files.size(path),
                            Files.getLastModifiedTime(path).toInstant()
                    ));
                } catch (IOException | NumberFormatException e) {
                    // 文件可能被并发删除，或版本号超大，跳过
                    log.warn("[getVersions] skip invalid version file: {}", path, e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to list version dir: " + versionDir, e);
        }

        versions.sort(Comparator.comparingInt(FileVersion::version).reversed());
        return versions;
    }

    // ==================== 还原版本 ====================

    /**
     * 还原到指定版本。
     *
     * <p>目标文件的父目录不存在时自动创建。
     *
     * @param fileName   文件名（含扩展名）
     * @param version    版本号（从 1 开始）
     * @param versionDir 版本存储目录
     * @param targetFile 还原目标文件
     * @throws IllegalArgumentException 版本文件不存在或参数非法时抛出
     * @throws UncheckedIOException     文件操作失败时抛出
     */
    public static void restoreVersion(String fileName, int version,
                                      Path versionDir, Path targetFile) {
        Objects.requireNonNull(fileName, "fileName must not be null");
        Objects.requireNonNull(versionDir, "versionDir must not be null");
        Objects.requireNonNull(targetFile, "targetFile must not be null");
        if (version <= 0) {
            throw new IllegalArgumentException("version must be positive, was: " + version);
        }

        String baseName = getBaseName(fileName);
        String extension = getExtension(fileName);
        Path sourcePath = versionDir.resolve(
                buildVersionFileName(baseName, version, extension));
        if (!Files.isRegularFile(sourcePath)) {
            throw new IllegalArgumentException("Version file not found: " + sourcePath);
        }

        try {
            Path parent = targetFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(sourcePath, targetFile, StandardCopyOption.REPLACE_EXISTING);
            if (log.isDebugEnabled()) {
                log.debug("[restoreVersion] restored, fileName={}, version={}, target={}",
                        fileName, version, targetFile);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to restore version: " + fileName + " v" + version, e);
        }
    }

    // ==================== 私有辅助 ====================

    /**
     * 获取文件名的基础部分（不含扩展名）。
     *
     * <p>例：{@code "archive.tar.gz"} → {@code "archive.tar"}，
     * 仅最后一个 {@code .} 之后视为扩展名。
     */
    private static String getBaseName(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    /**
     * 获取文件扩展名（含 {@code .}；无扩展名时返回空串）。
     */
    private static String getExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(dotIndex) : "";
    }

    /**
     * 构建版本文件名：{@code baseName_vN.ext}。
     */
    private static String buildVersionFileName(String baseName, int version, String extension) {
        return baseName + VERSION_MARKER + version + extension;
    }

    /**
     * 构建版本文件名匹配正则：{@code ^baseName_v(\d+)ext$}。
     *
     * <p>使用 {@link Pattern#quote(String)} 转义 baseName 与 extension，
     * 避免其中的特殊字符（如 {@code .}、{@code +}）被误解析。
     */
    private static Pattern buildVersionPattern(String baseName, String extension) {
        return Pattern.compile(
                "^" + Pattern.quote(baseName + VERSION_MARKER)
                        + "(\\d+)"
                        + Pattern.quote(extension)
                        + "$");
    }

    /**
     * 查找目录内指定文件的最大版本号；无版本时返回 0。
     */
    private static int findMaxVersion(Path versionDir, String baseName, String extension)
            throws IOException {
        Pattern pattern = buildVersionPattern(baseName, extension);
        int max = 0;
        try (var stream = Files.list(versionDir)) {
            for (Path path : (Iterable<Path>) stream::iterator) {
                Matcher matcher = pattern.matcher(path.getFileName().toString());
                if (matcher.matches()) {
                    try {
                        int v = Integer.parseInt(matcher.group(1));
                        if (v > max) {
                            max = v;
                        }
                    } catch (NumberFormatException ignored) {
                        // 版本号过大，跳过
                    }
                }
            }
        }
        return max;
    }

    /**
     * 清理旧版本，保留最新的 {@link #DEFAULT_MAX_VERSIONS} 个。
     *
     * <p><b>异常策略：</b>单个版本删除失败记录 WARN 日志但不中断清理。
     */
    private static void cleanOldVersions(Path versionDir, String baseName, String extension) {
        List<FileVersion> versions = getVersions(baseName + extension, versionDir);
        if (versions.size() <= DEFAULT_MAX_VERSIONS) {
            return;
        }
        for (int i = DEFAULT_MAX_VERSIONS; i < versions.size(); i++) {
            Path path = versions.get(i).path();
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                log.warn("[cleanOldVersions] failed to delete: {}", path, e);
            }
        }
    }
}

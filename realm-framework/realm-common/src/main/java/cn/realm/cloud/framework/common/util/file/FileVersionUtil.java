package cn.realm.cloud.framework.common.util.file;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件版本管理工具类
 * <p>支持文件历史版本保存、查询、还原</p>
 *
 * @author QI Guang
 * @version 1.0.0
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public final class FileVersionUtil {

    private static final String VERSION_SUFFIX = "_v";
    private static final int MAX_VERSIONS = 10;

    private FileVersionUtil() {
    }

    /**
     * 保存文件新版本
     *
     * @param file       当前文件
     * @param versionDir 版本存储目录
     * @return 版本号
     */
    public static int saveVersion(File file, String versionDir) throws IOException {
        if (file == null || !file.exists()) {
            throw new IllegalArgumentException("文件不存在");
        }

        File dir = new File(versionDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // 获取当前最大版本号
        int maxVersion = getMaxVersion(file.getName(), versionDir);
        int newVersion = maxVersion + 1;

        // 拷贝文件到版本目录
        String versionFileName = getVersionFileName(file.getName(), newVersion);
        Path targetPath = Paths.get(versionDir, versionFileName);
        Files.copy(file.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        // 清理超出最大数量的旧版本
        cleanOldVersions(file.getName(), versionDir);

        return newVersion;
    }

    /**
     * 获取文件的所有历史版本
     */
    public static List<FileVersion> getVersions(String fileName, String versionDir) {
        List<FileVersion> versions = new ArrayList<>();
        File dir = new File(versionDir);
        if (!dir.exists()) {
            return versions;
        }

        File[] files = dir.listFiles((d, name) -> name.startsWith(getBaseName(fileName) + VERSION_SUFFIX));
        if (files == null) {
            return versions;
        }

        for (File f : files) {
            versions.add(new FileVersion(f));
        }

        versions.sort((a, b) -> Integer.compare(b.version, a.version));
        return versions;
    }

    /**
     * 还原到指定版本
     */
    public static void restoreVersion(String fileName, int version, String versionDir, File targetFile) throws IOException {
        String versionFileName = getVersionFileName(fileName, version);
        Path sourcePath = Paths.get(versionDir, versionFileName);
        if (!Files.exists(sourcePath)) {
            throw new IllegalArgumentException("版本文件不存在: " + versionFileName);
        }
        Files.copy(sourcePath, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private static int getMaxVersion(String fileName, String versionDir) {
        File dir = new File(versionDir);
        if (!dir.exists()) {
            return 0;
        }
        String baseName = getBaseName(fileName);
        File[] files = dir.listFiles((d, name) -> name.startsWith(baseName + VERSION_SUFFIX));
        if (files == null || files.length == 0) {
            return 0;
        }
        int max = 0;
        for (File f : files) {
            String name = f.getName();
            String versionStr = name.substring(name.lastIndexOf(VERSION_SUFFIX) + VERSION_SUFFIX.length());
            if (versionStr.contains(".")) {
                versionStr = versionStr.substring(0, versionStr.lastIndexOf('.'));
            }
            try {
                max = Math.max(max, Integer.parseInt(versionStr));
            } catch (NumberFormatException ignored) {
            }
        }
        return max;
    }

    private static void cleanOldVersions(String fileName, String versionDir) {
        List<FileVersion> versions = getVersions(fileName, versionDir);
        if (versions.size() <= MAX_VERSIONS) {
            return;
        }
        // 删除最旧的版本（保留最新的 MAX_VERSIONS 个）
        for (int i = MAX_VERSIONS; i < versions.size(); i++) {
            versions.get(i).file.delete();
        }
    }

    private static String getBaseName(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    private static String getVersionFileName(String fileName, int version) {
        String baseName = getBaseName(fileName);
        String ext = "";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            ext = fileName.substring(dotIndex);
        }
        return baseName + VERSION_SUFFIX + version + ext;
    }

    /**
     * 文件版本信息
     */
    public static class FileVersion {
        public final File file;
        public final int version;
        public final long size;
        public final String createTime;

        public FileVersion(File file) {
            this.file = file;
            this.size = file.length();
            this.createTime = LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(file.lastModified()),
                    java.time.ZoneId.systemDefault()
            ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String name = file.getName();
            String versionStr = name.substring(name.lastIndexOf(VERSION_SUFFIX) + VERSION_SUFFIX.length());
            if (versionStr.contains(".")) {
                versionStr = versionStr.substring(0, versionStr.lastIndexOf('.'));
            }
            this.version = Integer.parseInt(versionStr);
        }
    }
}

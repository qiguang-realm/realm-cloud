package cn.realm.cloud.framework.common.util.file;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 文件分片上传/断点续传工具类
 *
 * @author QI Guang
 * @version 1.0.0
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public final class FileChunkUtil {

    /**
     * 默认分片大小：5MB
     */
    private static final long DEFAULT_CHUNK_SIZE = 5 * 1024 * 1024L;

    /**
     * 分片上传进度存储目录
     */
    private static final String UPLOAD_TEMP_DIR = System.getProperty("java.io.tmpdir") + "/chunk_upload/";

    private FileChunkUtil() {
    }

    /**
     * 分片上传初始化
     *
     * @param fileName  原始文件名
     * @param totalSize 文件总大小
     * @param chunkSize 分片大小（字节）
     * @return 上传任务ID
     */
    public static UploadTask initUpload(String fileName, long totalSize, Long chunkSize) {
        if (chunkSize == null || chunkSize <= 0) {
            chunkSize = DEFAULT_CHUNK_SIZE;
        }
        String taskId = UUID.randomUUID().toString().replace("-", "");
        int totalChunks = (int) Math.ceil((double) totalSize / chunkSize);
        return new UploadTask(taskId, fileName, totalSize, chunkSize, totalChunks);
    }

    /**
     * 上传分片
     *
     * @param taskId     上传任务ID
     * @param chunkIndex 分片索引（从0开始）
     * @param chunkData  分片数据
     * @return 是否上传成功
     */
    public static boolean uploadChunk(String taskId, int chunkIndex, byte[] chunkData) throws IOException {
        String chunkDir = UPLOAD_TEMP_DIR + taskId + "/";
        File dir = new File(chunkDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        File chunkFile = new File(chunkDir, "chunk_" + chunkIndex);
        try (FileOutputStream fos = new FileOutputStream(chunkFile)) {
            fos.write(chunkData);
        }
        return true;
    }

    /**
     * 合并分片为完整文件
     *
     * @param taskId     上传任务ID
     * @param targetFile 目标文件
     * @return 合并后的文件
     */
    public static File mergeChunks(String taskId, File targetFile) throws IOException {
        String chunkDir = UPLOAD_TEMP_DIR + taskId + "/";
        File dir = new File(chunkDir);
        if (!dir.exists()) {
            throw new IllegalArgumentException("分片目录不存在: " + chunkDir);
        }

        // 确保目标文件父目录存在
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (FileOutputStream fos = new FileOutputStream(targetFile)) {
            int chunkIndex = 0;
            File chunkFile;
            while ((chunkFile = new File(chunkDir, "chunk_" + chunkIndex)).exists()) {
                Files.copy(chunkFile.toPath(), fos);
                chunkIndex++;
            }
        }

        // 清理临时分片文件
        cleanTempFiles(taskId);

        return targetFile;
    }

    /**
     * 获取上传进度
     */
    public static UploadProgress getProgress(String taskId) {
        String chunkDir = UPLOAD_TEMP_DIR + taskId + "/";
        File dir = new File(chunkDir);
        if (!dir.exists()) {
            return new UploadProgress(0, 0);
        }

        File[] chunks = dir.listFiles((d, name) -> name.startsWith("chunk_"));
        if (chunks == null) {
            return new UploadProgress(0, 0);
        }

        int uploaded = chunks.length;
        int total = uploaded; // 需要从任务记录中获取总片数
        return new UploadProgress(uploaded, total);
    }

    /**
     * 取消上传（清理临时文件）
     */
    public static void cancelUpload(String taskId) {
        cleanTempFiles(taskId);
    }

    private static void cleanTempFiles(String taskId) {
        String chunkDir = UPLOAD_TEMP_DIR + taskId + "/";
        try {
            Files.walk(Paths.get(chunkDir))
                    .sorted((a, b) -> -a.compareTo(b))
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException ignored) {
        }
    }

    // ===== 内部类 =====

    public static class UploadTask {
        public final String taskId;
        public final String fileName;
        public final long totalSize;
        public final long chunkSize;
        public final int totalChunks;

        public UploadTask(String taskId, String fileName, long totalSize, long chunkSize, int totalChunks) {
            this.taskId = taskId;
            this.fileName = fileName;
            this.totalSize = totalSize;
            this.chunkSize = chunkSize;
            this.totalChunks = totalChunks;
        }
    }

    public static class UploadProgress {
        public final int uploadedChunks;
        public final int totalChunks;
        public final double progress;

        public UploadProgress(int uploadedChunks, int totalChunks) {
            this.uploadedChunks = uploadedChunks;
            this.totalChunks = totalChunks;
            this.progress = totalChunks > 0 ? (double) uploadedChunks / totalChunks * 100 : 0;
        }
    }
}

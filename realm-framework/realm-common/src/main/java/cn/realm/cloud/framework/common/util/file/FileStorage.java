package cn.realm.cloud.framework.common.util.file;

import java.io.InputStream;
import java.util.Map;

/**
 * 统一文件存储抽象接口
 * <p>屏蔽不同云存储服务商的差异，支持 OSS、MinIO、本地文件系统等</p>
 *
 * @author QI Guang
 * @version 1.0.0
 */
public interface FileStorage {

    /**
     * 上传文件
     *
     * @param bucket   存储桶/目录
     * @param key      文件路径/键
     * @param data     文件数据
     * @param metadata 元数据（可选）
     * @return 文件访问URL
     */
    String upload(String bucket, String key, byte[] data, Map<String, String> metadata);

    /**
     * 上传文件（流式）
     */
    String upload(String bucket, String key, InputStream data, Map<String, String> metadata);

    /**
     * 下载文件
     */
    byte[] download(String bucket, String key);

    /**
     * 下载文件（流式）
     */
    InputStream downloadStream(String bucket, String key);

    /**
     * 删除文件
     */
    boolean delete(String bucket, String key);

    /**
     * 判断文件是否存在
     */
    boolean exists(String bucket, String key);

    /**
     * 获取文件元数据
     */
    Map<String, String> getMetadata(String bucket, String key);

    /**
     * 生成临时访问URL（带有效期）
     */
    String generatePresignedUrl(String bucket, String key, long expirationSeconds);

    /**
     * 获取存储类型名称
     */
    String getStorageType();
}

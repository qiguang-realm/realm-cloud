package cn.realm.cloud.framework.common.util.file;

/**
 * 存储类型。
 *
 * @author QI Guang
 * @since 1.0.0
 */
public enum StorageType {

    /**
     * 本地文件系统
     */
    LOCAL,

    /**
     * 阿里云 OSS
     */
    ALIYUN_OSS,

    /**
     * 腾讯云 COS
     */
    TENCENT_COS,

    /**
     * 华为云 OBS
     */
    HUAWEI_OBS,

    /**
     * MinIO（S3 兼容）
     */
    MINIO,

    /**
     * AWS S3
     */
    AWS_S3,

    /**
     * 七牛云 Kodo
     */
    QINIU_KODO,

    /**
     * 其他自定义存储
     */
    CUSTOM
}

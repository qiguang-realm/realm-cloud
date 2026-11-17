📁 realm-object-storage-spring-boot-starter 📁 详细包结构全景

realm-object-storage-spring-boot-starter/
└── src/main/java/cn/realm/cloud/framework/objectstorage/
│
├── package-info.java                     # [必须] 模块定位与架构红线声明
│
├── autoconfigure/                        # Spring Boot 3 自动配置入口
│   ├── ObjectStorageAutoConfiguration.java   # [总控] 全局开关与核心抽象装配
│   ├── S3AutoConfiguration.java              # [默认] AWS SDK v2 S3Client 装配
│   ├── MinioAutoConfiguration.java           # [可选] MinIO 方言适配器装配
│   └── AliyunOssAutoConfiguration.java       # [可选] 阿里云 OSS 方言适配器装配
│
├── core/                                 # 协议中立的存储抽象（零厂商依赖）
│   ├── ObjectStorageOperations.java      # [核心接口] 上传、下载、删除、存在性检查、预签名 URL
│   ├── ObjectStorageTemplate.java        # [模板类] 基于 ObjectStorageOperations 的便捷门面
│   ├── StorageObject.java                # 领域模型：对象键、元数据、内容类型、大小
│   └── ObjectStorageProperties.java      # 通用配置属性（默认桶、路径前缀、分片阈值等）
│
├── dialect/                              # 协议/厂商方言实现（均为可选适配器）
│   ├── s3/
│   │   └── S3ObjectStorage.java          # 基于 AWS SDK v2 S3Client 的默认实现
│   ├── minio/
│   │   └── MinioObjectStorage.java       # 基于 MinIO SDK 的方言实现
│   └── aliyun/
│       └── AliyunOssObjectStorage.java   # 基于阿里云 OSS SDK 的方言实现
│
├── support/                              # 支撑工具（协议中立）
│   ├── ObjectKeyGenerator.java           # 对象键生成策略（按日期、租户、UUID 等）
│   └── ContentTypeDetector.java          # 基于文件魔数或扩展名的 MIME 类型推断
│
└── metrics/                              # 可观测性
└── ObjectStorageMetrics.java         # 上传/下载耗时、成功率、流量（基于 Micrometer）

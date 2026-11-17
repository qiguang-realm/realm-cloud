📁 realm-utils 详细包结构全景

realm-utils/
└── src/main/java/cn/realm/cloud/framework/utils/
│
├── package-info.java             # [必须] 模块全局定位与架构红线声明
│
├── json/                         # JSON 处理辅助
│   ├── package-info.java
│   ├── JsonUtils.java            # 静态方法封装：toJson()、parseObject()、parseArray()
│   └── JsonTypeReference.java    # 泛型反序列化辅助（配合 Jackson 的 TypeReference）
│
├── date/                         # 日期与时间处理（基于 Java 17 java.time）
│   ├── package-info.java
│   ├── DateUtils.java            # 格式化、解析、时间加减、获取当月起止时间
│   └── DateTimeUtils.java        # 时间戳与 LocalDateTime 的互转
│
├── string/                       # 字符串处理
│   ├── package-info.java
│   └── StringUtils.java          # 封装的 isEmpty、isBlank、下划线转驼峰、脱敏处理
│
├── collection/                   # 集合与 Map 增强
│   ├── package-info.java
│   ├── CollectionUtils.java      # 集合判空、交并差集计算、分页截取
│   └── MapUtils.java             # Map 判空、转换、多值合并
│
├── id/                           # 全局 ID 生成（无状态算法）
│   ├── package-info.java
│   ├── SnowflakeIdGenerator.java # 雪花算法生成器（需传入 workerId）
│   ├── NanoIdGenerator.java      # 短随机 ID 生成器
│   └── UuidUtils.java            # 生成去连字符的 UUID、有序 UUID
│
├── crypto/                       # 加密与哈希摘要
│   ├── package-info.java
│   ├── DigestUtils.java          # MD5、SHA-256、HMAC 摘要计算
│   ├── Base64Utils.java          # Base64 编解码
│   └── AesUtils.java             # AES 对称加解密
│
├── io/                           # IO 与文件流处理
│   ├── package-info.java
│   ├── StreamUtils.java          # InputStream 转 String / byte[]
│   ├── FileUtils.java            # 文件大小格式化、安全创建目录、读取文件后缀
│   └── FileTypeUtils.java        # 基于魔数校验文件真实类型（防伪造）
│
├── reflect/                      # 反射与类操作
│   ├── package-info.java
│   └── ReflectUtils.java         # 获取泛型类型、实例化对象、获取字段值
│
├── concurrent/                   # 线程与并发上下文
│   ├── package-info.java
│   ├── ThreadUtils.java          # 获取当前线程信息、优雅休眠
│   └── ThreadLocalUtils.java     # 基于 TransmittableThreadLocal 的上下文传递封装
│
├── convert/                      # 数据转换
│   ├── package-info.java
│   └── ConvertUtils.java         # 基础类型转换（String -> Integer, List -> Set）
│
└── validate/                     # 轻量级断言与校验（非 Bean Validation）
├── package-info.java
└── AssertUtils.java          # 条件断言（如 notNull、isTrue），抛出 BusinessException

📁 realm-file-spring-boot-starter 📁 详细包结构全景

realm-file-spring-boot-starter/
└── src/main/java/cn/realm/cloud/framework/file/
│
├── package-info.java                     # [必须] 模块定位与架构红线声明
│
├── autoconfigure/                        # Spring Boot 3 自动配置入口（独立子包）
│   ├── FileAutoConfiguration.java        # [总控] 全局开关与核心抽象装配
│   ├── FastExcelAutoConfiguration.java   # [默认] FastExcel 引擎的条件装配
│   ├── PoiAutoConfiguration.java         # [可选] POI 引擎的条件装配（复杂格式/公式）
│   └── FileTaskAutoConfiguration.java    # [可选] 异步任务骨架的条件装配
│
├── core/                                 # 协议中立的文件处理抽象（零引擎依赖）
│   ├── FileEngine.java                   # [核心 SPI] 读、写、流式处理接口
│   ├── ReadOptions.java                  # 读选项（表头行号、批量大小、最大行数）
│   ├── WriteOptions.java                 # 写选项（是否内存模式、自动列宽）
│   └── FileFormat.java                   # 文件格式枚举（XLSX、XLS、CSV、PDF）
│
├── annotation/                           # 注解驱动的文件模型
│   ├── FileColumn.java                   # 列定义：表头、顺序、格式、字典映射
│   ├── FileImport.java                   # 标记可导入的模型类
│   └── FileExport.java                   # 标记可导出的模型类
│
├── engine/                               # 具体引擎实现（出站适配器，均为可选）
│   ├── fastexcel/
│   │   └── FastExcelFileEngine.java      # 基于 FastExcel（Fesod）的流式实现
│   └── poi/
│       └── PoiFileEngine.java            # 基于 POI 的复杂格式实现
│
├── validation/                           # 导入校验骨架
│   ├── ImportValidator.java              # [SPI] 校验器接口
│   ├── ValidationError.java              # 结构化错误（行号、列名、错误码）
│   └── builtin/                          # 内置校验器（必填、长度、字典映射、唯一性）
│
├── task/                                 # 异步任务骨架（可选）
│   ├── FileTask.java                     # 任务模型（状态、进度、结果摘要）
│   ├── FileTaskRepository.java           # [SPI] 任务持久化接口
│   └── FileTaskService.java              # 任务编排（提交、执行、状态更新）
│
└── support/                              # 支撑工具（协议中立）
├── FileTypeDetector.java             # 基于魔数（Magic Bytes）的文件真实类型检测
└── FileNameSanitizer.java            # 文件名清理（防止路径遍历 CWE-22）

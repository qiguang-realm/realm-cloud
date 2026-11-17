📁 realm-biz-location-spring-boot-starter 📁 详细包结构全景

realm-biz-location-spring-boot-starter/
└── src/main/java/cn/realm/cloud/framework/biz/location/
│
├── package-info.java                     # [必须] 模块定位与架构红线声明
│
├── LocationFacade.java                   # [公共 API] 对外的统一查询门面
├── IpLocationDto.java                    # [公共 API] IP 解析结果（国家、省、市、ISP）
├── CityDto.java                          # [公共 API] 城市信息（编码、名称、经纬度等）
│
├── autoconfigure/                        # Spring Boot 3 自动配置入口
│   ├── LocationAutoConfiguration.java    # [总控] 门面与字典的装配
│   ├── Ip2RegionAutoConfiguration.java   # [核心] xdb 文件加载与 Searcher 装配
│   └── WebIpAutoConfiguration.java       # [可选] Servlet 环境下的真实 IP 提取工具
│
├── internal/                             # 私有实现（Spring Modulith 隔离）
│   │
│   ├── LocationFacadeImpl.java           # [实现] 实现 LocationFacade，编排内部查询
│   │
│   ├── ip2region/                        # IP 库加载与查询
│   │   ├── Ip2RegionManager.java         # 管理 xdb 文件的读取、缓存加载（全量/索引）
│   │   └── Ip2RegionSearcher.java        # 封装 ip2region 的 Searcher 查询
│   │
│   ├── city/                             # 城市字典管理
│   │   ├── CityDictionary.java           # 城市数据的内存存储（Map）
│   │   └── CityDictionaryLoader.java     # 从 resources/cities.json 加载数据
│   │
│   ├── web/                              # 入站适配器（可选）
│   │   └── ClientIpResolver.java         # 从 HttpServletRequest 提取真实 IP（防范伪造）
│   │
│   └── cache/                            # 缓存增强（可选）
│       └── CaffeineLocationCache.java    # 基于 Caffeine 的 IP 查询结果缓存
│
└── properties/                           # 配置属性类
└── LocationProperties.java           # xdb 路径、缓存策略、代理头信任配置


realm-biz-location-spring-boot-starter/
├── src/main/java/...
├── src/main/resources/
│   ├── ip2region/
│   │   └── ip2region.xdb                   # [必须] 离线 IP 库文件
│   └── city/
│       └── cities.json                     # [必须] 内置城市字典数据
│
└── src/main/resources/META-INF/spring/
└── org.springframework.boot.autoconfigure.AutoConfiguration.imports
# [必须] Spring Boot 3 自动配置注册文件

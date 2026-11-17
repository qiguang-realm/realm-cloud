📁 realm-data-jpa-spring-boot-starter 📁 详细包结构全景

realm-data-jpa-spring-boot-starter/
└── src/main/java/cn/realm/cloud/framework/data/jpa/
│
├── package-info.java                     # [必须] 模块定位与架构红线声明
│
├── autoconfigure/                        # Spring Boot 3 自动配置入口
│   ├── RealmJpaAutoConfiguration.java    # [总控] 全局开关与 EntityManagerFactory 定制
│   ├── RealmJpaTransactionAutoConfiguration.java # [事务] JpaTransactionManager 定型（优先于 JDBC 默认）
│   ├── RealmJpaRepositoriesAutoConfiguration.java # [Repository] 扫描默认约定
│   └── RealmJpaAuditingAutoConfiguration.java     # [审计] 可选审计接入（默认关闭）
│
├── properties/                           # 配置属性类
│   ├── RealmJpaProperties.java           # 全局开关、Repository 扫描包路径
│   └── RealmJpaAuditingProperties.java   # 审计开关、字段名映射、AuditorAware 引用
│
├── support/                              # 可覆盖的定制点
│   ├── RealmEntityManagerFactoryCustomizer.java  # Hibernate 属性追加（不覆盖 spring.jpa.*）
│   └── RealmJpaTransactionManagerCustomizer.java # 对 JpaTransactionManager 的 Realm 定制
│
├── audit/                                # 可选审计实现
│   └── RealmAuditorAware.java            # 基于 SubjectContext 的 AuditorAware 默认实现
│
└── util/                                 # 公共转换工具
└── JpaPageUtils.java                 # PageParam/PageResult 与 Spring Data Page 的转换

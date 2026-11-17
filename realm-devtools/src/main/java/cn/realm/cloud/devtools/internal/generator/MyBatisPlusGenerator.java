package cn.realm.cloud.devtools.internal.generator;

import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.config.rules.DbColumnType;
import com.baomidou.mybatisplus.generator.config.rules.NamingStrategy;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.type.JdbcType;

import java.util.Collections;
import java.util.Scanner;

/**
 * MyBatis-Plus 代码生成器（基于 FastAutoGenerator，适配 3.5.14+）
 * <p>
 * 通过控制台交互，根据指定的数据库表生成 Entity、Mapper、Service、Controller 等代码。
 * 支持自定义输出目录、包名、表前缀，并使用 Freemarker 模板引擎。
 * </p>
 *
 * @author QI Guang
 */
@Slf4j
public class MyBatisPlusGenerator {

    // ==================== 数据库配置（切换时请注释/取消注释对应组） ====================

    // ---------- MySQL 配置（当前启用） ----------
    private static final String JDBC_URL =
            "jdbc:mysql://127.0.0.1:3306/realm?useUnicode=true&characterEncoding=utf8&sslMode=DISABLED&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&nullCatalogMeansCurrent=true&rewriteBatchedStatements=true";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "root";

    // ---------- Oracle 配置（已注释，切换时启用） ----------
    // private static final String JDBC_URL = "jdbc:oracle:thin:@192.168.2.36:1521/kyc_n";
    // private static final String USERNAME = "kyc_cloud";
    // private static final String PASSWORD = "kyc_cloud_dev";

    // ==================== 项目路径常量 ====================
    // 当前工作目录（即运行 main 方法的项目根目录）
    private static final String PROJECT_ROOT = System.getProperty("user.dir");

    // 目标模块的相对路径（当前为 realm-workforce-identity 下的 server 子模块）
    private static final String MODULE_PATH = "/realm-workforce-identity/realm-workforce-identity-server";

    // Java 源码输出目录
    private static final String JAVA_OUTPUT_DIR = PROJECT_ROOT + MODULE_PATH + "/src/main/java";
    // 资源文件输出目录
    private static final String RESOURCES_OUTPUT_DIR = PROJECT_ROOT + MODULE_PATH + "/src/main/resources";

    public static void main(String[] args) {
        try {
            // 控制台交互：获取要生成的表名（多个用逗号分隔）和模块名（作为子包名）
            String tableNames = scanner("表名（多个用英文逗号分隔，例如：iam_user,iam_role）");
            String moduleName = scanner("模块名/子包名（例如：user, role, department）");

            // 打印输出路径以便确认
            System.out.println("📂 Java 输出目录：" + JAVA_OUTPUT_DIR);
            System.out.println("📂 XML 输出目录：" + RESOURCES_OUTPUT_DIR + "/mapper/" + moduleName);

            // 构建快速生成器并执行（链式调用）
            FastAutoGenerator.create(JDBC_URL, USERNAME, PASSWORD)
                    // ---------- 自定义类型转换器（将 TINYINT 强制映射为 Integer） ----------
                    .dataSourceConfig(builder -> builder
                            .typeConvertHandler((globalConfig, typeRegistry, metaInfo) -> {
                                // IAM 模块中 status、is_deleted 等字段多为 tinyint，映射为 Integer 方便处理
                                if (metaInfo.getJdbcType() == JdbcType.TINYINT) {
                                    return DbColumnType.INTEGER;
                                }
                                return typeRegistry.getColumnType(metaInfo);
                            })
                    )
                    // ---------- 全局配置 ----------
                    .globalConfig(builder -> {
                        builder.author("QI Guang")                    // 作者名
                                .outputDir(JAVA_OUTPUT_DIR)            // Java 文件输出根目录
                                .enableSpringdoc()                     // 启用 Springdoc (OpenAPI 3)
                                // .fileOverride()                     // 建议保持注释，避免误覆盖手写代码
                                .disableOpenDir();                     // 生成后不自动打开文件夹
                    })
                    // ---------- 包配置 ----------
                    .packageConfig(builder -> {
                        // 父包名：cn.realm.cloud.workforce.identity
                        builder.parent("cn.realm.cloud.workforce.identity")
                                // 模块名（子包名），例如输入 user，则完整包为 ...workforce.identity.user
                                .moduleName(moduleName)
                                .entity("model.entity")                       // 实体类子包名
                                .service("service")                           // Service 接口子包名
                                .serviceImpl("service.impl")                  // Service 实现类子包名
                                .mapper("mapper")                             // Mapper 接口子包名
                                .controller("controller")                     // Controller 子包名
                                // 自定义 XML 文件输出路径（默认在 resources/mapper/ 下）
                                .pathInfo(Collections.singletonMap(
                                        OutputFile.xml,
                                        RESOURCES_OUTPUT_DIR + "/mapper/" + moduleName
                                ));
                    })
                    // ---------- 策略配置（核心） ----------
                    .strategyConfig(builder -> {
                        // ------------------ 表级策略 ------------------
                        // 指定需要生成代码的表名（用户输入，逗号分割）
                        builder.addInclude(tableNames.split(","))
                                // 设置表名前缀，生成实体类时会自动去除这些前缀
                                // IAM 模块的表统一以 "iam_" 开头（如 iam_user -> User）
                                // 如果需要去掉特定子模块前缀，也可以加入 moduleName + "_"（例如 user_role -> Role）
                                .addTablePrefix("iam_", moduleName + "_")

                                // ------------------ 实体类策略 ------------------
                                .entityBuilder()
                                .enableLombok()                           // 使用 Lombok 注解
                                .enableTableFieldAnnotation()             // 字段添加 @TableField 注解
                                .naming(NamingStrategy.underline_to_camel) // 表名转换：下划线转驼峰
                                .columnNaming(NamingStrategy.underline_to_camel) // 字段名转换：下划线转驼峰

                                // ------------------ Controller 策略 ------------------
                                .controllerBuilder()
                                .enableRestStyle()                       // @RestController
                                .enableHyphenStyle()                     // 路径连字符（如 /user-role）

                                // ------------------ Service 策略 ------------------
                                .serviceBuilder()
                                .formatServiceFileName("%sService")
                                .formatServiceImplFileName("%sServiceImpl")

                                // ------------------ Mapper 策略 ------------------
                                .mapperBuilder()
                                .mapperAnnotation(org.apache.ibatis.annotations.Mapper.class) // 添加 @Mapper
                                .formatMapperFileName("%sMapper")
                                .formatXmlFileName("%sMapper");
                    })
                    // ---------- 模板引擎配置 ----------
                    .templateEngine(new FreemarkerTemplateEngine())
                    // ---------- 执行生成 ----------
                    .execute();

            log.info("🎉 代码生成完成！请检查生成目录：{}", JAVA_OUTPUT_DIR);

        } catch (Exception e) {
            log.error("❌ 代码生成失败：{}", e.getMessage(), e);
            System.err.println("生成失败，错误信息：" + e.getMessage());
        }
    }

    // ==================== 控制台交互工具 ====================

    private static String scanner(String tip) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("请输入" + tip + "：");
        if (scanner.hasNext()) {
            String ipt = scanner.next().trim();
            if (StringUtils.isNotEmpty(ipt)) {
                return ipt;
            }
        }
        throw new MybatisPlusException("输入不能为空，请重新运行并正确输入！");
    }
}

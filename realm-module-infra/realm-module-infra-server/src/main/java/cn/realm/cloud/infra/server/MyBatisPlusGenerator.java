package cn.realm.cloud.infra.server;

import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.config.rules.NamingStrategy;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;
import lombok.extern.slf4j.Slf4j;

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
            "jdbc:mysql://127.0.0.1:3306/database?useSSL=true&allowPublicKeyRetrieval=true" +
                    "&useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai" +
                    "&rewriteBatchedStatements=true";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "root";

    // ---------- Oracle 配置（已注释，切换时启用） ----------
    // private static final String JDBC_URL = "jdbc:oracle:thin:@192.168.2.36:1521/kyc_n";
    // private static final String USERNAME = "kyc_cloud";
    // private static final String PASSWORD = "kyc_cloud_dev";

    // ==================== 项目路径常量 ====================
    // 当前工作目录（即运行 main 方法的项目根目录）
    private static final String PROJECT_ROOT = System.getProperty("user.dir");
    // 目标模块的相对路径（当前为 realm-module-system 下的 server 子模块）
    private static final String MODULE_PATH = "/realm-module-system/realm-module-system-server";
    // 目标模块的相对路径（当前为 realm-module-infra 下的 server 子模块）
//    private static final String MODULE_PATH = "/realm-module-infra/realm-module-infra-server";
    // Java 源码输出目录
    private static final String JAVA_OUTPUT_DIR = PROJECT_ROOT + MODULE_PATH + "/src/main/java";
    // 资源文件输出目录
    private static final String RESOURCES_OUTPUT_DIR = PROJECT_ROOT + MODULE_PATH + "/src/main/resources";

    public static void main(String[] args) {
        try {
            // 控制台交互：获取要生成的表名（多个用逗号分隔）和模块名
            String tableNames = scanner("表名（多个用英文逗号分隔，例如：system_user,system_order）");
            String moduleName = scanner("模块名（例如：user）");

            // 打印输出路径以便确认
            System.out.println("📂 Java 输出目录：" + JAVA_OUTPUT_DIR);
            System.out.println("📂 XML 输出目录：" + RESOURCES_OUTPUT_DIR + "/mapper/" + moduleName);

            // 构建快速生成器并执行（链式调用）
            FastAutoGenerator.create(JDBC_URL, USERNAME, PASSWORD)
                    // ---------- 全局配置 ----------
                    .globalConfig(builder -> {
                        builder.author("QI Guang")                    // 作者名
                                .outputDir(JAVA_OUTPUT_DIR)            // Java 文件输出根目录
//                                .enableSwagger()                      // 启用 Swagger2 注解（实体类上添加 @ApiModel 等）
                                .enableSpringdoc()                // 若项目使用 Springdoc（OpenAPI 3），则启用这行，同时注释掉 .enableSwagger()
                                // .fileOverride()                   // 是否覆盖已有文件（默认为 false，建议生产环境保持注释，避免误覆盖手写代码）
                                .disableOpenDir();                   // 生成后不自动打开文件夹（保持控制台干净）
                    })
                    // ---------- 包配置 ----------
                    .packageConfig(builder -> {
                        builder.parent("cn.realm.cloud.system.server.module") // 父包名（所有生成代码的根包）  如果必须加一层，用 module 或 domain 替代 business，语义更清晰。
                                .moduleName(moduleName)                       // 模块名，会拼接到父包后面作为子包（如 ...business.user）
                                .entity("model.entity")                             // 实体类子包名
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
                                // 设置表名前缀，生成实体类时会自动去除这些前缀（支持多个）
                                // 此处会去除 "system_" 前缀，以及 "模块名_" 前缀（例如 "user_"）
                                // 例如表名 "system_user_role" → 生成实体类 "UserRole"
                                .addTablePrefix("system_", moduleName + "_")
                                // ------------------ 实体类策略 ------------------
                                .entityBuilder()
                                .enableLombok()                           // 使用 Lombok 注解（@Data, @Accessors 等）
                                .enableTableFieldAnnotation()             // 给字段添加 @TableField 注解（映射数据库字段）
                                .naming(NamingStrategy.underline_to_camel) // 表名转换策略：下划线转驼峰（如 user_role → UserRole）
                                .columnNaming(NamingStrategy.underline_to_camel) // 字段名转换策略：下划线转驼峰（如 user_name → userName）
                                // ------------------ Controller 策略 ------------------
                                .controllerBuilder()
                                .enableRestStyle()                       // 生成 @RestController 而非 @Controller
                                .enableHyphenStyle()                     // 请求路径使用连字符（如 /user-role 而非 /userRole）
                                // ------------------ Service 策略 ------------------
                                .serviceBuilder()
                                .formatServiceFileName("%sService")         // Service 接口命名规则（例如 UserService）
                                .formatServiceImplFileName("%sServiceImpl") // Service 实现类命名规则（例如 UserServiceImpl）
                                // ------------------ Mapper 策略 ------------------
                                .mapperBuilder()
//                                .enableBaseResultMap()                   // 生成 BaseResultMap（MyBatis 的 resultMap 配置）
//                                .enableBaseColumnList()                  // 生成 BaseColumnList（MyBatis 的 sql 片段，包含所有字段）
                                .formatMapperFileName("%sMapper")        // Mapper 接口命名规则（例如 UserMapper）
                                .formatXmlFileName("%sMapper");          // Mapper XML 文件命名规则（例如 UserMapper.xml）
                    })
                    // ---------- 模板引擎配置 ----------
                    .templateEngine(new FreemarkerTemplateEngine()) // 使用 Freemarker 渲染模板（也可改为 VelocityTemplateEngine）
                    // ---------- 执行生成 ----------
                    .execute();

            log.info("🎉 代码生成完成！请检查生成目录：{}", JAVA_OUTPUT_DIR);

        } catch (Exception e) {
            log.error("❌ 代码生成失败：{}", e.getMessage(), e);
            System.err.println("生成失败，错误信息：" + e.getMessage());
        }
    }

    // ==================== 控制台交互工具 ====================

    /**
     * 读取控制台输入，不允许为空。
     *
     * @param tip 提示信息
     * @return 用户输入的字符串
     * @throws MybatisPlusException 如果输入为空
     */
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

package cn.realm.cloud.system.server.document.internal.application;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文档下载应用服务。
 *
 * <p>负责模板白名单校验与 classpath 资源定位，不直接写 HTTP 响应——
 * 响应头设置与流拷贝由入站适配器（{@code DocumentAdminController}）完成，
 * 使本类可脱离 Servlet 环境进行单元测试。
 *
 * <p><b>维护约束：</b>白名单 {@link #ALLOWED_TEMPLATES} 与
 * {@code DocumentAdminController} 中各端点传入的 {@code resourceName}
 * 必须保持一致。新增模板时需同时做两件事：在本白名单加入文件名、
 * 在 Controller 补一个端点。只改一处会在运行时抛“非法的模板文件名”
 * 或“模板不存在”。
 *
 * @author QI Guang
 */
@Service
public class TemplateDownloadService {

    private static final String TEMPLATE_DIR = "templates/";

    private static final List<String> ALLOWED_TEMPLATES = List.of(
            "user_import_template.xlsx",
            "dept_import_template.xlsx",
            "order_export_template.xlsx",
            "customer_statement_template.xlsx",
            "user_manual.pdf"
    );

    /**
     * 校验文件名是否在白名单内，且不含路径遍历字符。
     *
     * <p>白名单是主防线；路径遍历检查是纵深防御——白名单本身已排除带
     * 分隔符的项，但保留该检查可在白名单被误改时多一层拦截。
     *
     * @param fileName 待校验的文件名；{@code null} 或空白时返回 {@code false}
     * @return 文件名合法且在白名单内时为 {@code true}
     */
    public boolean isValidFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            return false;
        }
        return ALLOWED_TEMPLATES.contains(fileName);
    }

    /**
     * 获取模板资源。
     *
     * @param fileName 白名单中的模板文件名
     * @return 可读取的 classpath 资源
     * @throws IllegalArgumentException 文件名不在白名单内
     * @throws IllegalStateException    白名单中有该文件名，但 classpath 下资源缺失
     *                                  （属于部署问题，通常意味着打包时漏了模板文件）
     */
    public Resource getTemplateResource(String fileName) {
        if (!isValidFileName(fileName)) {
            throw new IllegalArgumentException("非法的模板文件名: " + fileName);
        }
        ClassPathResource resource = new ClassPathResource(TEMPLATE_DIR + fileName);
        if (!resource.exists()) {
            throw new IllegalStateException("模板不存在: " + fileName);
        }
        return resource;
    }
}

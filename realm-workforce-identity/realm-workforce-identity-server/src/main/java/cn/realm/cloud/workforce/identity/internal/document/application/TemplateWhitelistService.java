package cn.realm.cloud.workforce.identity.internal.document.application;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 模板白名单服务。
 *
 * <p><b>职责：</b>管理可下载模板的<b>白名单</b>，并根据白名单定位 classpath 资源。
 * 本类<b>不负责</b> HTTP 响应写出——响应头设置与流拷贝由入站适配器
 * （{@code DocumentAdminController}）委托 {@code FileResponseUtils} 完成，
 * 使本类可脱离 Servlet 环境进行单元测试。
 *
 * <p><b>为什么叫 {@code TemplateWhitelistService} 而非 {@code TemplateDownloadService}：</b>
 * 本类不做任何下载动作，它的核心价值是<b>安全边界</b>——白名单校验。
 * 命名直接点明“白名单”，安全审计时无需打开源码即可定位校验逻辑；
 * “Download”一词会把读者引向错误的方向（以为这里有 HTTP 写出），
 * 而真正的写出逻辑在 {@code realm-web} 的 {@code FileResponseUtils}。
 *
 * <p><b>白名单是主防线：</b>模板资源名一旦不在白名单内，直接拒绝，
 * 不允许通过路径遍历（{@code ..}、{@code /}、{@code \}）绕过。
 * 路径遍历检查是纵深防御——白名单本身已排除带分隔符的项，
 * 但保留该检查可在白名单被误改时多一层拦截。
 *
 * <p><b>维护约束：</b>白名单 {@link #ALLOWED_TEMPLATES} 与
 * {@code DocumentAdminController} 中各端点传入的 {@code resourceName}
 * 必须保持一致。新增模板时需同时做两件事：
 * <ol>
 *     <li>在 {@link #ALLOWED_TEMPLATES} 加入文件名</li>
 *     <li>在 {@code DocumentAdminController} 补一个端点</li>
 * </ol>
 * 只改一处会在运行时抛“非法的模板文件名”或“模板不存在”。
 *
 * <p><b>为什么白名单用 {@link Set} 而非 {@link java.util.List}：</b>
 * 白名单的核心操作是 {@code contains} 查找，{@link Set} 提供 O(1) 的查找复杂度，
 * 语义上“白名单”也更接近“集合”而非“序列”。当前白名单条目较少（&lt; 20），
 * 性能差异可忽略，但 {@link Set} 的语义更精确。
 *
 * @author QI Guang
 */
@Service
public class TemplateWhitelistService {

    /**
     * 模板文件在 classpath 下的根目录。
     */
    private static final String TEMPLATE_DIR = "templates/";

    /**
     * 允许下载的模板白名单。
     *
     * <p>与 {@code DocumentAdminController} 中各端点传入的 {@code resourceName}
     * 必须保持一致；新增模板时需同步更新此处。
     */
    private static final Set<String> ALLOWED_TEMPLATES = Set.of(
            "user_import_template.xlsx",
            "dept_import_template.xlsx",
            "order_export_template.xlsx",
            "customer_statement_template.xlsx",
            "user_manual.pdf"
    );

    /**
     * 校验文件名是否在白名单内，且不含路径遍历字符。
     *
     * <p><b>校验规则：</b>
     * <ol>
     *     <li>文件名非 {@code null} 且非空白</li>
     *     <li>文件名不包含 {@code ..}、{@code /}、{@code \}（纵深防御）</li>
     *     <li>文件名在 {@link #ALLOWED_TEMPLATES} 白名单内（主防线）</li>
     * </ol>
     *
     * @param fileName 待校验的文件名；{@code null} 或空白时返回 {@code false}
     * @return 文件名合法且在白名单内时为 {@code true}
     */
    public boolean isValidFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        // 纵深防御：白名单本身已排除带分隔符的项，保留此检查可在白名单被误改时多一层拦截
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            return false;
        }
        return ALLOWED_TEMPLATES.contains(fileName);
    }

    /**
     * 获取模板资源。
     *
     * <p><b>前置条件：</b>调用方必须先通过 {@link #isValidFileName(String)} 校验，
     * 或由本方法内部再次校验——当前实现选择了后者（内部校验），
     * 使调用方即使忘记校验也不会绕过白名单。
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

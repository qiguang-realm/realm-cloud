package cn.realm.cloud.workforce.identity.internal.document.adapter.inbound.web.admin;

import cn.realm.cloud.framework.web.file.FileResponseUtils;
import cn.realm.cloud.workforce.identity.internal.document.application.TemplateWhitelistService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Document domain — Admin view.
 * 文档域【管理后台视图】文档下载资源入口。
 *
 * <p>面向管理后台的模板文档下载接口，使用 RBAC 权限模型
 * （如 {@code @PreAuthorize("@ss.hasPermission('document:template:download')")}），
 * 提供预设模板文件下载能力。
 * 与其他视图（如应用端视图、开放平台视图）在权限模型、DTO契约、可见接口集合上严格隔离。
 *
 * <p><b>分层职责边界（Clean Architecture）：</b>
 * <ul>
 *   <li>本 Controller：仅 HTTP 协议适配、OpenAPI 契约声明、权限注解校验、端点编排；</li>
 *   <li>白名单校验、资源定位逻辑下沉至 {@link TemplateWhitelistService}；</li>
 *   <li>响应头组装、文件流输出委托至 {@link FileResponseUtils}；</li>
 *   <li>不包含任何文件 IO、白名单判定业务逻辑，支持独立 Mock 测试。</li>
 * </ul>
 *
 * <p>维护约束：合法模板清单由 {@link TemplateWhitelistService} 的 {@code ALLOWED_TEMPLATES} 白名单统一维护。
 * 新增模板必须同步完成两项修改：
 * <ol>
 *     <li>在白名单常量内添加模板文件名</li>
 *     <li>在当前 Controller 新增对应的下载端点</li>
 * </ol>
 * 只修改其中一处会触发运行时异常：非法模板文件名 / 模板不存在。
 *
 * <p>包路径说明：本类位于 {@code internal.document.api.web.admin}。
 * {@code internal} 为模块私有实现根目录，Spring Modulith 的 {@code verify()} 校验会阻止外部模块直接访问该包下类型；
 * {@code document} 属于 internal 下的子域，并非和 internal 平级的顶层包。
 *
 * <p>业务编排委托：{@link TemplateWhitelistService}、{@link FileResponseUtils}。
 *
 * @author QI Guang
 * @see <a href="https://google.aip.dev/122">AIP-122 Resource names</a>
 * @see <a href="https://google.aip.dev/132">AIP-132 Standard List methods</a>
 */
@RestController
@RequestMapping("/admin/document")
@Tag(name = "Document-Admin", description = "文档域｜B端管理后台接口")
@RequiredArgsConstructor
public class DocumentAdminController {

    private final TemplateWhitelistService templateWhitelistService;

    /**
     * 下载用户导入模板。
     */
    @GetMapping("/user-import")
    @PreAuthorize("@ss.hasPermission('system:document:download')")
    public void downloadUserImport(HttpServletResponse response) {
        writeTemplate(response, "user_import_template.xlsx", "用户导入模板.xlsx", false);
    }

    /**
     * 下载部门导入模板。
     */
    @GetMapping("/dept-import")
    @PreAuthorize("@ss.hasPermission('system:document:download')")
    public void downloadDeptImport(HttpServletResponse response) {
        writeTemplate(response, "dept_import_template.xlsx", "部门导入模板.xlsx", false);
    }

    /**
     * 下载订单导出模板。
     */
    @GetMapping("/order-export")
    @PreAuthorize("@ss.hasPermission('system:document:download')")
    public void downloadOrderExport(HttpServletResponse response) {
        writeTemplate(response, "order_export_template.xlsx", "订单导出模板.xlsx", false);
    }

    /**
     * 下载客户对账单模板。
     */
    @GetMapping("/customer-statement")
    @PreAuthorize("@ss.hasPermission('system:document:download')")
    public void downloadCustomerStatement(HttpServletResponse response) {
        writeTemplate(response, "customer_statement_template.xlsx", "客户对账单.xlsx", false);
    }

    /**
     * 预览用户操作手册（PDF，浏览器内联打开）。
     */
    @GetMapping("/user-manual/preview")
    @PreAuthorize("@ss.hasPermission('system:document:download')")
    public void previewUserManual(HttpServletResponse response) {
        writeTemplate(response, "user_manual.pdf", "用户操作手册.pdf", true);
    }

    /**
     * 从白名单解析资源并交给 {@link FileResponseUtils} 写出。
     *
     * @param resourceName 白名单中的模板文件名
     * @param displayName  展示给用户的文件名（支持中文）
     * @param inline       {@code true}=内联预览；{@code false}=附件下载
     */
    private void writeTemplate(HttpServletResponse response, String resourceName,
                               String displayName, boolean inline) {
        Resource resource = templateWhitelistService.getTemplateResource(resourceName);
        FileResponseUtils.writeResource(response, resource, displayName, inline);
    }
}

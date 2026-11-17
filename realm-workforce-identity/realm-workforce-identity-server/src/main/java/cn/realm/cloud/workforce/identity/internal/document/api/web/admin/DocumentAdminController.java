package cn.realm.cloud.workforce.identity.internal.document.api.web.admin;

import cn.realm.cloud.framework.web.file.FileResponseUtils;
import cn.realm.cloud.workforce.identity.internal.document.application.TemplateWhitelistService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台 - 文档下载接口入口。
 *
 * <p><b>职责：</b>只做端点声明、权限校验与编排。
 * <ul>
 *     <li>白名单校验与资源定位委托给 {@link TemplateWhitelistService}</li>
 *     <li>响应头设置与流拷贝委托给 {@link FileResponseUtils}</li>
 * </ul>
 * 本类不包含任何文件 IO 或白名单逻辑，可被独立 Mock 测试。
 *
 * <p><b>维护约束：</b>模板文件的合法清单由 {@link TemplateWhitelistService}
 * 的 {@code ALLOWED_TEMPLATES} 白名单维护。新增模板时需同时做两件事：
 * <ol>
 *     <li>在白名单中加入文件名</li>
 *     <li>在本类补一个端点</li>
 * </ol>
 * 只改一处会在运行时抛“非法的模板文件名”或“模板不存在”。
 *
 * <p><b>包路径说明：</b>本类位于 {@code internal.document.api.web.admin}，
 * {@code internal} 是模块私有实现区的根，Spring Modulith 的 {@code verify()}
 * 会阻止外部模块通过此包路径访问本类。{@code document} 是子域，位于
 * {@code internal} 之下，不是与 {@code internal} 平级的顶层包。
 *
 * @author QI Guang
 */
@RestController
@RequestMapping("/admin/system/document")
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

package cn.realm.cloud.system.server.document.internal.api.web.admin;

import cn.realm.cloud.framework.common.util.file.TemplateDownloadUtils;
import cn.realm.cloud.system.server.document.internal.application.TemplateDownloadService;
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
 * <p>提供导入/导出模板、操作手册等静态资源的下载与预览。本类只做端点声明、
 * 权限校验与编排：白名单校验与资源定位委托给 {@link TemplateDownloadService}，
 * 响应头设置与流拷贝委托给 {@link TemplateDownloadUtils}。
 *
 * <p>模板文件的合法清单由 {@link TemplateDownloadService} 的白名单维护，
 * 新增模板时只需在白名单中加入文件名，再在本类补一个端点即可。
 *
 * @author QI Guang
 */
@RestController
@RequestMapping("/admin/system/document")
@RequiredArgsConstructor
public class DocumentAdminController {

    private final TemplateDownloadService templateDownloadService;

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
     * 从白名单解析资源并交给工具类写出。
     *
     * @param resourceName 白名单中的模板文件名
     * @param displayName  展示给用户的文件名（支持中文）
     * @param inline       {@code true}=内联预览；{@code false}=附件下载
     */
    private void writeTemplate(HttpServletResponse response, String resourceName,
                               String displayName, boolean inline) {
        Resource resource = templateDownloadService.getTemplateResource(resourceName);
        TemplateDownloadUtils.downloadResource(response, resource, displayName, inline);
    }
}

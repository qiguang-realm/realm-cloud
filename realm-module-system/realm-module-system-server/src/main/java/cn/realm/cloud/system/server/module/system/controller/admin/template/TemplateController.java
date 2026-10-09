package cn.realm.cloud.system.server.module.system.controller.admin.template;

import cn.realm.cloud.framework.common.util.file.TemplateDownloadUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模板下载 / 预览 Controller
 *
 * <p>注意：方法返回 void，直接写 response，不能返回统一响应体。
 *
 * @author QI Guang
 */
@Slf4j
@Tag(name = "模板下载")
@RestController
@RequestMapping("/system/template")
@RequiredArgsConstructor
public class TemplateController {

    /**
     * 下载用户导入模板
     */
    @Operation(summary = "下载用户导入模板")
    @GetMapping("/user-import")
    public void downloadUserImport(HttpServletResponse response) {
        log.info("[模板下载] 用户导入模板");
        TemplateDownloadUtils.downloadClasspathTemplate(
                response,
                "templates/user_import_template.xlsx",
                "用户导入模板.xlsx");
    }

    /**
     * 下载部门导入模板
     */
    @Operation(summary = "下载部门导入模板")
    @GetMapping("/dept-import")
    public void downloadDeptImport(HttpServletResponse response) {
        log.info("[模板下载] 部门导入模板");
        TemplateDownloadUtils.downloadClasspathTemplate(
                response,
                "templates/dept_import_template.xlsx",
                "部门导入模板.xlsx");
    }

    /**
     * 下载订单导出模板
     */
    @Operation(summary = "下载订单导出模板")
    @GetMapping("/order-export")
    public void downloadOrderExport(HttpServletResponse response) {
        log.info("[模板下载] 订单导出模板");
        TemplateDownloadUtils.downloadClasspathTemplate(
                response,
                "templates/order_export_template.xlsx",
                "订单导出模板.xlsx");
    }

    /**
     * 预览用户操作手册（PDF，浏览器内联打开）
     */
    @Operation(summary = "预览用户操作手册")
    @GetMapping("/user-manual/preview")
    public void previewUserManual(HttpServletResponse response) {
        log.info("[模板预览] 用户操作手册");
        TemplateDownloadUtils.previewClasspathTemplate(
                response,
                "templates/user_manual.pdf",
                "用户操作手册.pdf");
    }

    /**
     * 下载客户对账单模板
     */
    @Operation(summary = "下载客户对账单模板")
    @GetMapping("/customer-statement")
    public void downloadCustomerStatement(HttpServletResponse response) {
        log.info("[模板下载] 客户对账单模板");
        TemplateDownloadUtils.downloadClasspathTemplate(
                response,
                "templates/customer_statement_template.xlsx",
                "客户对账单.xlsx");
    }

}

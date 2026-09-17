package cn.realm.cloud.framework.common.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 通用分页结果类
 *
 * @param <T> 数据列表中元素的类型
 * @author QI Guang
 */
@Schema(description = "通用分页结果类")
@Data
public final class PageResult<T> implements Serializable {

    @Schema(description = "总记录数", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long total;

    @Schema(description = "数据列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<T> list;

    public PageResult() {
    }

    public PageResult(List<T> list, Long total) {
        this.list = list == null ? Collections.emptyList() : list;
        this.total = total;
    }

    public PageResult(Long total) {
        this.list = Collections.emptyList();
        this.total = total;
    }

    public static <T> PageResult<T> empty() {
        return new PageResult<>(0L);
    }

    public static <T> PageResult<T> empty(Long total) {
        return new PageResult<>(total);
    }


    // 工厂方法：从 Spring Data Page 构建
//    public static <T> PageResult<T> of(org.springframework.data.domain.Page<T> page) {
//        return new PageResult<>(page.getContent(), page.getTotalElements());
//    }

    // 工厂方法：从 MyBatis-Plus IPage 构建
//    public static <T> PageResult<T> of(com.baomidou.mybatisplus.extension.plugins.pagination.Page<T> page) {
//        return new PageResult<>(page.getRecords(), page.getTotal());
//    }


}

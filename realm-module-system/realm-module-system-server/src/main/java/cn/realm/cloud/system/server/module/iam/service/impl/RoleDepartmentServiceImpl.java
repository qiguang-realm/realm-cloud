package cn.realm.cloud.system.server.module.iam.service.impl;

import cn.realm.cloud.system.server.module.iam.model.entity.RoleDepartment;
import cn.realm.cloud.system.server.module.iam.mapper.RoleDepartmentMapper;
import cn.realm.cloud.system.server.module.iam.service.RoleDepartmentService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

/**
 * <p>
 * IAM-角色与部门关联表 服务实现类
 * </p>
 *
 * @author QI Guang
 */
@Slf4j
@Validated
@Service
public class RoleDepartmentServiceImpl extends ServiceImpl<RoleDepartmentMapper, RoleDepartment> implements RoleDepartmentService {
    private static final Logger logger = LoggerFactory.getLogger(RoleDepartmentServiceImpl.class);

}

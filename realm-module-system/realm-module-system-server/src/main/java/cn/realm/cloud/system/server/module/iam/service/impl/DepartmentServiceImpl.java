package cn.realm.cloud.system.server.module.iam.service.impl;

import cn.realm.cloud.system.server.module.iam.mapper.DepartmentMapper;
import cn.realm.cloud.system.server.module.iam.model.entity.Department;
import cn.realm.cloud.system.server.module.iam.service.DepartmentService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

/**
 * <p>
 * IAM-部门表 服务实现类
 * </p>
 *
 * @author QI Guang
 */
@Service
@Validated
@RequiredArgsConstructor
@Slf4j
public class DepartmentServiceImpl extends ServiceImpl<DepartmentMapper, Department> implements DepartmentService {


}

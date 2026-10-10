package cn.realm.cloud.system.server.module.iam.service.impl;

import cn.realm.cloud.system.server.module.iam.mapper.RoleMapper;
import cn.realm.cloud.system.server.module.iam.model.entity.Role;
import cn.realm.cloud.system.server.module.iam.service.RoleService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

/**
 * <p>
 * IAM-角色表 服务实现类
 * </p>
 *
 * @author QI Guang
 */
@Service
@Validated
@RequiredArgsConstructor
@Slf4j
public class RoleServiceImpl extends ServiceImpl<RoleMapper, Role> implements RoleService {


}

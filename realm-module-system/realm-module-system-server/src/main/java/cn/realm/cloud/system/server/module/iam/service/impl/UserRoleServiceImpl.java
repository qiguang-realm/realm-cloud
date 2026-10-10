package cn.realm.cloud.system.server.module.iam.service.impl;

import cn.realm.cloud.system.server.module.iam.mapper.UserRoleMapper;
import cn.realm.cloud.system.server.module.iam.model.entity.UserRole;
import cn.realm.cloud.system.server.module.iam.service.UserRoleService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

/**
 * <p>
 * IAM-用户角色关联表 服务实现类
 * </p>
 *
 * @author QI Guang
 */
@Service
@Validated
@RequiredArgsConstructor
@Slf4j
public class UserRoleServiceImpl extends ServiceImpl<UserRoleMapper, UserRole> implements UserRoleService {


}

package cn.realm.cloud.system.server.module.iam.service.impl;

import cn.realm.cloud.system.server.module.iam.mapper.UserPositionMapper;
import cn.realm.cloud.system.server.module.iam.model.entity.UserPosition;
import cn.realm.cloud.system.server.module.iam.service.UserPositionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

/**
 * <p>
 * IAM-用户岗位关联表 服务实现类
 * </p>
 *
 * @author QI Guang
 */
@Service
@Validated
@RequiredArgsConstructor
@Slf4j
public class UserPositionServiceImpl extends ServiceImpl<UserPositionMapper, UserPosition> implements UserPositionService {


}

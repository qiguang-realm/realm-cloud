package cn.realm.cloud.system.server.module.iam.service.impl;

import cn.realm.cloud.system.server.module.iam.mapper.MenuMapper;
import cn.realm.cloud.system.server.module.iam.model.entity.Menu;
import cn.realm.cloud.system.server.module.iam.service.MenuService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

/**
 * <p>
 * IAM-菜单权限表 服务实现类
 * </p>
 *
 * @author QI Guang
 */
@Service
@Validated
@RequiredArgsConstructor
@Slf4j
public class MenuServiceImpl extends ServiceImpl<MenuMapper, Menu> implements MenuService {


}

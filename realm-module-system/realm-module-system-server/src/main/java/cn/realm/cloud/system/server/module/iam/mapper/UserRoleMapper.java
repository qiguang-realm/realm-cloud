package cn.realm.cloud.system.server.module.iam.mapper;

import cn.realm.cloud.system.server.module.iam.model.entity.UserRole;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * IAM-用户角色关联表 Mapper 接口
 * </p>
 *
 * @author QI Guang
 */
@Mapper
public interface UserRoleMapper extends BaseMapper<UserRole> {

}

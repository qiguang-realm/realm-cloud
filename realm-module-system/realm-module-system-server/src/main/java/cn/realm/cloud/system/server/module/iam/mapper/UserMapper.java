package cn.realm.cloud.system.server.module.iam.mapper;

import cn.realm.cloud.system.server.module.iam.model.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * IAM-用户表 Mapper 接口
 * </p>
 *
 * @author QI Guang
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

}

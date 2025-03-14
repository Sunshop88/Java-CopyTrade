package net.maku.followcom.service;


import net.maku.followcom.entity.UserEntity;
import net.maku.framework.mybatis.service.BaseService;

import java.util.List;
import java.util.Map;

/**
 * 用户管理
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
public interface UserService extends BaseService<UserEntity> {
    List<String> getUserId(List<Long> vpsUserVO);

    Map<Long, String> getUserName(List<Long> creatorIds);

    List<Long> getUserNameId(List<String> userList);
}

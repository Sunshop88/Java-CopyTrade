package net.maku.followcom.service.impl;

import lombok.AllArgsConstructor;
import net.maku.followcom.dao.UserDao;
import net.maku.followcom.entity.UserEntity;
import net.maku.followcom.service.UserService;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户管理
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Service
@AllArgsConstructor
public class UserServiceImpl extends BaseServiceImpl<UserDao, UserEntity> implements UserService {

/*
根据id获取用户名
 */
    @Override
    public List<String> getUserId(List<Long> idList) {
        if (idList.isEmpty()) {
            return null;
        }

        return baseMapper.selectBatchIds(idList).stream().map(UserEntity::getRealName).toList();
    }

}

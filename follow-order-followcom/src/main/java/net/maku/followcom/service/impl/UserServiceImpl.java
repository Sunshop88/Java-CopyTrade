package net.maku.followcom.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.AllArgsConstructor;
import net.maku.followcom.dao.UserDao;
import net.maku.followcom.entity.UserEntity;
import net.maku.followcom.service.UserService;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        return baseMapper.selectBatchIds(idList).stream().map(UserEntity::getUsername).toList();
    }

    @Override
    public Map<Long, String> getUserName(List<Long> idList) {
        if (idList.isEmpty()) {
            return null;
        }
        return baseMapper.selectBatchIds(idList).stream().collect(
                (HashMap<Long, String>::new),
                (m, v) -> m.put(v.getId(), v.getUsername()),
                HashMap::putAll
        );
    }

    @Override
    public List<Long> getUserNameId(List<String> userList) {
        if (userList.isEmpty()){
            return null;
        }
        List<Long> userIdList = list(new LambdaQueryWrapper<UserEntity>().in(UserEntity::getUsername, userList)).stream().map(UserEntity::getId).toList();
        return userIdList;
    }

    @Override
    public List<Long> getUserIds(String creatorName) {
        // 转义 % 和 _ 字符
        String escapedCreatorName = creatorName.replace("%", "\\%").replace("_", "\\_");

        // 使用转义后的名称进行模糊搜索
        List<UserEntity> list = list(new LambdaQueryWrapper<UserEntity>()
                .like(UserEntity::getUsername, escapedCreatorName));
        if (ObjectUtil.isNotEmpty(list)){
            List<Long> list1 = list.stream().map(UserEntity::getId).toList();
            return list1;
        }
        return null;
    }

}

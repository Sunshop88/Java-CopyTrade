package net.maku.followcom.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.convert.FollowConvert;
import net.maku.followcom.dao.FollowDao;
import net.maku.followcom.entity.FollowEntity;
import net.maku.followcom.service.FollowService;
import net.maku.followcom.vo.FollowInsertVO;
import net.maku.followcom.vo.FollowUpdateVO;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Author:  zsd
 * Date:  2024/11/14/周四 17:29
 * 跟单者
 */
@Service
@AllArgsConstructor
@Slf4j
@DS("slave")
public class FollowServiceImpl extends BaseServiceImpl<FollowDao, FollowEntity> implements FollowService {

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public void add(FollowInsertVO followInsertVO) {
        FollowEntity follow = FollowConvert.INSTANCE.convert(followInsertVO);
        save(follow);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public void edit(FollowUpdateVO followUpdateVO) {
        FollowEntity follow = FollowConvert.INSTANCE.convert(followUpdateVO);
        updateById(follow);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public void del(Long id) {
        removeById(id);
    }
}
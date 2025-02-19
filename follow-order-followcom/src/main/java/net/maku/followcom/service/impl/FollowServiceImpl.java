package net.maku.followcom.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.convert.FollowConvert;
import net.maku.followcom.dao.FollowDao;
import net.maku.followcom.entity.FollowEntity;
import net.maku.followcom.service.FollowService;
import net.maku.followcom.vo.FollowAddSalveVo;
import net.maku.followcom.vo.FollowInsertVO;
import net.maku.followcom.vo.FollowTraderVO;
import net.maku.followcom.vo.FollowUpdateVO;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

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
    public Integer add(FollowInsertVO followInsertVO) {
        FollowEntity follow = FollowConvert.INSTANCE.convert(followInsertVO);
        //默认值赋值
        //暴力反射设置默认值
        Field[] declaredFields = follow.getClass().getDeclaredFields();
        List<String> excludeFields = Arrays.asList("comment", "commentRegex", "commentRegex", "updateStopLossTakeProfitStatus", "positionComment", "repairyPriceHigh");
        Arrays.stream(declaredFields).forEach(x -> {
            x.setAccessible(true);
            try {
                Object o = x.get(follow);
                //如果为空，设置默认值
                if (ObjectUtil.isEmpty(o) && !excludeFields.contains(x.getName())) {
                    Class<?> type = x.getType();
                    if (type.equals(Integer.class)) {
                        x.set(follow, 0);
                    } else if (type.equals(Double.class)) {
                        x.set(follow, 0.00);
                    } else if (type.equals(Long.class)) {
                        x.set(follow, 0l);
                    } else {
                        x.set(follow, null);
                    }

                }
            } catch (IllegalAccessException e) {
                throw new ServerException("喊单账号从表参数异常:" + e);
            }
        });
        save(follow);
        return follow.getId();
    }

    @Override
    public FollowInsertVO  convert(FollowTraderVO followTraderVO, FollowAddSalveVo vo) {
        FollowInsertVO followInsertVO=new FollowInsertVO();
        followInsertVO.setId(followTraderVO.getId());
        followInsertVO.setClientId(Integer.valueOf(followTraderVO.getServerId()));
        followInsertVO.setSourceId(vo.getTraderId());
        followInsertVO.setPlatformId(Integer.valueOf(followTraderVO.getPlatformId()));
        followInsertVO.setUser(Long.valueOf(vo.getAccount()));
        followInsertVO.setPassword(vo.getPassword());
        followInsertVO.setComment(vo.getRemark());
        followInsertVO.setDirection(vo.getFollowDirection());
        followInsertVO.setType(4);
        followInsertVO.setMode(vo.getFollowMode());
        followInsertVO.setModeValue(vo.getFollowParam());
        followInsertVO.setStatus(vo.getFollowStatus());
        followInsertVO.setCloseOrderStatus(0);
        followInsertVO.setOpenOrderStatus(0);
        followInsertVO.setRepairStatus(0);
        followInsertVO.setPlacedType(vo.getPlacedType());
         return followInsertVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public void edit(FollowUpdateVO followUpdateVO) {
        FollowEntity follow = FollowConvert.INSTANCE.convert(followUpdateVO);
        updateById(follow);
    }
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public void edit( FollowEntity follow) {
        updateById(follow);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public void del(Long id) {
        removeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public FollowEntity getEntityById(Long id) {
        return getById(id);
    }
}
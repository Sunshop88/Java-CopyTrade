package net.maku.followcom.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.vo.FollowAddSalveVo;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import net.maku.followcom.convert.FollowTraderSubscribeConvert;
import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.followcom.query.FollowTraderSubscribeQuery;
import net.maku.followcom.vo.FollowTraderSubscribeVO;
import net.maku.followcom.dao.FollowTraderSubscribeDao;
import net.maku.followcom.service.FollowTraderSubscribeService;
import com.fhs.trans.service.impl.TransService;
import net.maku.framework.common.utils.ExcelUtils;
import net.maku.followcom.vo.FollowTraderSubscribeExcelVO;
import net.maku.framework.security.user.SecurityUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.util.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * 订阅关系表
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Service
@AllArgsConstructor
public class FollowTraderSubscribeServiceImpl extends BaseServiceImpl<FollowTraderSubscribeDao, FollowTraderSubscribeEntity> implements FollowTraderSubscribeService {
    private final TransService transService;
    private final RedisUtil redisUtil;
    private final CacheManager cacheManager;
    @Override
    public PageResult<FollowTraderSubscribeVO> page(FollowTraderSubscribeQuery query) {
        IPage<FollowTraderSubscribeEntity> page = baseMapper.selectPage(getPage(query), getWrapper(query));

        return new PageResult<>(FollowTraderSubscribeConvert.INSTANCE.convertList(page.getRecords()), page.getTotal());
    }


    private LambdaQueryWrapper<FollowTraderSubscribeEntity> getWrapper(FollowTraderSubscribeQuery query){
        LambdaQueryWrapper<FollowTraderSubscribeEntity> wrapper = Wrappers.lambdaQuery();

        return wrapper;
    }


    @Override
    public FollowTraderSubscribeVO get(Long id) {
        FollowTraderSubscribeEntity entity = baseMapper.selectById(id);
        FollowTraderSubscribeVO vo = FollowTraderSubscribeConvert.INSTANCE.convert(entity);

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(FollowTraderSubscribeVO vo) {
        FollowTraderSubscribeEntity entity = FollowTraderSubscribeConvert.INSTANCE.convert(vo);

        baseMapper.insert(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FollowTraderSubscribeVO vo) {
        FollowTraderSubscribeEntity entity = FollowTraderSubscribeConvert.INSTANCE.convert(vo);

        updateById(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> idList) {
        removeByIds(idList);

    }


    @Override
    public void export() {
    List<FollowTraderSubscribeExcelVO> excelList = FollowTraderSubscribeConvert.INSTANCE.convertExcelList(list());
        transService.transBatch(excelList);
        ExcelUtils.excelExport(FollowTraderSubscribeExcelVO.class, "订阅关系表", null, excelList);
    }

    @Override
    public List<String> initSubscriptions(Long id) {
        //清空缓存该跟单者所有的订阅关系的缓存
        redisUtil.del(Constant.FOLLOW_SUB_TRADER+id);

        //从数据库中获取到该跟单者的所有订阅关系
        List<FollowTraderSubscribeEntity> masterSlaves = this.list(Wrappers.<FollowTraderSubscribeEntity>lambdaQuery().eq(FollowTraderSubscribeEntity::getSlaveId, id)
                .eq(FollowTraderSubscribeEntity::getDeleted, CloseOrOpenEnum.CLOSE.getValue()));

        List<String> subscriptions = new LinkedList<>();
        for (FollowTraderSubscribeEntity item : masterSlaves) {
            subscriptions.add(item.getMasterId().toString());
            this.redisUtil.hSet(Constant.FOLLOW_SUB_TRADER+id, item.getMasterId().toString(), item);
        }
        return subscriptions;
    }

    @Override
    @Cacheable(
            value = "followSubscriptionCache",
            key = "#slaveId + '_' + #masterId",
            unless = "#result == null"
    )
    public FollowTraderSubscribeEntity subscription(Long slaveId, Long masterId) {
        //查看缓存
        Object hGet = this.redisUtil.hGet(Constant.FOLLOW_SUB_TRADER+slaveId.toString(), masterId.toString());
        if (!ObjectUtils.isEmpty(hGet)) {
            //缓存存在，直接返回
            return (FollowTraderSubscribeEntity) hGet;
        } else {
            FollowTraderSubscribeEntity masterSlave = this.getOne(Wrappers.<FollowTraderSubscribeEntity>lambdaQuery()
                    .eq(FollowTraderSubscribeEntity::getMasterId, masterId)
                    .eq(FollowTraderSubscribeEntity::getSlaveId, slaveId));

            if (!ObjectUtils.isEmpty(masterSlave)) {
                //数据库中存在
                this.redisUtil.hSet(Constant.FOLLOW_SUB_TRADER+slaveId, masterId.toString(), masterSlave);
                return masterSlave;
            } else {
                //数据库中不存在
                return null;
            }
        }
    }

    @Override
    public void addSubscription(FollowAddSalveVo vo) {
        FollowTraderSubscribeEntity followTraderSubscribeEntity=convertSubscribeConvert(vo);
        this.save(followTraderSubscribeEntity);
    }

    @Override
    public Map<String, Object> getStatus(String masterId, String slaveId) {
        if (ObjectUtil.isNotEmpty(redisUtil.get(Constant.FOLLOW_MASTER_SLAVE+masterId+":"+slaveId))){
            return (Map<String, Object>)redisUtil.get(Constant.FOLLOW_MASTER_SLAVE+masterId+":"+slaveId);
        }else {
            FollowTraderSubscribeEntity traderSubscribeEntity = this.getOne(new LambdaQueryWrapper<FollowTraderSubscribeEntity>().eq(FollowTraderSubscribeEntity::getMasterAccount, masterId).eq(FollowTraderSubscribeEntity::getSlaveAccount, slaveId));
            Map<String,Object> map=new HashMap<>();
            map.put("followStatus",traderSubscribeEntity.getFollowStatus());
            map.put("followOpen",traderSubscribeEntity.getFollowOpen());
            map.put("followClose",traderSubscribeEntity.getFollowClose());
            map.put("followRep",traderSubscribeEntity.getFollowRep());
            //设置跟单关系缓存值 保存状态
            redisUtil.set(Constant.FOLLOW_MASTER_SLAVE+traderSubscribeEntity.getMasterId()+":"+traderSubscribeEntity.getSlaveId(), JSONObject.toJSON(map));
            return map;
        }
    }

    @Override
    @Cacheable(
            value = "followSubOrderCache",
            key = "#id ?: 'defaultKey'",
            unless = "#result == null"
    )
    public List<FollowTraderSubscribeEntity> getSubscribeOrder(Long id) {
       return this.list(new LambdaQueryWrapper<FollowTraderSubscribeEntity>().eq(FollowTraderSubscribeEntity::getMasterId,id));
    }

    @Override
    public void updateSubCache(Long id) {
        List<FollowTraderSubscribeEntity> list = this.list(new LambdaQueryWrapper<FollowTraderSubscribeEntity>().eq(FollowTraderSubscribeEntity::getSlaveId, id));
        list.forEach(o->{
            //跟单关系缓存修改
            String cacheKey = generateCacheKey(o.getSlaveId(), o.getMasterId());
            Cache cache = cacheManager.getCache("followSubscriptionCache");
            if (cache != null) {
                cache.put(cacheKey,o); // 移除指定缓存条目
            }
        });
    }

    private String generateCacheKey(Long slaveId, Long masterId) {
        if (slaveId != null && masterId != null) {
            return slaveId + "_" + masterId;
        } else {
            return "defaultKey";
        }
    }

    private FollowTraderSubscribeEntity convertSubscribeConvert(FollowAddSalveVo vo) {
        FollowTraderSubscribeEntity followTraderSubscribeEntity=new FollowTraderSubscribeEntity();
        BeanUtil.copyProperties(vo,followTraderSubscribeEntity);
        followTraderSubscribeEntity.setMasterId(vo.getTraderId());
        followTraderSubscribeEntity.setCreator(SecurityUser.getUserId());
        followTraderSubscribeEntity.setCreateTime(LocalDateTime.now());
        return followTraderSubscribeEntity;
    }

}
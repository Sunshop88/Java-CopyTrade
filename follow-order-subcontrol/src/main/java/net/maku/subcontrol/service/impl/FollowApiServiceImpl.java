package net.maku.subcontrol.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.AllArgsConstructor;
import net.maku.followcom.convert.FollowTraderConvert;
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.*;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.followcom.service.*;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.vo.*;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.common.utils.Result;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.subcontrol.service.FollowApiService;
import net.maku.subcontrol.trader.CopierApiTrader;
import net.maku.subcontrol.trader.CopierApiTradersAdmin;
import net.maku.subcontrol.trader.LeaderApiTrader;
import net.maku.subcontrol.trader.LeaderApiTradersAdmin;
import online.mtapi.mt4.Order;
import online.mtapi.mt4.PlacedType;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@AllArgsConstructor
public class FollowApiServiceImpl implements FollowApiService {

    private static final Logger log = LoggerFactory.getLogger(FollowApiServiceImpl.class);
    private final FollowTraderService followTraderService;
    private final FollowPlatformService followPlatformService;
    private final RedisCache redisCache;
    private final LeaderApiTradersAdmin leaderApiTradersAdmin;
    private final SourceService sourceService;
    private final CopierApiTradersAdmin copierApiTradersAdmin;
    private final FollowTraderSubscribeService followTraderSubscribeService;
    private final FollowService followService;
    private final FollowVarietyService followVarietyService;
    private final CacheManager cacheManager;

    /**
     * 喊单账号保存
     *
     * @return false保存失败true保存成功
     */
    @Override
    public Boolean save(FollowTraderVO vo) {
        //默认模板最新的模板id
        if (ObjectUtil.isEmpty(vo.getTemplateId())) {
            vo.setTemplateId(followVarietyService.getLatestTemplateId());
        }
        //本机处理
        try {
            FollowTraderVO followTraderVO = followTraderService.save(vo);
            FollowTraderEntity convert = FollowTraderConvert.INSTANCE.convert(followTraderVO);
            convert.setId(followTraderVO.getId());
            ConCodeEnum conCodeEnum = leaderApiTradersAdmin.addTrader(followTraderService.getById(followTraderVO.getId()));
            if (!conCodeEnum.equals(ConCodeEnum.SUCCESS)) {
                followTraderService.removeById(followTraderVO.getId());
                return false;
            }
            LeaderApiTrader leaderApiTrader1 = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(followTraderVO.getId().toString());
            leaderApiTrader1.startTrade();
            ThreadPoolUtils.execute(() -> {
                LeaderApiTrader leaderApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(followTraderVO.getId().toString());
                leaderApiTradersAdmin.pushRedisData(followTraderVO,leaderApiTrader.quoteClient);
                leaderApiTrader.startTrade();
                followTraderService.saveQuo(leaderApiTrader.quoteClient, convert);
            });
        } catch (Exception e) {
            log.error("保存失败{}" + e);
            if (e instanceof ServerException) {
                throw e;
            } else {
                throw new ServerException(e.getMessage());
            }
        }
        return true;
    }

    @Override
    public void delete(List<Long> idList) {
        List<FollowTraderEntity> list = followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().in(FollowTraderEntity::getId, idList));
        List<FollowTraderEntity> masterList = list.stream().filter(o -> o.getType().equals(TraderTypeEnum.MASTER_REAL.getType())).toList();
        List<FollowTraderEntity> slaveList = list.stream().filter(o -> o.getType().equals(TraderTypeEnum.SLAVE_REAL.getType())).toList();
        if (ObjectUtil.isNotEmpty(masterList)) {
            //查看喊单账号是否存在用户
            List<FollowTraderSubscribeEntity> followTraderSubscribeEntityList = followTraderSubscribeService.list(new LambdaQueryWrapper<FollowTraderSubscribeEntity>().in(FollowTraderSubscribeEntity::getMasterId, masterList.stream().map(FollowTraderEntity::getId).toList()));
            if (ObjectUtil.isNotEmpty(followTraderSubscribeEntityList)) {
                throw new ServerException("请先删除跟单用户");
            }
        }
        followTraderService.delete(idList);

        //清空缓存
        list.stream().forEach(o ->{
            leaderApiTradersAdmin.removeTrader(o.getId().toString());
            copierApiTradersAdmin.removeTrader(o.getId().toString());
            redisCache.deleteByPattern(o.getId().toString());
            redisCache.deleteByPattern(o.getAccount());
            //账号缓存移除
            Cache cache = cacheManager.getCache("followFollowCache");
            if (cache != null) {
                cache.evict(o); // 移除指定缓存条目
            }
        });

        slaveList.forEach(o->{
            List<FollowTraderSubscribeEntity> followTraderSubscribeEntities = followTraderSubscribeService.list(new LambdaQueryWrapper<FollowTraderSubscribeEntity>().eq(FollowTraderSubscribeEntity::getSlaveId, o.getId()));
            //跟单关系缓存删除
            followTraderSubscribeEntities.forEach(o1->{
                String cacheKey = generateCacheKey(o1.getSlaveId(), o1.getMasterId());
                Cache cache = cacheManager.getCache("followSubscriptionCache");
                if (cache != null) {
                    cache.evict(cacheKey); // 移除指定缓存条目
                }
            });
        });

        masterList.forEach(o->{
            //喊单关系缓存移除
            Cache cache = cacheManager.getCache("followSubOrderCache");
            if (cache != null) {
                cache.evict(o.getId()); // 移除指定缓存条目
            }
        });

        //删除订阅关系
        followTraderSubscribeService.remove(new LambdaQueryWrapper<FollowTraderSubscribeEntity>().in(FollowTraderSubscribeEntity::getMasterId, idList).or().in(FollowTraderSubscribeEntity::getSlaveId, idList));

    }

    @Override
    public Boolean addSlave(FollowAddSalveVo vo) {
        try {
            FollowTraderEntity followTraderEntity = followTraderService.getById(vo.getTraderId());
            if (ObjectUtil.isEmpty(followTraderEntity)||!followTraderEntity.getIpAddr().equals(FollowConstant.LOCAL_HOST)) {
                throw new ServerException("请输入正确喊单账号");
            }
            LeaderApiTrader leaderApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(vo.getTraderId().toString());
            if (ObjectUtil.isEmpty(leaderApiTrader)){
                throw new ServerException("喊单账号状态异常，请确认");
            }
            //如果为固定手数和手数比例，必填参数
            if (vo.getFollowStatus().equals(FollowModeEnum.FIX.getCode()) || vo.getFollowStatus().equals(FollowModeEnum.RATIO.getCode())) {
                if (ObjectUtil.isEmpty(vo.getFollowParam())||vo.getFollowParam().compareTo(new BigDecimal("0.01"))<0) {
                    throw new ServerException("请输入正确跟单参数");
                }
            }
            //查看是否存在循环跟单情况
            FollowTraderSubscribeEntity traderSubscribeEntity = followTraderSubscribeService.getOne(new LambdaQueryWrapper<FollowTraderSubscribeEntity>().eq(FollowTraderSubscribeEntity::getMasterAccount, vo.getAccount()).eq(FollowTraderSubscribeEntity::getSlaveAccount, followTraderEntity.getAccount()));
            if (ObjectUtil.isNotEmpty(traderSubscribeEntity)) {
                throw new ServerException("存在循环跟单,请检查");
            }
            FollowTraderVO followTraderVo = new FollowTraderVO();
            followTraderVo.setAccount(vo.getAccount());
            followTraderVo.setPassword(vo.getPassword());
            followTraderVo.setPlatform(vo.getPlatform());
            followTraderVo.setType(TraderTypeEnum.SLAVE_REAL.getType());
            followTraderVo.setFollowStatus(vo.getFollowStatus());
            if (ObjectUtil.isEmpty(vo.getTemplateId())) {
                vo.setTemplateId(followVarietyService.getLatestTemplateId());
            }
            followTraderVo.setTemplateId(vo.getTemplateId());
            FollowTraderVO followTraderVO = followTraderService.save(followTraderVo);

            FollowTraderEntity convert = FollowTraderConvert.INSTANCE.convert(followTraderVO);
            convert.setId(followTraderVO.getId());
            ConCodeEnum conCodeEnum = copierApiTradersAdmin.addTrader(followTraderService.getById(followTraderVO.getId()));
            if (!conCodeEnum.equals(ConCodeEnum.SUCCESS)) {
                followTraderService.removeById(followTraderVO.getId());
                return false;
            }
            ThreadPoolUtils.execute(() -> {
                CopierApiTrader copierApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(followTraderVO.getId().toString());
                leaderApiTradersAdmin.pushRedisData(followTraderVO,copierApiTrader.quoteClient);
                followTraderService.saveQuo(copierApiTrader.quoteClient, convert);
                //设置下单方式
                copierApiTrader.orderClient.PlacedType = PlacedType.forValue(vo.getPlacedType());
                //建立跟单关系
                vo.setSlaveId(followTraderVO.getId());
                vo.setSlaveAccount(vo.getAccount());
                vo.setMasterAccount(followTraderEntity.getAccount());
                followTraderSubscribeService.addSubscription(vo);
                copierApiTrader.startTrade();
                //保存状态到redis
                Map<String, Object> map = new HashMap<>();
                map.put("followStatus", vo.getFollowStatus());
                map.put("followOpen", vo.getFollowOpen());
                map.put("followClose", vo.getFollowClose());
                map.put("followRep", vo.getFollowRep());
                //设置跟单关系缓存值 保存状态
                redisCache.set(Constant.FOLLOW_MASTER_SLAVE + followTraderEntity.getId() + ":" + vo.getSlaveId(), JSONObject.toJSON(map));
                //移除喊单的跟单缓存
                Cache cache = cacheManager.getCache("followSubOrderCache");
                if (cache != null) {
                    cache.evict(vo.getTraderId()); // 移除指定缓存条目
                }
                //查看是否该VPS存在过此账号
                if (ObjectUtil.isEmpty(redisCache.hGetAll(Constant.FOLLOW_REPAIR_SEND+FollowConstant.LOCAL_HOST+vo.getAccount()+"#"+followTraderEntity.getAccount()))&&
                        ObjectUtil.isEmpty(redisCache.hGetAll(Constant.FOLLOW_REPAIR_CLOSE+FollowConstant.LOCAL_HOST+vo.getAccount()+"#"+followTraderEntity.getAccount()))){
                    //建立漏单关系 查询喊单所有持仓
                    Order[] orders = leaderApiTrader.quoteClient.GetOpenedOrders();
                    if (orders.length>0){
                        Arrays.stream(orders).toList().forEach(order->{
                            EaOrderInfo eaOrderInfo = send2Copiers(OrderChangeTypeEnum.NEW, order, 0, leaderApiTrader.quoteClient.Account().currency, LocalDateTime.now(),followTraderEntity);
                            redisCache.hSet(Constant.FOLLOW_REPAIR_SEND + FollowConstant.LOCAL_HOST+"#"+vo.getAccount()+"#"+followTraderEntity.getAccount(),String.valueOf(order.Ticket),eaOrderInfo);
                        });
                    }
                }
            });
        } catch (Exception e) {
            log.error("跟单账号保存失败:", e);
            if(e instanceof ServerException) {
                throw e;
            }else{
                throw new ServerException("保存失败" + e);
            }

        }

        return true;
    }

    @Override
    public Boolean updateSlave(FollowUpdateSalveVo vo) {
        try {
            FollowTraderEntity followTraderEntity = followTraderService.getById(vo.getId());
            if (ObjectUtil.isEmpty(vo.getTemplateId())) {
                vo.setTemplateId(followVarietyService.getLatestTemplateId());
            }
            BeanUtil.copyProperties(vo, followTraderEntity);
            followTraderService.updateById(followTraderEntity);
            //查看绑定跟单账号
            FollowTraderSubscribeEntity followTraderSubscribeEntity = followTraderSubscribeService.getOne(new LambdaQueryWrapper<FollowTraderSubscribeEntity>()
                    .eq(FollowTraderSubscribeEntity::getSlaveId, vo.getId()));
            if(ObjectUtil.isNotEmpty(followTraderSubscribeEntity)) {
                BeanUtil.copyProperties(vo, followTraderSubscribeEntity, "id");
                //更新订阅状态
                followTraderSubscribeService.updateById(followTraderSubscribeEntity);
                redisCache.delete(Constant.FOLLOW_MASTER_SLAVE + followTraderSubscribeEntity.getMasterId() + ":" + followTraderEntity.getId());
            }
            BeanUtil.copyProperties(vo, followTraderSubscribeEntity, "id");
            //更新订阅状态
            followTraderSubscribeService.updateById(followTraderSubscribeEntity);
            Map<String,Object> map=new HashMap<>();
            map.put("followStatus",vo.getFollowStatus());
            map.put("followOpen",vo.getFollowOpen());
            map.put("followClose",vo.getFollowClose());
            map.put("followRep",vo.getFollowRep());
            redisCache.set(Constant.FOLLOW_MASTER_SLAVE + followTraderSubscribeEntity.getMasterId() + ":" + followTraderEntity.getId(),map);
            //删除缓存
            copierApiTradersAdmin.removeTrader(followTraderEntity.getId().toString());
            redisCache.delete(Constant.FOLLOW_SUB_TRADER+vo.getId().toString());
            //修改内存缓存
            followTraderSubscribeService.updateSubCache(vo.getId());
            //重连
            reconnectSlave(vo.getId().toString());
            ThreadPoolUtils.execute(() -> {
                CopierApiTrader copierApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(followTraderEntity.getId().toString());
                //设置下单方式
                copierApiTrader.orderClient.PlacedType = PlacedType.forValue(vo.getPlacedType());
                copierApiTrader.startTrade();
                FollowTraderVO followTraderVO = FollowTraderConvert.INSTANCE.convert(followTraderEntity);
                //修改缓存
                leaderApiTradersAdmin.pushRedisData(followTraderVO,copierApiTrader.quoteClient);
            });
        } catch (Exception e) {
            throw new ServerException("修改失败" + e);
        }

        return false;
    }

    /**
     * 喊单账号添加，主从表同时写入
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertSource(SourceInsertVO vo) {
        //参数转换，转成主表数据
        FollowTraderVO followTrader = FollowTraderConvert.INSTANCE.convert(vo);
        //根据平台id查询平台
        FollowPlatformEntity platform = followPlatformService.getById(vo.getPlatformId());
        if (ObjectUtil.isEmpty(platform)) {
            throw new ServerException("暂无可用服务器商");
        }
        followTrader.setPlatform(platform.getServer());
        //判断主表如果保存失败，则返回false
        Boolean result = save(followTrader);
        if (!result) {
            return false;
        }
        //保存从表数据
        sourceService.add(vo);
        return true;

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateSource(SourceUpdateVO vo) {
        SourceEntity source = sourceService.getEntityById(vo.getId());
        FollowTraderEntity followTrader = FollowTraderConvert.INSTANCE.convert(vo);
        LambdaQueryWrapper<FollowTraderEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(FollowTraderEntity::getAccount, source.getUser()).eq(FollowTraderEntity::getPlatformId, source.getPlatformId());
        followTraderService.update(followTrader, lambdaQueryWrapper);
        //重连
        reconnect(vo.getId().toString());
        //保存从表数据
        sourceService.edit(vo);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean delSource(SourceDelVo vo) {
        SourceEntity source = sourceService.getEntityById(vo.getId());
        if (source == null) {
            return false;
        }
        List<Long> ids = followTraderService.lambdaQuery().eq(FollowTraderEntity::getAccount, source.getUser()).eq(FollowTraderEntity::getPlatformId, source.getPlatformId()).list().stream().map(FollowTraderEntity::getId).toList();
        delete(ids);
        //删除从表数据
        sourceService.del(vo.getId());
        return true;
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertFollow(FollowInsertVO vo) {
        //参数转化
        FollowAddSalveVo followAddSalveVo = FollowTraderConvert.INSTANCE.convert(vo);
        //根据平台id查询平台
        FollowPlatformEntity platform = followPlatformService.getById(vo.getPlatformId());
        if (ObjectUtil.isEmpty(platform)) {
            throw new ServerException("暂无可用服务器商");
        }
        followAddSalveVo.setPlatform(platform.getServer());
        //判断主表如果保存失败，则返回false
        Boolean result = addSlave(followAddSalveVo);
        if (!result) {
            return false;
        }
        //处理副表数据
        followService.add(vo);
        return true;
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateFollow(FollowUpdateVO vo) {
        //查询从表
        FollowEntity followEntity = followService.getEntityById(vo.getId());
        //查询主表
        LambdaQueryWrapper<FollowTraderEntity> query = new LambdaQueryWrapper<>();
        query.eq(FollowTraderEntity::getAccount, followEntity.getUser()).eq(FollowTraderEntity::getPlatformId, followEntity.getPlatformId());
        FollowTraderEntity entity = followTraderService.getOne(query);
        FollowUpdateSalveVo followUpdateSalveVo = FollowTraderConvert.INSTANCE.convert(vo);
        followUpdateSalveVo.setId(entity.getId());
        String pwd = StringUtils.isNotBlank(vo.getPassword()) ? vo.getPassword() : entity.getPassword();
        followUpdateSalveVo.setPassword(pwd);
        // 判断主表如果保存失败，则返回false
        Boolean result = updateSlave(followUpdateSalveVo);
        if (!result) {
            return false;
        }
        //重连
       // reconnectSlave(vo.getId().toString());
        //处理副表数据
        followService.edit(vo);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean delFollow(SourceDelVo vo) {
        FollowEntity followEntity = followService.getEntityById(vo.getId());
        if (followEntity == null) {
            return false;
        }
        List<Long> ids = followTraderService.lambdaQuery().eq(FollowTraderEntity::getAccount, followEntity.getUser()).eq(FollowTraderEntity::getPlatformId, followEntity.getPlatformId()).list().stream().map(FollowTraderEntity::getId).toList();
        delete(ids);
        //删除从表数据
        followService.del(vo.getId());
        return true;
    }

    private void reconnect(String traderId) {
        try{
            leaderApiTradersAdmin.removeTrader(traderId);
            FollowTraderEntity followTraderEntity = followTraderService.getById(traderId);
            ConCodeEnum conCodeEnum = leaderApiTradersAdmin.addTrader(followTraderService.getById(traderId));
            LeaderApiTrader leaderApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(traderId);
            if (conCodeEnum != ConCodeEnum.SUCCESS && !followTraderEntity.getStatus().equals(TraderStatusEnum.ERROR.getValue())) {
                followTraderEntity.setStatus(TraderStatusEnum.ERROR.getValue());
                followTraderService.updateById(followTraderEntity);
                log.error("喊单者:[{}-{}-{}]重连失败，请校验", followTraderEntity.getId(), followTraderEntity.getAccount(), followTraderEntity.getServerName());
                throw new ServerException("重连失败");
            } else {
                log.info("喊单者:[{}-{}-{}-{}]在[{}:{}]重连成功", followTraderEntity.getId(), followTraderEntity.getAccount(), followTraderEntity.getServerName(), followTraderEntity.getPassword(), leaderApiTrader.quoteClient.Host, leaderApiTrader.quoteClient.Port);
                leaderApiTrader.startTrade();
            }
        }catch (RuntimeException e){
            throw new ServerException("请检查账号密码，稍后再试");
        }
    }

    private void reconnectSlave(String traderId) {
        try {
            FollowTraderEntity followTraderEntity = followTraderService.getById(traderId);
            copierApiTradersAdmin.removeTrader(traderId);
            ConCodeEnum conCodeEnum = copierApiTradersAdmin.addTrader(followTraderService.getById(traderId));
            CopierApiTrader copierApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(traderId);
            if (conCodeEnum != ConCodeEnum.SUCCESS && !followTraderEntity.getStatus().equals(TraderStatusEnum.ERROR.getValue())) {
                followTraderEntity.setStatus(TraderStatusEnum.ERROR.getValue());
                followTraderService.updateById(followTraderEntity);
                log.error("跟单者:[{}-{}-{}]重连失败，请校验", followTraderEntity.getId(), followTraderEntity.getAccount(), followTraderEntity.getServerName());
                throw new ServerException("重连失败");
            } else {
                log.info("跟单者:[{}-{}-{}-{}]在[{}:{}]重连成功", followTraderEntity.getId(), followTraderEntity.getAccount(), followTraderEntity.getServerName(), followTraderEntity.getPassword(), copierApiTrader.quoteClient.Host, copierApiTrader.quoteClient.Port);
                copierApiTrader.startTrade();
            }

        } catch (RuntimeException e) {
            throw new ServerException("请检查账号密码，稍后再试");
        }
    }
    private String generateCacheKey(Long slaveId, Long masterId) {
        if (slaveId != null && masterId != null) {
            return slaveId + "_" + masterId;
        } else {
            return "defaultKey";
        }
    }

    protected EaOrderInfo send2Copiers(OrderChangeTypeEnum type, online.mtapi.mt4.Order order, double equity, String currency, LocalDateTime detectedDate,FollowTraderEntity leader) {

        // 并且要给EaOrderInfo添加额外的信息：喊单者id+喊单者账号+喊单者服务器
        // #84 喊单者发送订单前需要处理前后缀
        EaOrderInfo orderInfo = new EaOrderInfo(order, leader.getId() ,leader.getAccount(), leader.getServerName(), equity, currency, Boolean.FALSE);
        assembleOrderInfo(type, orderInfo, detectedDate);
        return orderInfo;
    }

    void assembleOrderInfo(OrderChangeTypeEnum type, EaOrderInfo orderInfo, LocalDateTime detectedDate) {
        if (type == OrderChangeTypeEnum.NEW) {
            orderInfo.setOriginal(AcEnum.MO);
            orderInfo.setDetectedOpenTime(detectedDate);
        } else if (type == OrderChangeTypeEnum.CLOSED) {
            orderInfo.setDetectedCloseTime(detectedDate);
            orderInfo.setOriginal(AcEnum.MC);
        } else if (type == OrderChangeTypeEnum.MODIFIED) {
            orderInfo.setOriginal(AcEnum.MM);
        }
    }
}

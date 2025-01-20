package net.maku.subcontrol.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.AllArgsConstructor;
import net.maku.followcom.convert.FollowOrderDetailConvert;
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
import net.maku.framework.common.utils.DateUtils;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.common.utils.Result;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.subcontrol.service.FollowApiService;
import net.maku.subcontrol.trader.*;
import online.mtapi.mt4.*;
import online.mtapi.mt4.Exception.ConnectException;
import online.mtapi.mt4.Exception.InvalidSymbolException;
import online.mtapi.mt4.Exception.TimeoutException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

import static online.mtapi.mt4.Op.Buy;
import static online.mtapi.mt4.Op.Sell;

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
    private final FollowOrderDetailService followOrderDetailService;
    private final FollowVpsService followVps;
    private final FollowSysmbolSpecificationService followSysmbolSpecificationService;
    private final FollowOrderCloseService followOrderCloseService;

    /**
     * 喊单账号保存
     *
     * @return false保存失败true保存成功
     */
    @Override
    public Boolean save(FollowTraderVO vo) {
        //默认模板最新的模板id
        if (ObjectUtil.isEmpty(vo.getTemplateId())) {
            vo.setTemplateId(followVarietyService.getBeginTemplateId());
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
                vo.setTemplateId(followVarietyService.getBeginTemplateId());
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
                vo.setTemplateId(followVarietyService.getBeginTemplateId());
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

        return true;
    }

    /**
     * 喊单账号添加，主从表同时写入
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer insertSource(SourceInsertVO vo) {
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
            return null;
        }
        //保存从表数据
        Integer id = sourceService.add(vo);
        return id;

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateSource(SourceUpdateVO vo) {
        SourceEntity source = sourceService.getEntityById(vo.getId());
        FollowTraderEntity followTrader = FollowTraderConvert.INSTANCE.convert(vo);

        FollowTraderEntity one = followTraderService.lambdaQuery().eq(FollowTraderEntity::getAccount, source.getUser()).eq(FollowTraderEntity::getServerId, vo.getServerId()).eq(FollowTraderEntity::getPlatformId, source.getPlatformId()).one();
       if (ObjectUtil.isEmpty(one)) { throw  new ServerException("账号不存在,请检查id");}
        followTrader.setId(one.getId());
        followTraderService.updateById(followTrader);
        //重连
        reconnect(one.getId().toString());
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
        List<Long> ids = followTraderService.lambdaQuery().eq(FollowTraderEntity::getAccount, source.getUser()).eq(FollowTraderEntity::getServerId,vo.getServerId()).eq(FollowTraderEntity::getPlatformId, source.getPlatformId()).list().stream().map(FollowTraderEntity::getId).toList();
        delete(ids);
        //删除从表数据
        sourceService.del(vo.getId());
        return true;
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer insertFollow(FollowInsertVO vo) {
        //参数转化
        FollowAddSalveVo followAddSalveVo = FollowTraderConvert.INSTANCE.convert(vo);
        //根据平台id查询平台
        FollowPlatformEntity platform = followPlatformService.getById(vo.getPlatformId());
        if (ObjectUtil.isEmpty(platform)) {
            throw new ServerException("暂无可用服务器商");
        }
        SourceEntity source = sourceService.getEntityById(vo.getSourceId());
        FollowTraderEntity one = followTraderService.lambdaQuery().eq(FollowTraderEntity::getAccount, source.getUser()).eq(FollowTraderEntity::getPlatformId, source.getPlatformId()).eq(FollowTraderEntity::getServerId, vo.getClientId()).one();
        followAddSalveVo.setPlatform(platform.getServer());
        followAddSalveVo.setTraderId(one.getId());
        //判断主表如果保存失败，则返回false
        Boolean result = addSlave(followAddSalveVo);
        if (!result) {
            return null;
        }
        //处理副表数据
        Integer id = followService.add(vo);
        return id;
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateFollow(FollowUpdateVO vo) {
        //查询从表
        FollowEntity followEntity = followService.getEntityById(vo.getId());
        //查询主表
        LambdaQueryWrapper<FollowTraderEntity> query = new LambdaQueryWrapper<>();
        query.eq(FollowTraderEntity::getAccount, followEntity.getUser()).eq(FollowTraderEntity::getServerId,vo.getClientId()).eq(FollowTraderEntity::getPlatformId, followEntity.getPlatformId());
        FollowTraderEntity entity = followTraderService.getOne(query);
        if (ObjectUtil.isEmpty(entity)) { throw  new ServerException("账号不存在,请检查id");}

        FollowUpdateSalveVo followUpdateSalveVo = FollowTraderConvert.INSTANCE.convert(vo);
   /*     Integer mode = FollowModeEnum.getVal(vo.getMode());
        log.info("{}跟随模式{}",vo.getMode(),mode);
        followUpdateSalveVo.setFollowMode(mode);*/
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
        List<Long> ids = followTraderService.lambdaQuery().eq(FollowTraderEntity::getAccount, followEntity.getUser()).eq(FollowTraderEntity::getServerId,vo.getServerId()).eq(FollowTraderEntity::getPlatformId, followEntity.getPlatformId()).list().stream().map(FollowTraderEntity::getId).toList();
        delete(ids);
        //删除从表数据
        followService.del(vo.getId());
        return true;
    }

    @Override
    public OrderClosePageVO orderCloseList(OrderHistoryVO vo) {
        Page<FollowOrderDetailEntity> page = new Page<>(vo.getPageNumber(), vo.getPageSize());
        LambdaQueryWrapper<FollowOrderDetailEntity> query = new LambdaQueryWrapper<>();
        query.isNotNull(FollowOrderDetailEntity::getCloseId);
        query.eq(ObjectUtil.isNotEmpty(vo.getAccount()),FollowOrderDetailEntity::getAccount, vo.getAccount());
        query.in(ObjectUtil.isNotEmpty(vo.getPlaceType()),FollowOrderDetailEntity::getPlacedType, vo.getPlaceType());
        query.in(ObjectUtil.isNotEmpty(vo.getType()),FollowOrderDetailEntity::getType, vo.getType());
        query.ge(ObjectUtil.isNotEmpty(vo.getCloseFrom()),FollowOrderDetailEntity::getCloseTime,DateUtils.format(vo.getCloseFrom(),DateUtils.DATE_TIME_PATTERN));
        query.le(ObjectUtil.isNotEmpty(vo.getCloseTo()),FollowOrderDetailEntity::getCloseTime, DateUtils.format(vo.getCloseTo(),DateUtils.DATE_TIME_PATTERN));
        query.ge(ObjectUtil.isNotEmpty(vo.getOpenFrom()),FollowOrderDetailEntity::getOpenTime, DateUtils.format(vo.getOpenFrom(),DateUtils.DATE_TIME_PATTERN));
        query.le(ObjectUtil.isNotEmpty(vo.getOpenTo()),FollowOrderDetailEntity::getOpenTime, DateUtils.format(vo.getOpenTo(),DateUtils.DATE_TIME_PATTERN));
        if(ObjectUtil.isNotEmpty(vo.getClientId())){
            FollowVpsEntity vps = followVps.getById(vo.getClientId());
            query.eq(ObjectUtil.isNotEmpty(vps),FollowOrderDetailEntity::getIpAddr,vps.getIpAddress());
        }
        if(ObjectUtil.isNotEmpty(vo.getAccount())){
            List<Long> traderIds =new ArrayList<>();
            List<Integer> types = vo.getAccount().stream().map(AccountModelVO::getType).collect(Collectors.toList());
            if(ObjectUtil.isNotEmpty(types)){
                List<FollowTraderEntity> ls = followTraderService.lambdaQuery().in(FollowTraderEntity::getType, types).list();
                List<Long> ids = ls.stream().map(FollowTraderEntity::getId).collect(Collectors.toList());
                traderIds.addAll(ids);
            }
            List<Long> aids = vo.getAccount().stream().map(AccountModelVO::getId).collect(Collectors.toList());
            if(ObjectUtil.isNotEmpty(aids)){
                traderIds.addAll(aids);
            }
            query.in(ObjectUtil.isNotEmpty(traderIds),FollowOrderDetailEntity::getTraderId,traderIds);

        }
        Page<FollowOrderDetailEntity> pageOrder = followOrderDetailService.page(page, query);
        OrderClosePageVO orderList = OrderClosePageVO.builder().totalCount(pageOrder.getTotal()).orders(FollowOrderDetailConvert.INSTANCE.convertOrderList(page.getRecords())).build();
        return orderList;
    }

    @Override
    public Boolean orderSend(OrderSendVO vo) {

        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean orderClose(OrderCloseVO vo) {
        List<AccountModelVO> account = vo.getAccount();
        account.forEach(a->{
            List<Integer> ticket = vo.getTicket();
            ticket.forEach(o->{
                //0=喊单，1=跟单
                Integer type = a.getType();
                Long user=null;
                Integer serverId=null;
                Integer platformId=null;
                if(type==0){
                    SourceEntity     source = sourceService.getEntityById(a.getId());
                    user=source.getUser();
                    serverId=source.getClientId();
                    platformId = source.getPlatformId();
                }else{
                    //查询从表
                    FollowEntity   followEntity = followService.getEntityById(a.getId());
                    user=followEntity.getUser();
                    serverId=followEntity.getClientId();
                    platformId = followEntity.getPlatformId();
                }
                FollowTraderEntity followTraderVO = followTraderService.lambdaQuery().eq(FollowTraderEntity::getAccount,user).eq(FollowTraderEntity::getPlatformId, platformId).eq(FollowTraderEntity::getServerId,serverId).one();
                FollowOrderSendCloseVO followOrderSendCloseVO = new FollowOrderSendCloseVO();
                followOrderSendCloseVO.setFlag(0);
                followOrderSendCloseVO.setTraderId(followTraderVO.getId());
                if(ObjectUtil.isEmpty(o)){
                    throw  new ServerException("订单号不能为空");
                }
                followOrderSendCloseVO.setOrderNo(o);
                
                localOrderClose(followOrderSendCloseVO,followTraderVO);
            });

        });
        return true;
    }

    @Override
    public Boolean orderCloseAll(OrderCloseAllVO vo) {
        List<AccountModelVO> account = vo.getAccount();
        account.forEach(a->{
            //0=喊单，1=跟单
            Integer type = a.getType();
            Long user=null;
            Integer serverId=null;
            Integer platformId=null;
            if(type==0){
                SourceEntity     source = sourceService.getEntityById(a.getId());
                user=source.getUser();
                serverId=source.getClientId();
                 platformId = source.getPlatformId();
            }else{
                //查询从表
                FollowEntity   followEntity = followService.getEntityById(a.getId());
                user=followEntity.getUser();
                serverId=followEntity.getClientId();
                platformId = followEntity.getPlatformId();
            }
            FollowTraderEntity followTraderVO = followTraderService.lambdaQuery().eq(FollowTraderEntity::getAccount,user).eq(FollowTraderEntity::getPlatformId, platformId).eq(FollowTraderEntity::getServerId,serverId).one();
            FollowOrderSendCloseVO followOrderSendCloseVO = new FollowOrderSendCloseVO();
            followOrderSendCloseVO.setFlag(1);
            followOrderSendCloseVO.setIsCloseAll(TraderRepairEnum.CLOSE.getType());
            followOrderSendCloseVO.setTraderId(followTraderVO.getId());
            localOrderClose(followOrderSendCloseVO,followTraderVO);
        });
        return  true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean changePassword(ChangePasswordVO vo) {
        List<AccountModelVO> account = vo.getAccount();
        account.forEach(a->{
            //0=喊单，1=跟单
            Integer type = a.getType();
            Long user=null;
            Integer serverId=null;
            SourceEntity source =null;
            FollowEntity followEntity=null;
            Integer platformId=null;
            if(type==0){
                 source = sourceService.getEntityById(a.getId());
                user=source.getUser();
                serverId=source.getClientId();
                platformId = source.getPlatformId();
            }else{
                //查询从表
                followEntity = followService.getEntityById(a.getId());
                user=followEntity.getUser();
                serverId=followEntity.getClientId();
                platformId = followEntity.getPlatformId();
            }

            QuoteClient quoteClient = null;
            FollowTraderEntity followTraderVO = followTraderService.lambdaQuery().eq(FollowTraderEntity::getAccount,user).eq(FollowTraderEntity::getPlatformId, platformId).eq(FollowTraderEntity::getServerId,serverId).one();
            quoteClient=getQuoteClient(followTraderVO.getId(),followTraderVO,quoteClient);
            try {
                quoteClient.ChangePassword(vo.getPassword(), vo.getInvestor());
            } catch (IOException e) {
                throw new ServerException("MT4修改密码异常,检查参数"+"密码："+vo.getPassword()+"是否投资密码"+ vo.getInvestor()+",异常原因"+e);
            } catch (online.mtapi.mt4.Exception.ServerException e) {
                throw new ServerException("mt4修改密码异常,检查参数"+"密码："+vo.getPassword()+"是否投资密码"+ vo.getInvestor()+",异常原因"+e);
            }
            //如果修改登录密码触发
            if (!vo.getInvestor()){
                followTraderVO.setPassword(vo.getPassword());
                followTraderService.updateById(followTraderVO);
                if(followTraderVO.getType().equals(TraderTypeEnum.MASTER_REAL.getType())){
                    reconnect(followTraderVO.getId().toString());
                }else{
                    reconnectSlave(followTraderVO.getId().toString());
                }
                if(type==0){
                    source = sourceService.getEntityById(a.getId());
                    source.setPassword(vo.getPassword());
                    sourceService.edit(source);
                }else{
                    //修改从数据库
                    followEntity.setPassword(vo.getPassword());
                    followService.edit(followEntity);
                }

            }

        });

        return true;
    }

    @Override
    public Boolean orderCloseProfit(OrderCloseAllVO vo) {
        List<AccountModelVO> account = vo.getAccount();
        account.forEach(a->{
            //0=喊单，1=跟单
            Integer type = a.getType();
            Long user=null;
            Integer serverId=null;
            Integer platformId=null;
            if(type==0){
                SourceEntity     source = sourceService.getEntityById(a.getId());
                user=source.getUser();
                serverId=source.getClientId();
                platformId = source.getPlatformId();
            }else{
                //查询从表
                FollowEntity   followEntity = followService.getEntityById(a.getId());
                user=followEntity.getUser();
                serverId=followEntity.getClientId();
                platformId = followEntity.getPlatformId();
            }
            FollowTraderEntity followTraderVO = followTraderService.lambdaQuery().eq(FollowTraderEntity::getAccount,user).eq(FollowTraderEntity::getPlatformId, platformId).eq(FollowTraderEntity::getServerId,serverId).one();
            FollowOrderSendCloseVO followOrderSendCloseVO = new FollowOrderSendCloseVO();
            followOrderSendCloseVO.setFlag(1);
            followOrderSendCloseVO.setIsCloseAll(TraderRepairEnum.CLOSE.getType());
            followOrderSendCloseVO.setTraderId(followTraderVO.getId());
            followOrderSendCloseVO.setProfitOrLoss(ProfitOrLossEnum.Profit.getValue());
            localOrderClose(followOrderSendCloseVO,followTraderVO);
        });
        return  true;
    }

    @Override
    public Boolean orderCloseLoss(OrderCloseAllVO vo) {
        List<AccountModelVO> account = vo.getAccount();
        account.forEach(a->{
            //0=喊单，1=跟单
            Integer type = a.getType();
            Long user=null;
            Integer serverId=null;
            Integer platformId=null;
            if(type==0){
                SourceEntity     source = sourceService.getEntityById(a.getId());
                user=source.getUser();
                serverId=source.getClientId();
                platformId = source.getPlatformId();
            }else{
                //查询从表
                FollowEntity   followEntity = followService.getEntityById(a.getId());
                user=followEntity.getUser();
                serverId=followEntity.getClientId();
                platformId = followEntity.getPlatformId();
            }
            FollowTraderEntity followTraderVO = followTraderService.lambdaQuery().eq(FollowTraderEntity::getAccount,user).eq(FollowTraderEntity::getPlatformId, platformId).eq(FollowTraderEntity::getServerId,serverId).one();
            FollowOrderSendCloseVO followOrderSendCloseVO = new FollowOrderSendCloseVO();
            followOrderSendCloseVO.setFlag(1);
            followOrderSendCloseVO.setIsCloseAll(TraderRepairEnum.CLOSE.getType());
            followOrderSendCloseVO.setTraderId(followTraderVO.getId());
            followOrderSendCloseVO.setProfitOrLoss(ProfitOrLossEnum.Loss.getValue());
            localOrderClose(followOrderSendCloseVO,followTraderVO);
        });
        return  true;
    }

    @Override
    public ExternalSysmbolSpecificationVO symbolParams(Long accountId, Integer accountType) {
        //0=喊单，1=跟单
        Long user=null;
        Integer serverId=null;
        if(accountType==0){
            SourceEntity     source = sourceService.getEntityById(accountId);
            user=source.getUser();
            serverId=source.getClientId();
        }else{
            //查询从表
            FollowEntity   followEntity = followService.getEntityById(accountId);
            user=followEntity.getUser();
            serverId=followEntity.getClientId();
        }
        FollowSysmbolSpecificationEntity sysmbolSpecificationServiceOne = followSysmbolSpecificationService.getOne(new LambdaQueryWrapper<FollowSysmbolSpecificationEntity>().eq(FollowSysmbolSpecificationEntity::getTraderId, user));
        if (ObjectUtil.isEmpty(sysmbolSpecificationServiceOne)){
            return null;
        }
        return convertExternal(sysmbolSpecificationServiceOne);
    }

    private ExternalSysmbolSpecificationVO convertExternal(FollowSysmbolSpecificationEntity sysmbolSpecificationServiceOne) {
        ExternalSysmbolSpecificationVO externalSysmbolSpecificationVO = new ExternalSysmbolSpecificationVO();
        externalSysmbolSpecificationVO.setContractSize(sysmbolSpecificationServiceOne.getContractSize());
        externalSysmbolSpecificationVO.setDigits(sysmbolSpecificationServiceOne.getDigits());
        externalSysmbolSpecificationVO.setSymbol(sysmbolSpecificationServiceOne.getSymbol());
        externalSysmbolSpecificationVO.setLotMax(sysmbolSpecificationServiceOne.getMaxLot());
        externalSysmbolSpecificationVO.setLotMin(sysmbolSpecificationServiceOne.getMinLot());
        externalSysmbolSpecificationVO.setLotStep(sysmbolSpecificationServiceOne.getLotStep());
        externalSysmbolSpecificationVO.setStopsLevel(sysmbolSpecificationServiceOne.getStopsLevel());
        externalSysmbolSpecificationVO.setMarginCurrency(sysmbolSpecificationServiceOne.getMarginCurrency());
        externalSysmbolSpecificationVO.setSwapLong(sysmbolSpecificationServiceOne.getSwapLong());
        externalSysmbolSpecificationVO.setSwapShort(sysmbolSpecificationServiceOne.getSwapShort());
        //默认0
        externalSysmbolSpecificationVO.setSwapType(0);
        return externalSysmbolSpecificationVO;
    }

    private Boolean localOrderClose(FollowOrderSendCloseVO vo, FollowTraderEntity followTraderVO){
        checkParams(vo);
        if (ObjectUtil.isEmpty(followTraderVO)) {
            throw new ServerException("账号不存在");
        }
        //检查vps是否正常
        FollowVpsEntity followVpsEntity = followVps.getById(followTraderVO.getServerId());
        if (followVpsEntity.getIsOpen().equals(CloseOrOpenEnum.CLOSE.getValue()) || followVpsEntity.getConnectionStatus().equals(CloseOrOpenEnum.CLOSE.getValue())) {
            throw new ServerException("VPS服务异常，请检查");
        }
        AbstractApiTrader abstractApiTrader;
        QuoteClient quoteClient = null;
        quoteClient = getQuoteClient(vo.getTraderId(), followTraderVO, quoteClient);
        //获取vps数据
        if (ObjectUtil.isEmpty(quoteClient)){
            throw new ServerException(vo.getTraderId()+"登录异常");
        }
        //判断是否全平,全平走这里逻辑，处理完成退出
        if (vo.getIsCloseAll() == TraderRepairEnum.CLOSE.getType()) {
            //查找mt4订单
            List<Order> openedOrders ;
            if (ObjectUtil.isNotEmpty(vo.getProfitOrLoss())){
                if (vo.getProfitOrLoss().equals(ProfitOrLossEnum.Profit.getValue())){
                    openedOrders = Arrays.stream(quoteClient.GetOpenedOrders()).filter(order -> order.Profit>0&&(order.Type == Buy || order.Type == Sell)).collect(Collectors.toList());
                }else {
                    openedOrders = Arrays.stream(quoteClient.GetOpenedOrders()).filter(order ->  order.Profit<0&&(order.Type == Buy || order.Type == Sell)).collect(Collectors.toList());
                }
            }else {
                openedOrders = Arrays.stream(quoteClient.GetOpenedOrders()).filter(order -> order.Type == Buy || order.Type == Sell).collect(Collectors.toList());
            }
            CountDownLatch countDownLatch = new CountDownLatch(openedOrders.size());
            for (int i = 0; i < openedOrders.size(); i++) {
                Order order = openedOrders.get(i);
                QuoteClient finalQuoteClient = quoteClient;
                ThreadPoolUtils.execute(() -> {
                    FollowOrderSendCloseVO followOrderSendCloseVO = new FollowOrderSendCloseVO();
                    BeanUtils.copyProperties(vo, followOrderSendCloseVO);
                    followOrderSendCloseVO.setOrderNo(order.Ticket);
                    followOrderSendCloseVO.setSymbol(order.Symbol);
                    followOrderSendCloseVO.setSize(order.Lots);
                    handleOrder(finalQuoteClient,followOrderSendCloseVO);
                    countDownLatch.countDown();
                });
            }
          
            try {
                countDownLatch.await();
            } catch (InterruptedException e) {
                log.error("全平处理异常:" + e.getMessage());
                throw new ServerException("全平处理异常:" + e);
            }
            return true;
        }

        boolean result = handleOrder(quoteClient,vo);
        if (!result) {
            throw  new ServerException(followTraderVO.getAccount() + "正在平仓,请稍后再试");
        }
        return true;
    }

    private QuoteClient getQuoteClient(Long traderId, FollowTraderEntity followTraderVO, QuoteClient quoteClient) {
        AbstractApiTrader abstractApiTrader;
        if (followTraderVO.getType().equals(TraderTypeEnum.MASTER_REAL.getType())){
            abstractApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(traderId.toString());
            if (ObjectUtil.isEmpty(abstractApiTrader) || ObjectUtil.isEmpty(abstractApiTrader.quoteClient) || !abstractApiTrader.quoteClient.Connected()) {
                leaderApiTradersAdmin.removeTrader(traderId.toString());
                ConCodeEnum conCodeEnum = leaderApiTradersAdmin.addTrader(followTraderVO);
                if (conCodeEnum == ConCodeEnum.SUCCESS ) {
                    quoteClient =leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(traderId.toString()).quoteClient;
                    LeaderApiTrader leaderApiTrader1 = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(followTraderVO.getId().toString());
                    leaderApiTrader1.startTrade();
                }else if (conCodeEnum == ConCodeEnum.AGAIN){
                    //重复提交
                    abstractApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(followTraderVO.getId().toString());
                    if (ObjectUtil.isNotEmpty(abstractApiTrader)){
                        quoteClient = abstractApiTrader.quoteClient;
                    }
                }
            } else {
                quoteClient = abstractApiTrader.quoteClient;
            }
        }else {
            abstractApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(traderId.toString());
            if (ObjectUtil.isEmpty(abstractApiTrader) || ObjectUtil.isEmpty(abstractApiTrader.quoteClient) || !abstractApiTrader.quoteClient.Connected()) {
                copierApiTradersAdmin.removeTrader(followTraderVO.getId().toString());
                ConCodeEnum conCodeEnum = copierApiTradersAdmin.addTrader(followTraderVO);
                if (conCodeEnum == ConCodeEnum.SUCCESS) {
                    quoteClient =copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(traderId.toString()).quoteClient;
                    CopierApiTrader copierApiTrader1 = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(followTraderVO.getId().toString());
                    copierApiTrader1.setTrader(followTraderVO);
                }else if (conCodeEnum == ConCodeEnum.AGAIN){
                    //重复提交
                    CopierApiTrader copierApiTrader1 = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(followTraderVO.getId().toString());
                    if (ObjectUtil.isNotEmpty(copierApiTrader1)) {
                        quoteClient = copierApiTrader1.quoteClient;
                    }
                }
            } else {
                quoteClient = abstractApiTrader.quoteClient;
            }
        }
        return quoteClient;
    }


    private Boolean handleOrder(QuoteClient quoteClient, FollowOrderSendCloseVO vo) {
        //判断是否正在平仓
        if (ObjectUtil.isNotEmpty(redisCache.get(Constant.TRADER_CLOSE + vo.getTraderId()))) {
            return false;
        }
        //登录
        OrderClient oc;
        if (ObjectUtil.isNotEmpty(quoteClient.OrderClient)) {
            oc = quoteClient.OrderClient;
        } else {
            oc = new OrderClient(quoteClient);
        }
        //指定平仓
        List<FollowOrderDetailEntity> detailServiceOnes = followOrderDetailService.list(new LambdaQueryWrapper<FollowOrderDetailEntity>().eq(FollowOrderDetailEntity::getOrderNo, vo.getOrderNo()));

        if (ObjectUtil.isNotEmpty(detailServiceOnes)) {
            detailServiceOnes.forEach(detailServiceOne->{
                updateCloseOrder(detailServiceOne, quoteClient, oc, null);
                ThreadPoolUtils.execute(() -> {
                    //进行平仓滑点分析
                    updateCloseSlip(vo.getTraderId(), detailServiceOne.getSymbol(), null, 2);
                });
            });
        } else {
            try {
                Order order = quoteClient.GetOpenedOrder(vo.getOrderNo());
                if (ObjectUtil.isEmpty(quoteClient.GetQuote(order.Symbol))) {
                    //订阅
                    quoteClient.Subscribe(vo.getSymbol());
                }
                double bid =0;
                double ask =0;
                int loopTimes=1;
                QuoteEventArgs quoteEventArgs = null;
                while (quoteEventArgs == null && quoteClient.Connected()) {
                    quoteEventArgs = quoteClient.GetQuote(vo.getSymbol());
                    if (++loopTimes > 20) {
                        break;
                    } else {
                        Thread.sleep(50);
                    }
                }
                bid =ObjectUtil.isNotEmpty(quoteEventArgs.Bid)?quoteEventArgs.Bid:0;
                ask =ObjectUtil.isNotEmpty(quoteEventArgs.Ask)?quoteEventArgs.Bid:0;

                if (order.Type.getValue() == Buy.getValue()) {
                    oc.OrderClose(order.Symbol, vo.getOrderNo(), order.Lots, bid, 0);
                } else {
                    oc.OrderClose(order.Symbol, vo.getOrderNo(), order.Lots, ask, 0);
                }
            } catch (Exception e) {
                log.error(vo.getOrderNo()+"平仓出错" + e.getMessage());
            }
        }
        return true;
    }


    private void updateCloseSlip(long traderId, String symbol, FollowOrderCloseEntity followOrderCloseEntity, Integer flag) {
        if (flag != 2) {
            //查看平仓所有数据
            List<FollowOrderDetailEntity> list = followOrderDetailService.list(new LambdaQueryWrapper<FollowOrderDetailEntity>().eq(FollowOrderDetailEntity::getCloseId, followOrderCloseEntity.getId()));
            followOrderCloseEntity.setFailNum((int) list.stream().filter(o -> ObjectUtil.isNotEmpty(o.getRemark())).count());
            followOrderCloseEntity.setSuccessNum(list.size() - followOrderCloseEntity.getFailNum());
            if (flag == 1) {
                //间隔平仓判断
                if (followOrderCloseEntity.getTotalNum() == list.size()) {
                    followOrderCloseEntity.setFinishTime(LocalDateTime.now());
                    followOrderCloseEntity.setStatus(CloseOrOpenEnum.OPEN.getValue());
                }
            } else {
                //同步平仓直接结束
                followOrderCloseEntity.setFinishTime(LocalDateTime.now());
                followOrderCloseEntity.setStatus(CloseOrOpenEnum.OPEN.getValue());
            }
            followOrderCloseService.updateById(followOrderCloseEntity);
        }

        LambdaQueryWrapper<FollowOrderDetailEntity> followLambdaQueryWrapper = new LambdaQueryWrapper<>();
        followLambdaQueryWrapper.eq(FollowOrderDetailEntity::getTraderId, traderId)
                .isNotNull(FollowOrderDetailEntity::getClosePrice)
                .isNotNull(FollowOrderDetailEntity::getRequestClosePrice)
                .eq(FollowOrderDetailEntity::getIsExternal,CloseOrOpenEnum.CLOSE.getValue())
                .isNull(FollowOrderDetailEntity::getClosePriceSlip);
        //查询需要滑点分析的数据 有平仓价格但是无平仓滑点
        if (ObjectUtil.isNotEmpty(symbol)) {
            followLambdaQueryWrapper.eq(FollowOrderDetailEntity::getSymbol, symbol);
        }
        List<FollowOrderDetailEntity> list = followOrderDetailService.list(followLambdaQueryWrapper);
        //获取symbol信息
        Map<String, FollowSysmbolSpecificationEntity> specificationEntityMap = followSysmbolSpecificationService.getByTraderId(traderId);
        //开始平仓
        list.parallelStream().forEach(o -> {
            FollowSysmbolSpecificationEntity followSysmbolSpecificationEntity = specificationEntityMap.get(o.getSymbol());
            BigDecimal hd;
            if (followSysmbolSpecificationEntity.getProfitMode().equals("Forex")) {
                //如果forex 并包含JPY 也是100
                if (o.getSymbol().contains("JPY")) {
                    hd = new BigDecimal("100");
                } else {
                    hd = new BigDecimal("10000");
                }
            } else {
                //如果非forex 都是 100
                hd = new BigDecimal("100");
            }
            o.setClosePriceSlip(o.getClosePrice().subtract(o.getRequestClosePrice()).multiply(hd).abs());
            followOrderDetailService.updateById(o);
        });
    }

    private void updateCloseOrder(FollowOrderDetailEntity followOrderDetailEntity, QuoteClient quoteClient, OrderClient oc, FollowOrderCloseEntity followOrderCloseEntity) {
        String symbol = followOrderDetailEntity.getSymbol();
        Integer orderNo = followOrderDetailEntity.getOrderNo();
        try {
            if (ObjectUtil.isEmpty(quoteClient.GetQuote(symbol))) {
                //订阅
                quoteClient.Subscribe(symbol);
            }
            double bid =0;
            double ask =0;
            int loopTimes=1;
            QuoteEventArgs quoteEventArgs = null;
            while (quoteEventArgs == null && quoteClient.Connected()) {
                quoteEventArgs = quoteClient.GetQuote(symbol);
                if (++loopTimes > 20) {
                    break;
                } else {
                    Thread.sleep(50);
                }
            }
            bid =ObjectUtil.isNotEmpty(quoteEventArgs.Bid)?quoteEventArgs.Bid:0;
            ask =ObjectUtil.isNotEmpty(quoteEventArgs.Ask)?quoteEventArgs.Bid:0;
            LocalDateTime nowdate = LocalDateTime.now();
            log.info("平仓信息{},{},{},{},{}", symbol, orderNo, followOrderDetailEntity.getSize(), bid, ask);
            if (ObjectUtil.isNotEmpty(followOrderCloseEntity)) {
                followOrderDetailEntity.setCloseId(followOrderCloseEntity.getId());
            }
            Order orderResult;
            if (followOrderDetailEntity.getType() == Buy.getValue()) {
                orderResult = oc.OrderClose(symbol, orderNo, followOrderDetailEntity.getSize().doubleValue(), bid, 0);
                followOrderDetailEntity.setRequestClosePrice(BigDecimal.valueOf(bid));
            } else {
                orderResult = oc.OrderClose(symbol, orderNo, followOrderDetailEntity.getSize().doubleValue(), ask, 0);
                followOrderDetailEntity.setRequestClosePrice(BigDecimal.valueOf(ask));
            }
            log.info("订单 " + orderNo + ": 平仓 " + orderResult);
            //保存平仓信息
            followOrderDetailEntity.setResponseCloseTime(LocalDateTime.now());
            followOrderDetailEntity.setRequestCloseTime(nowdate);
            followOrderDetailEntity.setCloseTime(orderResult.CloseTime);
            followOrderDetailEntity.setClosePrice(BigDecimal.valueOf(orderResult.ClosePrice));
            followOrderDetailEntity.setSwap(BigDecimal.valueOf(orderResult.Swap));
            followOrderDetailEntity.setCommission(BigDecimal.valueOf(orderResult.Commission));
            followOrderDetailEntity.setProfit(BigDecimal.valueOf(orderResult.Profit));
            followOrderDetailEntity.setCloseStatus(CloseOrOpenEnum.OPEN.getValue());
        } catch (Exception e) {
            log.error(orderNo+"平仓出错" + e.getMessage());
            if (ObjectUtil.isNotEmpty(followOrderCloseEntity)) {
                followOrderDetailEntity.setRemark("平仓出错" + e.getMessage());
            }
        }
        followOrderDetailService.updateById(followOrderDetailEntity);
        if (ObjectUtil.isNotEmpty(followOrderCloseEntity)) {
            followOrderCloseService.updateById(followOrderCloseEntity);
        }
    }
    private void  checkParams(FollowOrderSendCloseVO vo){
        if (ObjectUtil.isEmpty(vo.getTraderId()) ) {
            throw new ServerException("账号id不能为空");
        }
    }
    private void reconnect(String traderId) {
        try{
            leaderApiTradersAdmin.removeTrader(traderId);
            FollowTraderEntity followTraderEntity = followTraderService.getById(traderId);
            ConCodeEnum conCodeEnum = leaderApiTradersAdmin.addTrader(followTraderService.getById(traderId));
            if (conCodeEnum != ConCodeEnum.SUCCESS&&conCodeEnum != ConCodeEnum.AGAIN) {
                followTraderEntity.setStatus(TraderStatusEnum.ERROR.getValue());
                followTraderService.updateById(followTraderEntity);
                log.error("喊单者:[{}-{}-{}]重连失败，请校验", followTraderEntity.getId(), followTraderEntity.getAccount(), followTraderEntity.getServerName());
                throw new ServerException("重连失败");
            }  else if (conCodeEnum == ConCodeEnum.AGAIN){
                log.info("喊单者:[{}-{}-{}]启动重复", followTraderEntity.getId(), followTraderEntity.getAccount(), followTraderEntity.getServerName());
            } else {
                LeaderApiTrader leaderApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(traderId);
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
            if (conCodeEnum != ConCodeEnum.SUCCESS&&conCodeEnum != ConCodeEnum.AGAIN) {
                followTraderEntity.setStatus(TraderStatusEnum.ERROR.getValue());
                followTraderService.updateById(followTraderEntity);
                log.error("跟单者:[{}-{}-{}]重连失败，请校验", followTraderEntity.getId(), followTraderEntity.getAccount(), followTraderEntity.getServerName());
                throw new ServerException("重连失败");
            }  else if (conCodeEnum == ConCodeEnum.AGAIN){
                log.info("跟单者:[{}-{}-{}]启动重复", followTraderEntity.getId(), followTraderEntity.getAccount(), followTraderEntity.getServerName());
            } else {
                CopierApiTrader copierApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(traderId);
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

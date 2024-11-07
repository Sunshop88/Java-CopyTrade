package net.maku.subcontrol.trader.strategy;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.enums.DirectionEnum;
import net.maku.followcom.enums.TraderLogEnum;
import net.maku.followcom.enums.TraderLogTypeEnum;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.followcom.service.*;
import net.maku.followcom.service.impl.*;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.subcontrol.entity.FollowOrderHistoryEntity;
import net.maku.subcontrol.entity.FollowRepairOrderEntity;
import net.maku.subcontrol.entity.FollowSubscribeOrderEntity;
import net.maku.subcontrol.enums.TraderRepairOrderEnum;
import net.maku.subcontrol.pojo.CachedCopierOrderInfo;
import net.maku.subcontrol.rule.FollowRule;
import net.maku.subcontrol.service.FollowOrderHistoryService;
import net.maku.subcontrol.service.FollowRepairOrderService;
import net.maku.subcontrol.service.FollowSubscribeOrderService;
import net.maku.subcontrol.service.impl.FollowOrderHistoryServiceImpl;
import net.maku.subcontrol.service.impl.FollowRepairOrderServiceImpl;
import net.maku.subcontrol.service.impl.FollowSubscribeOrderServiceImpl;
import net.maku.subcontrol.trader.AbstractApiTrader;
import online.mtapi.mt4.Exception.ConnectException;
import online.mtapi.mt4.Exception.InvalidSymbolException;
import online.mtapi.mt4.Exception.TimeoutException;
import online.mtapi.mt4.Op;
import online.mtapi.mt4.QuoteEventArgs;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static online.mtapi.mt4.Op.Buy;
import static online.mtapi.mt4.Op.Sell;


/**
 * @author samson bruce
 */
@Slf4j
public class AbstractOperation {
    protected FollowTraderSubscribeService leaderCopierService;
    protected FollowSubscribeOrderService openOrderMappingService;
    protected RedisUtil redisUtil;
    protected String mapKey;
    protected FollowRule followRule;
    protected ScheduledThreadPoolExecutor threeStrategyThreadPoolExecutor;
    protected FollowOrderHistoryService followOrderHistoryService;
    protected FollowVarietyService followVarietyService;
    protected FollowTraderService followTraderService;
    protected FollowPlatformService followPlatformService;
    protected FollowVpsService followVpsService;
    protected FollowTraderLogService followTraderLogService;
    protected FollowRepairOrderService followRepairOrderService;
    int whileTimes = 20;

    public AbstractOperation(FollowTraderEntity trader) {
        this.mapKey = trader.getId() + "#" + trader.getAccount();
        redisUtil = SpringContextUtils.getBean(RedisUtil.class);
        leaderCopierService = SpringContextUtils.getBean(FollowTraderSubscribeServiceImpl.class);
        openOrderMappingService = SpringContextUtils.getBean(FollowSubscribeOrderServiceImpl.class);
        this.threeStrategyThreadPoolExecutor = ThreadPoolUtils.getScheduledExecute();
        followRule = new FollowRule();
        this.followOrderHistoryService=SpringContextUtils.getBean(FollowOrderHistoryServiceImpl.class);
        this.followVarietyService=SpringContextUtils.getBean(FollowVarietyServiceImpl.class);
        this.followTraderService=SpringContextUtils.getBean(FollowTraderServiceImpl.class);
        this.followPlatformService=SpringContextUtils.getBean(FollowPlatformServiceImpl.class);
        this.followVpsService=SpringContextUtils.getBean(FollowVpsServiceImpl.class);
        this.followTraderLogService=SpringContextUtils.getBean(FollowTraderLogServiceImpl.class);
        this.followRepairOrderService=SpringContextUtils.getBean(FollowRepairOrderServiceImpl.class);
    }

    protected String comment(EaOrderInfo orderInfo) {
        //#喊单者账号(36进制)#喊单者订单号(订单号)#AUTO
        return "#" + Long.toString(Long.parseLong(orderInfo.getAccount()), 36) + "#" + Long.toString(orderInfo.getTicket(), 36) + "#FO_AUTO";
    }

    protected Op op(EaOrderInfo orderInfo, FollowTraderSubscribeEntity leaderCopier) {
        Op op = Op.forValue(orderInfo.getType());
        if (DirectionEnum.REVERSE.getType().equals(leaderCopier.getFollowDirection())) {
            //反向跟单时不会跟随止损、止盈
            orderInfo.setSl(0.0);
            orderInfo.setTp(0.0);
            if (op == Buy) {
                op = Sell;
            } else if (op == Sell) {
                op = Buy;
            }
        } else {
            return op;
        }
        return op;
    }

    protected double price(AbstractApiTrader abstract4ApiTrader, CachedCopierOrderInfo cachedCopierOrderInfo) throws InvalidSymbolException, TimeoutException, ConnectException {
        int loopTimes = 0;
        QuoteEventArgs quoteEventArgs = abstract4ApiTrader.quoteClient.GetQuote(cachedCopierOrderInfo.getSlaveSymbol());
        while (quoteEventArgs == null && abstract4ApiTrader.quoteClient.Connected() && loopTimes < whileTimes) {
            try {
                loopTimes++;
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
            }
            abstract4ApiTrader.quoteClient.Subscribe(cachedCopierOrderInfo.getSlaveSymbol());
            quoteEventArgs = abstract4ApiTrader.quoteClient.GetQuote(cachedCopierOrderInfo.getSlaveSymbol());
        }
        if (quoteEventArgs != null) {
            return cachedCopierOrderInfo.getSlaveType() == 0 ? quoteEventArgs.Bid : quoteEventArgs.Ask;
        } else {
            return cachedCopierOrderInfo.getOpenPrice() == null ? 1 : cachedCopierOrderInfo.getOpenPrice();
        }
    }


    public void orderSend(ConsumerRecord<String, Object> record, int retry, FollowTraderEntity trader) {
        EaOrderInfo orderInfo = (EaOrderInfo) record.value();
        //查看跟单关系
        List<FollowTraderSubscribeEntity> subscribeEntityList = leaderCopierService.list(new LambdaQueryWrapper<FollowTraderSubscribeEntity>().eq(FollowTraderSubscribeEntity::getMasterId, orderInfo.getMasterId())
                .eq(FollowTraderSubscribeEntity::getFollowStatus,CloseOrOpenEnum.OPEN.getValue())
                .eq(FollowTraderSubscribeEntity::getFollowOpen,CloseOrOpenEnum.OPEN.getValue()));
        List<Long> slaveList = subscribeEntityList.stream().map(o -> o.getSlaveId()).toList();
        threeStrategyThreadPoolExecutor.schedule(()->{
            //生成日志
            FollowTraderLogEntity followTraderLogEntity = new FollowTraderLogEntity();
            followTraderLogEntity.setTraderType(TraderLogEnum.FOLLOW_OPERATION.getType());
            FollowVpsEntity followVpsEntity = followVpsService.getById(trader.getServerId());
            followTraderLogEntity.setVpsId(followVpsEntity.getId());
            followTraderLogEntity.setVpsClient(followVpsEntity.getClientId());
            followTraderLogEntity.setVpsName(followVpsEntity.getName());
            followTraderLogEntity.setCreateTime(LocalDateTime.now());
            followTraderLogEntity.setType(TraderLogTypeEnum.SEND.getType());
            String remark= FollowConstant.FOLLOW_SEND+"策略账号="+orderInfo.getAccount();
            List<FollowSubscribeOrderEntity> list = openOrderMappingService.list(new LambdaQueryWrapper<FollowSubscribeOrderEntity>().eq(FollowSubscribeOrderEntity::getMasterTicket, orderInfo.getTicket()));
            if (ObjectUtil.isNotEmpty(list)){
                //跟单信息
                List<String> remarkList=new ArrayList<>();
                for (int i=0;i<list.size();i++){
                    FollowSubscribeOrderEntity o = list.get(i);
                    remarkList.add(i+1+".跟单账号="+o.getSlaveAccount()+",单号="+o.getSlaveTicket()+",品种="+o.getSlaveSymbol()+",手数="+o.getSlaveLots()+",类型="+ Op.forValue(o.getSlaveType()).name());
                }
                remark=remark+remarkList;
            }else {
                remark=remark+"暂无跟单";
            }
            followTraderLogEntity.setLogDetail(remark);
            followTraderLogService.save(followTraderLogEntity);
            //对比当前下单关系和已下单情况
            List<Long> result = new ArrayList<>(slaveList);
            result.removeAll(list.stream().map(o->o.getSlaveId()).toList());
            if (ObjectUtil.isNotEmpty(result)){
                result.parallelStream().forEach(o->{
                    //漏单记录
                    FollowRepairOrderEntity followRepairOrderEntity = new FollowRepairOrderEntity();
                    followRepairOrderEntity.setMasterId(orderInfo.getMasterId());
                    followRepairOrderEntity.setType(TraderRepairOrderEnum.SEND.getType());
                    followRepairOrderEntity.setCreateTime(LocalDateTime.now());
                    followRepairOrderEntity.setMasterAccount(orderInfo.getAccount());
                    followRepairOrderEntity.setMasterLots(BigDecimal.valueOf(orderInfo.getLots()));
                    followRepairOrderEntity.setMasterOpenTime(orderInfo.getOpenTime());
                    followRepairOrderEntity.setMasterSymbol(orderInfo.getSymbol());
                    followRepairOrderEntity.setMasterTicket(orderInfo.getTicket());
                    followRepairOrderEntity.setSlaveAccount(followTraderService.get(o).getAccount());
                    followRepairOrderEntity.setSlaveId(o);
                    followRepairOrderService.save(followRepairOrderEntity);
                });
            }

        },5, TimeUnit.SECONDS);
    }

    public void orderClose(ConsumerRecord<String, Object> record, int retry, FollowTraderEntity trader) {
        //生成历史订单
        EaOrderInfo orderInfo = (EaOrderInfo) record.value();
        log.info("生成历史订单"+orderInfo.getTicket());
        FollowOrderHistoryEntity followOrderHistory=new FollowOrderHistoryEntity();
        BeanUtil.copyProperties(orderInfo,followOrderHistory);
        followOrderHistory.setOrderNo(orderInfo.getTicket());
        followOrderHistory.setClosePrice(BigDecimal.valueOf(orderInfo.getClosePrice()));
        followOrderHistory.setOpenPrice(BigDecimal.valueOf(orderInfo.getOpenPrice()));
        followOrderHistory.setTraderId(trader.getId());
        followOrderHistory.setAccount(trader.getAccount());
        followOrderHistory.setSize(BigDecimal.valueOf(orderInfo.getLots()));
        followOrderHistory.setCreateTime(LocalDateTime.now());
        followOrderHistoryService.save(followOrderHistory);
        //查看跟单关系
        List<FollowTraderSubscribeEntity> subscribeEntityList = leaderCopierService.list(new LambdaQueryWrapper<FollowTraderSubscribeEntity>().eq(FollowTraderSubscribeEntity::getMasterId, orderInfo.getMasterId())
                .eq(FollowTraderSubscribeEntity::getFollowStatus,CloseOrOpenEnum.OPEN.getValue())
                .eq(FollowTraderSubscribeEntity::getFollowClose,CloseOrOpenEnum.OPEN.getValue()));
        List<Long> slaveList = subscribeEntityList.stream().map(o -> o.getSlaveId()).toList();
        threeStrategyThreadPoolExecutor.schedule(()->{
            //生成日志
            FollowTraderLogEntity followTraderLogEntity = new FollowTraderLogEntity();
            followTraderLogEntity.setTraderType(TraderLogEnum.FOLLOW_OPERATION.getType());
            FollowVpsEntity followVpsEntity = followVpsService.getById(trader.getServerId());
            followTraderLogEntity.setVpsId(followVpsEntity.getId());
            followTraderLogEntity.setVpsClient(followVpsEntity.getClientId());
            followTraderLogEntity.setVpsName(followVpsEntity.getName());
            followTraderLogEntity.setCreateTime(LocalDateTime.now());
            String remark= FollowConstant.FOLLOW_CLOSE+"策略账号="+orderInfo.getAccount();
            List<FollowSubscribeOrderEntity> list = openOrderMappingService.list(new LambdaQueryWrapper<FollowSubscribeOrderEntity>().eq(FollowSubscribeOrderEntity::getMasterTicket, orderInfo.getTicket()).isNotNull(FollowSubscribeOrderEntity::getSlaveCloseTime));
            if (ObjectUtil.isNotEmpty(list)){
                //跟单信息
                List<String> remarkList=new ArrayList<>();
                for (int i=0;i<list.size();i++){
                    FollowSubscribeOrderEntity o = list.get(i);
                    remarkList.add(i+1+".跟单账号="+o.getSlaveAccount()+",单号="+o.getSlaveTicket()+",品种="+o.getSlaveSymbol()+",手数="+o.getSlaveLots()+",类型="+ Op.forValue(o.getSlaveType()).name());
                }
                remark=remark+remarkList;
            }else {
                remark=remark+"暂无跟单";
            }
            followTraderLogEntity.setLogDetail(remark);
            followTraderLogService.save(followTraderLogEntity);  //对比当前跟单关系和已平仓情况
            List<Long> result = new ArrayList<>(slaveList);
            result.removeAll(list.stream().map(o->o.getSlaveId()).toList());
            List<FollowSubscribeOrderEntity> followSubscribeOrderEntities = openOrderMappingService.list(new LambdaQueryWrapper<FollowSubscribeOrderEntity>().eq(FollowSubscribeOrderEntity::getMasterTicket, orderInfo.getTicket()).isNull(FollowSubscribeOrderEntity::getSlaveCloseTime));
            Map<Long, FollowSubscribeOrderEntity> collect = followSubscribeOrderEntities.stream().collect(Collectors.toMap(FollowSubscribeOrderEntity::getSlaveId, i -> i));
            if (ObjectUtil.isNotEmpty(result)){
                result.parallelStream().forEach(o->{
                    //漏单记录
                    FollowRepairOrderEntity followRepairOrderEntity = new FollowRepairOrderEntity();
                    followRepairOrderEntity.setMasterId(orderInfo.getMasterId());
                    followRepairOrderEntity.setType(TraderRepairOrderEnum.CLOSE.getType());
                    followRepairOrderEntity.setCreateTime(LocalDateTime.now());
                    followRepairOrderEntity.setMasterAccount(orderInfo.getAccount());
                    followRepairOrderEntity.setMasterLots(BigDecimal.valueOf(orderInfo.getLots()));
                    followRepairOrderEntity.setMasterOpenTime(orderInfo.getOpenTime());
                    followRepairOrderEntity.setMasterSymbol(orderInfo.getSymbol());
                    followRepairOrderEntity.setMasterTicket(orderInfo.getTicket());
                    followRepairOrderEntity.setSlaveAccount(followTraderService.get(o).getAccount());
                    followRepairOrderEntity.setSlaveId(o);
                    //保存订单关系ID
                    followRepairOrderEntity.setSubscribeId(collect.get(o).getId());
                    followRepairOrderService.save(followRepairOrderEntity);
                });
            }

        },5, TimeUnit.SECONDS);
    }

    public void orderModify(ConsumerRecord<String, Object> record, int retry, FollowTraderEntity trader) {
        log.info("{}-{}-{}", record, retry, trader);
    }
}

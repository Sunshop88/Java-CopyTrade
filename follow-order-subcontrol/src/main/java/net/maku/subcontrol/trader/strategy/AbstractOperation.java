package net.maku.subcontrol.trader.strategy;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.*;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.followcom.service.*;
import net.maku.followcom.service.impl.*;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.subcontrol.entity.FollowOrderHistoryEntity;
import net.maku.subcontrol.entity.FollowSubscribeOrderEntity;
import net.maku.subcontrol.pojo.CachedCopierOrderInfo;
import net.maku.subcontrol.rule.FollowRule;
import net.maku.subcontrol.service.FollowOrderHistoryService;
import net.maku.subcontrol.service.FollowSubscribeOrderService;
import net.maku.subcontrol.service.impl.FollowOrderHistoryServiceImpl;
import net.maku.subcontrol.service.impl.FollowSubscribeOrderServiceImpl;
import net.maku.subcontrol.trader.AbstractApiTrader;
import online.mtapi.mt4.Exception.ConnectException;
import online.mtapi.mt4.Exception.InvalidSymbolException;
import online.mtapi.mt4.Exception.TimeoutException;
import online.mtapi.mt4.Op;
import online.mtapi.mt4.QuoteEventArgs;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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
    int whileTimes = 20;

    public AbstractOperation(FollowTraderEntity trader) {
        this.mapKey = trader.getId() + "#" + trader.getAccount();
        this.redisUtil = SpringContextUtils.getBean(RedisUtil.class);
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
    }

    protected String comment(EaOrderInfo orderInfo) {
        //#喊单者账号(36进制)#喊单者订单号(订单号)#AUTO
        return "#" + Long.toString(Long.parseLong(orderInfo.getAccount()), 36) + "#" + Long.toString(orderInfo.getTicket(), 36) + "#FO_AUTO";
    }

    //解析comment
    public static long recomment(String comment) {
        // 去掉头尾的 `#` 并按 `#` 分割
        String[] parts = comment.split("#");
        // 解析 36 进制的 account 和 ticket
        long ticket = Long.parseLong(parts[2], 36);
        return ticket;
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
        //保存所需要下单的用户到redis，用备注记录 set类型存储
        String comment = comment(orderInfo);
        orderInfo.setSlaveComment(comment);
        //保存下单信息
        subscribeEntityList.forEach(o->{
            redisUtil.lSet(Constant.FOLLOW_REPAIR_SEND+o.getId(),orderInfo);
        });
        threeStrategyThreadPoolExecutor.schedule(()->{
            //生成记录
            FollowSubscribeOrderEntity openOrderMapping = new FollowSubscribeOrderEntity(orderInfo,trader);
            openOrderMappingService.save(openOrderMapping);
            //生成日志
            FollowTraderLogEntity followTraderLogEntity = new FollowTraderLogEntity();
            followTraderLogEntity.setTraderType(TraderLogEnum.FOLLOW_OPERATION.getType());
            FollowVpsEntity followVpsEntity = followVpsService.getById(trader.getServerId());
            followTraderLogEntity.setVpsId(followVpsEntity.getId());
            followTraderLogEntity.setVpsClient(followVpsEntity.getClientId());
            followTraderLogEntity.setVpsName(followVpsEntity.getName());
            followTraderLogEntity.setCreateTime(LocalDateTime.now());
            followTraderLogEntity.setType(TraderLogTypeEnum.SEND.getType());
            String remark= FollowConstant.FOLLOW_SEND+"策略账号="+orderInfo.getAccount()+",单号="+orderInfo.getTicket()+",品种="+orderInfo.getSymbol()+",手数="+orderInfo.getLots()+",类型="+ Op.forValue(orderInfo.getType()).name();
            followTraderLogEntity.setLogDetail(remark);
            followTraderLogService.save(followTraderLogEntity);
        },100,TimeUnit.MILLISECONDS);
    }

    public void orderClose(ConsumerRecord<String, Object> record, int retry, FollowTraderEntity trader) {
        EaOrderInfo orderInfo = (EaOrderInfo) record.value();
        //修改喊单订单记录
        FollowSubscribeOrderEntity subscribeOrderEntity = openOrderMappingService.getOne(new LambdaQueryWrapper<FollowSubscribeOrderEntity>().eq(FollowSubscribeOrderEntity::getMasterTicket, orderInfo.getTicket()).eq(FollowSubscribeOrderEntity::getMasterOrSlave, TraderTypeEnum.MASTER_REAL.getType()));
        if (ObjectUtil.isNotEmpty(subscribeOrderEntity)){
            subscribeOrderEntity.setMasterCloseTime(orderInfo.getCloseTime());
            subscribeOrderEntity.setMasterProfit(orderInfo.getProfit());
            subscribeOrderEntity.setDetectedCloseTime(orderInfo.getDetectedCloseTime());
            subscribeOrderEntity.setExtra("平仓成功");
            openOrderMappingService.updateById(subscribeOrderEntity);
        }
        //修改喊单的跟单订单记录
        List<FollowSubscribeOrderEntity> subscribeOrderEntityList = openOrderMappingService.list(new LambdaQueryWrapper<FollowSubscribeOrderEntity>().eq(FollowSubscribeOrderEntity::getMasterTicket, orderInfo.getTicket()).eq(FollowSubscribeOrderEntity::getMasterOrSlave, TraderTypeEnum.SLAVE_REAL.getType()));
        subscribeOrderEntityList.forEach(o->{
            o.setMasterCloseTime(orderInfo.getCloseTime());
            o.setMasterProfit(orderInfo.getProfit());
            o.setDetectedCloseTime(orderInfo.getDetectedCloseTime());
            openOrderMappingService.updateById(o);
        });
        BigDecimal leaderProfit = orderInfo.getSwap().add(orderInfo.getCommission()).add(orderInfo.getProfit()).setScale(2, RoundingMode.HALF_UP);
        //生成历史订单
        log.info("生成历史订单"+orderInfo.getTicket());
        FollowOrderHistoryEntity followOrderHistory=new FollowOrderHistoryEntity();
        followOrderHistory.setTraderId(trader.getId());
        followOrderHistory.setAccount(trader.getAccount());
        followOrderHistory.setOrderNo(orderInfo.getTicket());
        followOrderHistory.setClosePrice(BigDecimal.valueOf(orderInfo.getClosePrice()));
        followOrderHistory.setOpenPrice(BigDecimal.valueOf(orderInfo.getOpenPrice()));
        followOrderHistory.setOpenTime(orderInfo.getOpenTime());
        followOrderHistory.setCloseTime(orderInfo.getCloseTime());
        followOrderHistory.setProfit(leaderProfit);
        followOrderHistory.setComment(orderInfo.getComment());
        followOrderHistory.setSize(BigDecimal.valueOf(orderInfo.getLots()));
        followOrderHistory.setType(orderInfo.getType());
        followOrderHistory.setSwap(orderInfo.getSwap());
        followOrderHistory.setMagic((int)orderInfo.getMagic());
        followOrderHistory.setTp(BigDecimal.valueOf(orderInfo.getTp()));
        followOrderHistory.setSl(BigDecimal.valueOf(orderInfo.getSl()));
        followOrderHistory.setSymbol(orderInfo.getSymbol());
        followOrderHistoryService.save(followOrderHistory);
        //查看跟单关系
        List<FollowTraderSubscribeEntity> subscribeEntityList = leaderCopierService.list(new LambdaQueryWrapper<FollowTraderSubscribeEntity>().eq(FollowTraderSubscribeEntity::getMasterId, orderInfo.getMasterId())
                .eq(FollowTraderSubscribeEntity::getFollowStatus,CloseOrOpenEnum.OPEN.getValue())
                .eq(FollowTraderSubscribeEntity::getFollowClose,CloseOrOpenEnum.OPEN.getValue()));
        //保存所需要平仓的用户到redis，用备注记录 set类型存储
        String comment = comment(orderInfo);
        orderInfo.setSlaveComment(comment);
        subscribeEntityList.forEach(o->redisUtil.lSet(Constant.FOLLOW_REPAIR_CLOSE+o.getId(),orderInfo));
        threeStrategyThreadPoolExecutor.schedule(()->{
            //生成日志
            FollowTraderLogEntity followTraderLogEntity = new FollowTraderLogEntity();
            followTraderLogEntity.setTraderType(TraderLogEnum.FOLLOW_OPERATION.getType());
            FollowVpsEntity followVpsEntity = followVpsService.getById(trader.getServerId());
            followTraderLogEntity.setVpsId(followVpsEntity.getId());
            followTraderLogEntity.setVpsClient(followVpsEntity.getClientId());
            followTraderLogEntity.setVpsName(followVpsEntity.getName());
            followTraderLogEntity.setCreateTime(LocalDateTime.now());
            String remark= FollowConstant.FOLLOW_CLOSE+"策略账号="+orderInfo.getAccount()+",单号="+orderInfo.getTicket()+",品种="+orderInfo.getSymbol()+",手数="+orderInfo.getLots()+",类型="+ Op.forValue(orderInfo.getType()).name();
            followTraderLogEntity.setLogDetail(remark);
            followTraderLogService.save(followTraderLogEntity);
        },100, TimeUnit.MILLISECONDS);
    }

    public void orderModify(ConsumerRecord<String, Object> record, int retry, FollowTraderEntity trader) {
        log.info("{}-{}-{}", record, retry, trader);
    }
}

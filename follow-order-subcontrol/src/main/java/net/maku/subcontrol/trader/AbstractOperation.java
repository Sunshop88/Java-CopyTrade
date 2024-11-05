package net.maku.subcontrol.trader;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.DirectionEnum;
import net.maku.followcom.enums.TraderLogEnum;
import net.maku.followcom.enums.TraderLogTypeEnum;
import net.maku.followcom.enums.TraderTypeEnum;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.followcom.service.*;
import net.maku.followcom.service.impl.*;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.followcom.vo.FollowTraderVO;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.subcontrol.pojo.CachedCopierOrderInfo;
import net.maku.subcontrol.rule.FollowRule;
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
    protected FollowOrderHistoryService historyOrderService;
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
        redisUtil = SpringContextUtils.getBean(RedisUtil.class);
        leaderCopierService = SpringContextUtils.getBean(FollowTraderSubscribeServiceImpl.class);
        openOrderMappingService = SpringContextUtils.getBean(FollowSubscribeOrderServiceImpl.class);
        historyOrderService = SpringContextUtils.getBean(FollowOrderHistoryServiceImpl.class);
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
        threeStrategyThreadPoolExecutor.schedule(()->{
            //生成日志
            EaOrderInfo orderInfo = (EaOrderInfo) record.value();
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
            followTraderLogService.save(followTraderLogEntity);
        },5, TimeUnit.SECONDS);
    }

    public void orderModify(ConsumerRecord<String, Object> record, int retry, FollowTraderEntity trader) {
        log.info("{}-{}-{}", record, retry, trader);
    }
}

package net.maku.subcontrol.trader;

import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.followcom.enums.DirectionEnum;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.followcom.service.FollowOrderHistoryService;
import net.maku.followcom.service.FollowSubscribeOrderService;
import net.maku.followcom.service.FollowTraderSubscribeService;
import net.maku.followcom.service.impl.FollowOrderHistoryServiceImpl;
import net.maku.followcom.service.impl.FollowSubscribeOrderServiceImpl;
import net.maku.followcom.service.impl.FollowTraderSubscribeServiceImpl;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.subcontrol.pojo.CachedCopierOrderInfo;
import net.maku.subcontrol.rule.FollowRule;
import online.mtapi.mt4.Exception.ConnectException;
import online.mtapi.mt4.Exception.InvalidSymbolException;
import online.mtapi.mt4.Exception.TimeoutException;
import online.mtapi.mt4.Op;
import online.mtapi.mt4.QuoteEventArgs;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.time.LocalDateTime;
import java.util.concurrent.ScheduledThreadPoolExecutor;

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

    int whileTimes = 20;

    public AbstractOperation(FollowTraderEntity trader) {
        this.mapKey = trader.getId() + "#" + trader.getAccount();
        redisUtil = SpringContextUtils.getBean(RedisUtil.class);
        leaderCopierService = SpringContextUtils.getBean(FollowTraderSubscribeServiceImpl.class);
        openOrderMappingService = SpringContextUtils.getBean(FollowSubscribeOrderServiceImpl.class);
        historyOrderService = SpringContextUtils.getBean(FollowOrderHistoryServiceImpl.class);
        this.threeStrategyThreadPoolExecutor = ThreadPoolUtils.getScheduledExecute();
        followRule = new FollowRule();
    }

    protected String comment(EaOrderInfo orderInfo) {
        //#喊单者账号(36进制)#喊单者订单号(订单号)#AUTO
        return "#" + Long.toString(Long.parseLong(orderInfo.getAccount()), 36) + "#" + Long.toString(orderInfo.getTicket(), 36) + "#WK_AUTO";
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

    protected double openPrice4(AbstractApiTrader abstract4ApiTrader, String symbol, int type) throws InvalidSymbolException, TimeoutException, ConnectException {
        int loopTimes = 0;
        QuoteEventArgs quoteEventArgs = abstract4ApiTrader.quoteClient.GetQuote(symbol);
        while (quoteEventArgs == null && abstract4ApiTrader.quoteClient.Connected() && loopTimes < whileTimes) {
            try {
                loopTimes++;
                Thread.sleep(50);
            } catch (Exception ignored) {
            }
            abstract4ApiTrader.quoteClient.Subscribe(symbol);
            quoteEventArgs = abstract4ApiTrader.quoteClient.GetQuote(symbol);
        }
        if (quoteEventArgs != null) {
            return type == Buy.getValue() ? quoteEventArgs.Ask : quoteEventArgs.Bid;
        } else {
            return 1.00000;
        }
    }

    public void orderSend(ConsumerRecord<String, Object> record, int retry, FollowTraderEntity trader) {
        EaOrderInfo orderInfo = (EaOrderInfo) record.value();
        LocalDateTime now = LocalDateTime.now();
        int tryTimes = 0;
        boolean update = Boolean.FALSE;
        while (!update) {
            tryTimes++;
//            update = orderChangeService.update(Wrappers.<BchainOrderChange>lambdaUpdate().eq(BchainOrderChange::getTraderId, trader.getId()).eq(BchainOrderChange::getTicket, orderInfo.getTicket()).eq(BchainOrderChange::getChangeType, OrderChangeTypeEnum.NEW.toString()).set(BchainOrderChange::getKafkaReceive, now).set(JeecgEntity::getUpdateTime, LocalDateTime.now()));
            try {
                Thread.sleep(tryTimes * 1000L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (tryTimes > 500) {
                break;
            }
        }
    }

    public void orderClose(ConsumerRecord<String, Object> record, int retry, FollowTraderEntity trader) {
        EaOrderInfo orderInfo = (EaOrderInfo) record.value();
        LocalDateTime now = LocalDateTime.now();
        int tryTimes = 0;
        boolean update = Boolean.FALSE;
        while (!update) {
            tryTimes++;
//            update = orderChangeService.update(Wrappers.<BchainOrderChange>lambdaUpdate().eq(BchainOrderChange::getTraderId, trader.getId()).eq(BchainOrderChange::getTicket, orderInfo.getTicket()).eq(BchainOrderChange::getChangeType, OrderChangeTypeEnum.CLOSED).set(BchainOrderChange::getKafkaReceive, now).set(JeecgEntity::getUpdateTime, LocalDateTime.now()));
            try {
                Thread.sleep(tryTimes * 1000L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (tryTimes > 500) {
                break;
            }
        }
    }

    public void orderModify(ConsumerRecord<String, Object> record, int retry, FollowTraderEntity trader) {
        log.info("{}-{}-{}", record, retry, trader);
    }
}

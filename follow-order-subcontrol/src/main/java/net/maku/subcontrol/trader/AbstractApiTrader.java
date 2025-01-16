package net.maku.subcontrol.trader;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cld.message.pubsub.kafka.IKafkaProducer;
import com.cld.message.pubsub.kafka.impl.CldKafkaConsumer;
import com.cld.message.pubsub.kafka.properties.Ks;
import com.cld.utils.HumpLine;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowBrokeServerEntity;
import net.maku.followcom.entity.FollowSysmbolSpecificationEntity;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.enums.TraderTypeEnum;
import net.maku.followcom.service.FollowSysmbolSpecificationService;
import net.maku.followcom.service.impl.FollowSysmbolSpecificationServiceImpl;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.exception.ServerException;
import net.maku.followcom.entity.FollowPlatformEntity;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.subcontrol.even.OnQuoteTraderHandler;
import net.maku.subcontrol.even.OrderUpdateHandler;
import net.maku.subcontrol.even.CopierOrderUpdateEventHandlerImpl;
import net.maku.subcontrol.even.LeaderOrderUpdateEventHandlerImpl;
import net.maku.subcontrol.pojo.EaSymbolInfo;
import online.mtapi.mt4.*;
import online.mtapi.mt4.Exception.ConnectException;
import online.mtapi.mt4.Exception.InvalidSymbolException;
import online.mtapi.mt4.Exception.TimeoutException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.util.ObjectUtils;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;


@Slf4j
public abstract class AbstractApiTrader extends ApiTrader {
    public  QuoteClient quoteClient;
    @Getter
    @Setter
    protected FollowTraderEntity trader;
    protected ScheduledFuture<?> updateTradeInfoFuture;

    /**
     * 一个账号同时只能处理一个同步持仓订单
     */
    @Getter
    protected Semaphore semaTrade = new Semaphore(1);

    @Getter
    protected Semaphore equitySemaphore = new Semaphore(1);

    /**
     * 同时只能有10个账号进行同步
     */
    @Getter
    public static Semaphore semaCommonTrade = new Semaphore(100);
    protected Semaphore reconnectSemaphore = new Semaphore(1);
    protected int reconnectTimes = 0;
    @Getter
    protected Map<String, EaSymbolInfo> symbolInfoMap = new HashMap<>();
    public OrderClient orderClient;
    public static List<String> availableException4 = new LinkedList<>();
    OnQuoteTraderHandler onQuoteTraderHandler;
    OrderUpdateHandler orderUpdateHandler;
    protected final RedisCache redisCache =SpringContextUtils.getBean(RedisCache.class);;
    static {
        availableException4.add("Market is closed");
        availableException4.add("Invalid volume");
        availableException4.add("Not enough money");
        availableException4.add("Trade is disabled");
    }

    public AbstractApiTrader(FollowTraderEntity trader,  String host, int port) throws IOException {
        quoteClient = new QuoteClient(Integer.parseInt(trader.getAccount()), trader.getPassword(), host, port);
        this.trader = trader;
    }

    public AbstractApiTrader(FollowTraderEntity trader, String host, int port, LocalDateTime closedOrdersFrom, LocalDateTime closedOrdersTo) throws IOException {
        quoteClient = new QuoteClient(Integer.parseInt(trader.getAccount()), trader.getPassword(), host, port, closedOrdersFrom, closedOrdersTo);
        this.trader = trader;
    }

    /**
     * 连接服务器
     */
    public synchronized void connect2Broker() throws Exception {
        this.initPrefixSuffix = Boolean.FALSE;
        this.quoteClient.Connect();
        if (this.quoteClient.OrderClient == null) {
            this.orderClient = new OrderClient(quoteClient);
        }
        boolean isLeader = Objects.equals(trader.getType(), TraderTypeEnum.MASTER_REAL.getType());
        if (this.orderUpdateHandler==null) {
            if (isLeader) {
                //订单变化监听
                this.orderUpdateHandler = new LeaderOrderUpdateEventHandlerImpl(this);
                this.quoteClient.OnOrderUpdate.addListener(orderUpdateHandler);
            }else {
                this.orderUpdateHandler = new CopierOrderUpdateEventHandlerImpl(this);
                this.quoteClient.OnOrderUpdate.addListener(orderUpdateHandler);
            }
        }

        if (this.onQuoteTraderHandler==null){
            //账号监听
            onQuoteTraderHandler=new OnQuoteTraderHandler(this);
            this.quoteClient.OnQuote.addListener(onQuoteTraderHandler);
        }

    }


    public SymbolInfo GetSymbolInfo(String symbol) throws InvalidSymbolException, ConnectException {
        boolean anyMatch;
        try {
            anyMatch = Arrays.stream(quoteClient.Symbols()).anyMatch((s) -> s.equalsIgnoreCase(symbol));
        } catch (TimeoutException e) {
            throw new ConnectException(e, quoteClient.Log);
        }
        if (!anyMatch) {
            throw new InvalidSymbolException(symbol, quoteClient.Log);
        }
        return quoteClient.GetSymbolInfo(symbol);
    }

    /**
     * 重连账号
     *
     * @return true - 重连成功 false - 重连失败
     */
    public boolean reconnect() {
        reconnectTimes++;
        //连续尝试重连10就不重连了;
        if (reconnectTimes > 10) {
            return false;
        }
        boolean result = Boolean.FALSE;
        try {
            reconnectSemaphore.acquire();
        } catch (InterruptedException e) {
            log.error("", e);
            return false;
        }
        FollowTraderEntity trader = this.getTrader();
        long start, end;
        start = System.currentTimeMillis();
        try {
            traderService.update(Wrappers.<FollowTraderEntity>lambdaUpdate().set(FollowTraderEntity::getStatus, CloseOrOpenEnum.OPEN.getValue()).set(FollowTraderEntity::getStatusExtra, "账号掉线").eq(FollowTraderEntity::getId, trader.getId()));
            try {
                log.error("[MT4账号：{}-{}-{}]在{}:{}掉线后立刻开始重连", trader.getId(), trader.getAccount(), trader.getServerName(), this.quoteClient.Host, this.quoteClient.Port);
                AbstractApiTrader.this.connect2Broker();
                result = Boolean.TRUE;
                reconnectTimes = 0;
                end = System.currentTimeMillis();
                log.info("[MT4账号：{}-{}-{}]掉线后在{}:{}立刻重连成功,耗时{}秒", trader.getId(), trader.getAccount(), trader.getServerName(), this.quoteClient.Host, this.quoteClient.Port, (end - start) / 1000.0);
                traderService.update(Wrappers.<FollowTraderEntity>lambdaUpdate().set(FollowTraderEntity::getLoginNode,this.quoteClient.Host+":"+this.quoteClient.Port).set(FollowTraderEntity::getStatus, CloseOrOpenEnum.CLOSE.getValue()).set(FollowTraderEntity::getStatusExtra, "账号在线").eq(FollowTraderEntity::getId, trader.getId()));
            } catch (Exception ex) {
                log.error("", ex);
                traderService.update(Wrappers.<FollowTraderEntity>lambdaUpdate().set(FollowTraderEntity::getStatus, CloseOrOpenEnum.OPEN.getValue()).set(FollowTraderEntity::getStatusExtra, "账号掉线").eq(FollowTraderEntity::getId, trader.getId()));
                log.error("[MT4账号：{}-{}-{}]在{}:{}重连失败", trader.getId(), trader.getAccount(), trader.getServerName(), this.quoteClient.Host, this.quoteClient.Port);
            }
        } catch (Exception ex) {
            result = Boolean.FALSE;
            end = System.currentTimeMillis();
            log.error("[MT4账号：{}-{}-{}]掉线，立刻重连失败:{},耗时{}秒", trader.getId(), trader.getAccount(), trader.getServerName(), ex.getMessage(), (end - start) / 1000.0);
        } finally {
            reconnectSemaphore.release();
        }
        return result;
    }

    public void updateTraderInfo() {
        adjust(trader);
        log.info("更新信息++++++{}",trader.getAccount());
        try {
            boolean connected = quoteClient.Connected();
            if (!connected) {
                log.info("进行重连");
                reconnect();
//                throw new ConnectException("Not Connected.", quoteClient.Log);
            }
            freshTimeWhenConnected();
        } catch (Exception e) {
            log.error("[MT4{}:{}-{}-{}-{}-{}] {}抛出需要重连的异常：{}",  trader.getType().equals(TraderTypeEnum.MASTER_REAL.getType()) ? "喊单者" : "跟单者", trader.getId(), trader.getAccount(), trader.getServerName(),trader.getPlatform(), trader.getPassword(), eurusd, e.getMessage());
            traderService.update(Wrappers.<FollowTraderEntity>lambdaUpdate().set(FollowTraderEntity::getStatus, CloseOrOpenEnum.OPEN.getValue()).set(FollowTraderEntity::getStatusExtra, "账号掉线").eq(FollowTraderEntity::getId, trader.getId()));
            String serverNode;
            //优先查看平台默认节点
            if (ObjectUtil.isNotEmpty(redisCache.hGet(Constant.VPS_NODE_SPEED+trader.getServerId(),trader.getPlatform()))){
                serverNode=(String)redisCache.hGet(Constant.VPS_NODE_SPEED+trader.getServerId(),trader.getPlatform());
            }else {
                FollowPlatformEntity followPlatformServiceOne = followPlatformService.getOne(new LambdaQueryWrapper<FollowPlatformEntity>().eq(FollowPlatformEntity::getServer, trader.getPlatform()));
                serverNode=followPlatformServiceOne.getServerNode();
            }
            if (ObjectUtil.isNotEmpty(serverNode)){
                try {
                    //处理节点格式
                    String[] split = serverNode.split(":");
                    QuoteClient quoteClient = new QuoteClient(Integer.parseInt(trader.getAccount()), trader.getPassword(),  split[0], Integer.valueOf(split[1]));
                    quoteClient.Connect();
                }catch (Exception e1){
                    if (e.getMessage().contains("Invalid account")){
                        log.info("账号密码错误");
                        throw new ServerException("账号密码错误");
                    }else {
                        //重连失败
                        log.info("重连失败{}",trader.getId());
                    }
                }
            }else {
                List<FollowBrokeServerEntity> serverEntityList = followBrokeServerService.listByServerName(trader.getPlatform());
                if (ObjectUtil.isEmpty(serverEntityList)) {
                    log.error("server is null");
                    return;
                }
                serverEntityList.stream().anyMatch(address->{
                    try {
                        this.quoteClient.Disconnect();
                        this.quoteClient.Password = trader.getPassword();
                        this.quoteClient.Host = address.getServerNode();
                        this.quoteClient.Port = Integer.valueOf(address.getServerPort());
                    } catch (Exception ex) {
                        log.error("连接失败", ex);
                    }
                    return reconnect();
                });
            }
        }
    }

    void freshTimeWhenConnected() throws ConnectException, TimeoutException {
        LambdaUpdateWrapper<FollowTraderEntity> lambdaUpdateWrapper = Wrappers.<FollowTraderEntity>lambdaUpdate().set(FollowTraderEntity::getLeverage, quoteClient.AccountLeverage())
                .set(FollowTraderEntity::getBalance, quoteClient.AccountBalance()).set(FollowTraderEntity::getIsDemo, quoteClient.IsDemoAccount())
                .set(FollowTraderEntity::getEuqit,quoteClient.AccountEquity())
                .set(FollowTraderEntity::getStatus,  CloseOrOpenEnum.CLOSE.getValue())
                .set(FollowTraderEntity::getStatusExtra, "账号在线").eq(FollowTraderEntity::getId, trader.getId());
        //  此处加上try的原因是，某些经济商不支持读取服务器时间的获取。或者市场关闭(ERR_MARKET_CLOSED 132)。获取失败后会抛出异常。
        try {
            lambdaUpdateWrapper.set(FollowTraderEntity::getDiff, quoteClient.ServerTimeZone() / 60);
        } catch (Exception exception) {
            log.error("读取Server时间失败", exception);
        } finally {
            traderService.update(lambdaUpdateWrapper);
        }
    }

    protected void adjust(FollowTraderEntity trader) {
        int loopTimes = 0;
        int largestTimes = 120;
        long initialDelay = Long.parseLong(trader.getAccount()) % 60;
        while (loopTimes < largestTimes) {
            ++loopTimes;
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime next = LocalDateTime.of(now.getYear(), now.getMonthValue(), now.getDayOfMonth(), now.getHour(), now.getMinute(), (int) initialDelay, now.getNano());
            long between = Math.abs(Duration.between(now, next).toMillis() / 1000);
            if (between < 2) {
                break;
            } else {
                try {
                    Thread.sleep(900);
                } catch (Exception e) {
                    log.error("",e);
                }
            }
        }
    }

    /**
     * 停止交易
     *
     * @return Boolean
     */
    public Boolean stopTrade() {
        if (orderUpdateHandler != null) {
            try {
                orderUpdateHandler.setRunning(Boolean.FALSE);
            } catch (Exception e) {
                log.error("设置orderUpdateHandler running 为false", e);
            }
        }
        if (onQuoteTraderHandler != null) {
            try {
                onQuoteTraderHandler.setRunning(Boolean.FALSE);
            } catch (Exception e) {
                log.error("onQuoteTraderHandler running 为false", e);
            }
        }
        // 跟单者对象关闭定时任务
        if (!ObjectUtils.isEmpty(updateTradeInfoFuture)) {
            try {
                updateTradeInfoFuture.cancel(Boolean.TRUE);
            } catch (Exception e) {
                log.error("updateTradeInfoFuture cancel", e);
            }
        }

        try {
            quoteClient.OnOrderUpdate.removeAllListeners();
            quoteClient.OnConnect.removeAllListeners();
            quoteClient.OnDisconnect.removeAllListeners();
            quoteClient.OnQuoteHistory.removeAllListeners();
            quoteClient.OnDisconnect.removeAllListeners();
            quoteClient.OnQuote.removeAllListeners();
            quoteClient.Disconnect();
            // 关闭线程池
            scheduledExecutorService.shutdown();
            try {
                if (!scheduledExecutorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduledExecutorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduledExecutorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("关闭mtapi");
        } catch (Exception e) {
            log.error("停止失败", e);
        }
        return true;
    }

    /**
     * 获取MT4品种的信息
     *
     * @param symbol 品种
     * @param cache  是否从缓存中获取
     * @return EaSymbolInfo
     */
    public EaSymbolInfo symbolInfo(String symbol, boolean cache) throws InvalidSymbolException, ConnectException, TimeoutException {
        EaSymbolInfo eaSymbolInfo;
        if (cache) {
            eaSymbolInfo = symbolInfoMap.get(symbol);
            if (ObjectUtils.isEmpty(eaSymbolInfo)) {
                eaSymbolInfo = symbolInfo(symbol);
            }
        } else {
            eaSymbolInfo = symbolInfo(symbol);
            symbolInfoMap.put(symbol, eaSymbolInfo);
        }
        return eaSymbolInfo;
    }

    private EaSymbolInfo symbolInfo(String symbol) throws InvalidSymbolException, ConnectException, TimeoutException {
        EaSymbolInfo eaSymbolInfo;
        boolean anyMatch = Arrays.stream(this.quoteClient.Symbols()).anyMatch((s) -> s.equalsIgnoreCase(symbol));
        if (!anyMatch) {
            throw new InvalidSymbolException(symbol, this.quoteClient.Log);
        }
        SymbolInfo symbolInfo = this.GetSymbolInfo(symbol);
        ConGroupSec conGroupSec = this.quoteClient.GetSymbolGroupParams(symbol);
        eaSymbolInfo = new EaSymbolInfo(symbolInfo, conGroupSec);
        eaSymbolInfo.setTradeTickSize(this.quoteClient.GetTickSize(symbol));
        eaSymbolInfo.setTradeTickValue(this.quoteClient.GetTickValue(symbol, 10000));
        return eaSymbolInfo;
    }

}

package net.maku.mascontrol.trader;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowBrokeServerEntity;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.enums.TraderTypeEnum;
import net.maku.framework.common.exception.ServerException;
import net.maku.mascontrol.entity.FollowPlatformEntity;
import net.maku.mascontrol.even.OnQuoteHandler;
import net.maku.mascontrol.even.OnQuoteTraderHandler;
import net.maku.mascontrol.service.FollowPlatformService;
import online.mtapi.mt4.*;
import online.mtapi.mt4.Exception.ConnectException;
import online.mtapi.mt4.Exception.InvalidSymbolException;
import online.mtapi.mt4.Exception.TimeoutException;
import org.springframework.util.ObjectUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.stream.Collectors;


@Slf4j
public abstract class AbstractApiTrader extends ApiTrader {

    private final Object lock = new Object();
    public QuoteClient quoteClient;
    @Getter
    @Setter
    protected FollowTraderEntity trader;
    protected ScheduledFuture<?> updateTradeInfoFuture;
    protected ScheduledFuture<?> cycleFuture;
//    @Setter
//    @Getter
//    protected IEquityRiskListener equityRiskListener;

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
    private QuoteEventArgs quoteEventArgs;
    @Getter
    public OrderClient orderClient;
    public static List<String> availableException4 = new LinkedList<>();
    protected Map<LocalDate, BigDecimal> equityMap = new HashMap<>(1);
    private Date apiAnalysisBeginDate = null;
    OnQuoteTraderHandler onQuoteHandler;

    static {
        availableException4.add("Market is closed");
        availableException4.add("Invalid volume");
        availableException4.add("Not enough money");
        availableException4.add("Trade is disabled");
    }

    public AbstractApiTrader(FollowTraderEntity trader, String host, int port) throws IOException {
        quoteClient = new QuoteClient(Integer.parseInt(trader.getAccount()), trader.getPassword(), host, port);
        this.trader = trader;
//        this.equityRiskListener = new EquityRiskListenerImpl();
        initService();
    }

    public AbstractApiTrader(FollowTraderEntity trader, String host, int port, LocalDateTime closedOrdersFrom, LocalDateTime closedOrdersTo) throws IOException {
        quoteClient = new QuoteClient(Integer.parseInt(trader.getAccount()), trader.getPassword(), host, port, closedOrdersFrom, closedOrdersTo);
//        this.equityRiskListener = new EquityRiskListenerImpl4();
        this.trader = trader;
        initService();
    }

    /**
     * 连接服务器
     */
    protected void connect2Broker() throws Exception {
        this.initPrefixSuffix = Boolean.FALSE;
        this.quoteClient.Connect();
        if (quoteClient.OrderClient == null) {
            this.orderClient = new OrderClient(quoteClient);
        }
        if (this.onQuoteHandler==null){
            //订单监听
            this.quoteClient.OnQuote.addListener(new OnQuoteTraderHandler(this));
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
                traderService.update(Wrappers.<FollowTraderEntity>lambdaUpdate().set(FollowTraderEntity::getStatus, CloseOrOpenEnum.CLOSE.getValue()).set(FollowTraderEntity::getStatusExtra, "账号在线").eq(FollowTraderEntity::getId, trader.getId()));
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

    public void updatePrefixSuffix() throws TimeoutException, ConnectException {
        if (!initPrefixSuffix) {
            prefixSuffixList.clear();
            // initPrefixSuffix变量的作用是：如果已经初始化则不需要继续判断，解决周末货币品种不可交易的情况；
            // 即周末(未开市)计算不出合理的前后缀，等到周一(开始后)计算成功后就不需要再反复计算了。

            //1-过滤出包含EURUSD（不区分大小写）的品种名
            List<String> eurusds = Arrays.stream(this.quoteClient.Symbols()).filter(symbol -> StrUtil.containsAnyIgnoreCase(symbol, EURUSD)).sorted(Comparator.comparingInt(String::length)).collect(Collectors.toList());

            //2-遍历包含了EURUSD的所有货币对
            for (String symbol : eurusds) {
                //2.1-判断该货币对是否是正常可交易的
                try {
                    //通过TRADEALLOWED来判断该品种是否可交易
                    if (!tradeAllowed(symbol)) {
                        continue;
                    } else {
                        eurusd = symbol;
                        this.quoteClient.Subscribe(eurusd);
                        //成功初始化
                        initPrefixSuffix = Boolean.TRUE;
                    }
                } catch (Exception e) {
                    log.error("", e);
                }

                //2.2-不区分大小写找出EURUSD的准确位置，之前的就是前缀，之后的就是后缀
                Matcher matcher = pattern.matcher(symbol);
                while (matcher.find()) {
                    String p = symbol.substring(0, matcher.start());
                    String s = symbol.substring(matcher.end());
                    String psItem = (ObjectUtils.isEmpty(p) ? EMPTY : p) + StrUtil.COMMA + (ObjectUtils.isEmpty(s) ? EMPTY : s);
                    //前后缀配对的方式存储
                    if (!prefixSuffixList.contains(psItem)) {
                        prefixSuffixList.add((ObjectUtils.isEmpty(p) ? EMPTY : p) + StrUtil.COMMA + (ObjectUtils.isEmpty(s) ? EMPTY : s));
                    }
                }
            }
            FollowTraderEntity build = new FollowTraderEntity();
            build.setId(trader.getId());
            traderService.updateById(build);
        } else {
            log.info(" {}'s initPrefixSuffix is {}", trader.getAccount(), initPrefixSuffix);
        }
    }

    /**
     * 判断品种是否可以交易
     *
     * @param symbol 交易品种
     * @return true 可以交易 false 不可以交易
     */
    public boolean tradeAllowed(String symbol) {
        SymbolInfoEx symbolInfoEx;
        try {
            symbolInfoEx = this.GetSymbolInfo(symbol).Ex;
            switch (symbolInfoEx.trade) {
                case 0:
                    log.debug("DISABLED");
                    break;
                case 1:
                    log.debug("CLOSE_ONLY");
                    break;
                case 2:
                    log.debug("FULL_ACCESS");
                    return Boolean.TRUE;
                case 3:
                    log.debug("LONG_ONLY");
                    break;
                case 4:
                    log.debug("SHORT_ONLY");
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + symbolInfoEx.trade);
            }
        } catch (InvalidSymbolException | ConnectException e) {
            return Boolean.FALSE;
        }


        return Boolean.FALSE;
    }

    public boolean tradeSession(String symbol) {
        SymbolInfoEx symbolInfoEx;
        try {
            symbolInfoEx = this.GetSymbolInfo(symbol).Ex;
        } catch (InvalidSymbolException | ConnectException e) {
            return Boolean.FALSE;
        }
        LocalDateTime localDateTime = this.quoteClient.ServerTime();
        long minutes = Duration.between(localDateTime.toLocalDate().atStartOfDay(), localDateTime).toMinutes();

        if (!ObjectUtils.isEmpty(symbolInfoEx)) {
            if (symbolInfoEx.trade == 2) {
                ConSessions session = symbolInfoEx.sessions[this.quoteClient.ServerTime().getDayOfWeek().getValue() % 7];
                for (ConSession conSession : session.trade) {
                    if (minutes >= conSession.open && minutes < conSession.close) {
                        return Boolean.TRUE;
                    }
                }
            }
        }
        return Boolean.FALSE;
    }


    public void updateTraderInfo() {
        adjust(trader);
        log.info("更新信息++++++{}",trader.getAccount());
        int loopTimes = 0;
        try {
            boolean connected = quoteClient.Connected();
            if (!connected) {
                log.info("进行重连");
                throw new ConnectException("Not Connected.", quoteClient.Log);
            }
            freshTimeWhenConnected();
        } catch (Exception e) {
            log.error("[MT4{}:{}-{}-{}-{}-{}] {}抛出需要重连的异常：{}",  trader.getType().equals(TraderTypeEnum.MASTER_REAL.getType()) ? "喊单者" : "跟单者", trader.getId(), trader.getAccount(), trader.getServerName(),trader.getPlatform(), trader.getPassword(), eurusd, e.getMessage());
            traderService.update(Wrappers.<FollowTraderEntity>lambdaUpdate().set(FollowTraderEntity::getStatus, CloseOrOpenEnum.OPEN.getValue()).set(FollowTraderEntity::getStatusExtra, "账号掉线").eq(FollowTraderEntity::getId, trader.getId()));
            FollowPlatformEntity followPlatformServiceOne = followPlatformService.getOne(new LambdaQueryWrapper<FollowPlatformEntity>().eq(FollowPlatformEntity::getServer, trader.getPlatform()));
            if (ObjectUtil.isNotEmpty(followPlatformServiceOne.getServerNode())){
                try {
                    //处理节点格式
                    String[] split = followPlatformServiceOne.getServerNode().split(":");
                    QuoteClient quoteClient = new QuoteClient(Integer.parseInt(trader.getAccount()), trader.getPassword(),  split[0], Integer.valueOf(split[1]));
                    quoteClient.Connect();
                }catch (Exception e1){
                    if (e.getMessage().contains("Invalid account")){
                        log.info("账号密码错误");
                        throw new ServerException("账号密码错误");
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
//        Date apiAnalysisEndDate = new Date();
//        long between = apiAnalysisBeginDate == null ? 100 : DateUtil.between(apiAnalysisBeginDate, apiAnalysisEndDate, DateUnit.MINUTE, true);
//        if (between > 5) {
//            apiAnalysisBeginDate = new Date();
////            analysisThreeStrategyThreadPoolExecutor.submit(new ApiAnalysisCallable(analysisThreeStrategyThreadPoolExecutor, trader, this, new SynInfo(trader.getId().toString(), false)));
//        }

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
//    /**
//     * 判断当前账户净值是否小于设置的最小风控净值，
//     * <p>
//     * 1-如果是则进行风控干预
//     * 2-如果不是则不干预
//     */
//    public void riskControl(FollowTraderEntity traderFromDb) {
//        BigDecimal riskEquity = traderFromDb == null ? BigDecimal.ZERO : traderFromDb.getMinEquity();
//        if (riskEquity != null && riskEquity.compareTo(BigDecimal.ZERO) != 0) {
//            equityRiskListener.onTriggered(this, riskEquity);
//            this.updateTraderInfo();
//        }
//    }
}

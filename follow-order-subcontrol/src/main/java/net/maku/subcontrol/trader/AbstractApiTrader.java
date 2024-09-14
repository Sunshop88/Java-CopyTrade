//package net.maku.subcontrol.trader;
//
//import cn.hutool.core.date.DateUnit;
//import cn.hutool.core.date.DateUtil;
//import cn.hutool.core.util.StrUtil;
//import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
//import com.baomidou.mybatisplus.core.toolkit.Wrappers;
//import com.cld.message.pubsub.kafka.IKafkaProducer;
//import com.cld.message.pubsub.kafka.impl.CldKafkaConsumer;
//import com.cld.message.pubsub.kafka.properties.Ks;
//import com.cld.utils.HumpLine;
//import lombok.Getter;
//import lombok.Setter;
//import lombok.extern.slf4j.Slf4j;
//import net.maku.followcom.entity.FollowTraderEntity;
//import online.mtapi.mt4.*;
//import online.mtapi.mt4.Exception.ConnectException;
//import online.mtapi.mt4.Exception.InvalidSymbolException;
//import online.mtapi.mt4.Exception.TimeoutException;
//import org.jeecg.common.util.SpringContextUtils;
//import org.jeecg.modules.blockchain.enums.TraderTypeEnum;
//import org.jeecg.modules.copytrade.entity.AotfxServer;
//import org.jeecg.modules.copytrade.entity.AotfxTrader;
//import org.jeecg.modules.copytrade.manager.ApiAnalysisCallable;
//import org.jeecg.modules.copytrade.manager.CommissionCallable;
//import org.jeecg.modules.copytrade.manager.ServersDatIniUtil;
//import org.jeecg.modules.copytrade.manager.task.SyncLiveOrdersTask;
//import org.jeecg.modules.copytrade.manager.task.check.SynInfo;
//import org.jeecg.modules.copytrade.manager.trade.copier.mtapi.mt4.impl.Copier4OrderUpdateEventHandlerImpl;
//import org.jeecg.modules.copytrade.manager.trade.copier.mtapi.mt4.task.CycleCloseOrderTask;
//import org.jeecg.modules.copytrade.manager.trade.folllow.rule.EaSymbolInfo;
//import org.jeecg.modules.copytrade.manager.trade.leader.mtapi.OrderUpdateHandler;
//import org.jeecg.modules.copytrade.manager.trade.leader.mtapi.mt4.impl.Leader4OrderUpdateEventHandlerImpl;
//import org.jeecg.modules.copytrade.vo.OffLineTypeVO;
//import org.springframework.util.ObjectUtils;
//
//import java.io.IOException;
//import java.math.BigDecimal;
//import java.time.Duration;
//import java.time.LocalDate;
//import java.time.LocalDateTime;
//import java.util.*;
//import java.util.concurrent.ScheduledFuture;
//import java.util.concurrent.Semaphore;
//import java.util.regex.Matcher;
//import java.util.stream.Collectors;
//
//
//
//@Slf4j
//public abstract class AbstractApiTrader extends ApiTrader {
//
//    private final Object lock = new Object();
//    public QuoteClient quoteClient;
//    @Getter
//    @Setter
//    protected FollowTraderEntity trader;
//    protected ScheduledFuture<?> updateTradeInfoFuture;
//    protected ScheduledFuture<?> cycleFuture;
//    @Getter
//    private volatile SyncLiveOrdersTask syncLiveOrdersTask = null;
//
//    /**
//     * 一个账号同时只能处理一个同步持仓订单
//     */
//    @Getter
//    protected Semaphore semaTrade = new Semaphore(1);
//
//    @Getter
//    protected Semaphore equitySemaphore = new Semaphore(1);
//
//    /**
//     * 同时只能有10个账号进行同步
//     */
//    @Getter
//    public static Semaphore semaCommonTrade = new Semaphore(100);
//    protected Semaphore reconnectSemaphore = new Semaphore(1);
//    protected int reconnectTimes = 0;
//    private QuoteEventArgs quoteEventArgs;
//    @Getter
//    protected Map<String, EaSymbolInfo> symbolInfoMap = new HashMap<>();
//    //    public OrderClientSafeCmnt orderClientSafeCmnt;
////    public OrderClientSafe orderClientSafe;
//    public OrderClient orderClient;
//    public static List<String> availableException4 = new LinkedList<>();
//    protected CycleCloseOrderTask cycleCloseOrderTask;
//    protected Map<LocalDate, BigDecimal> equityMap = new HashMap<>(1);
//
//    private Date commissionBeginDate = null;
//
//    private Date apiAnalysisBeginDate = null;
//
//    OrderUpdateHandler orderUpdateHandler;
//
//    static {
//        availableException4.add("Market is closed");
//        availableException4.add("Invalid volume");
//        availableException4.add("Not enough money");
//        availableException4.add("Trade is disabled");
//    }
//
//    public AbstractApiTrader(AotfxTrader trader4, IKafkaProducer<String, Object> kafkaProducer, String host, int port) throws IOException {
//        quoteClient = new QuoteClient(Integer.parseInt(trader4.getAccount()), trader4.getPassword(), host, port);
//        this.trader4 = trader4;
//        this.kafkaProducer = kafkaProducer;
//        this.equityRiskListener = new EquityRiskListenerImpl4();
//        initService();
//        this.cldKafkaConsumer = new CldKafkaConsumer<>(CldKafkaConsumer.defaultProperties((Ks) SpringContextUtils.getBean(HumpLine.pascalToHump(Ks.class.getSimpleName())), this.trader4.getId()));
//    }
//
//    public AbstractApiTrader(AotfxTrader trader4, IKafkaProducer<String, Object> kafkaProducer, String host, int port, LocalDateTime closedOrdersFrom, LocalDateTime closedOrdersTo) throws IOException {
//        quoteClient = new QuoteClient(Integer.parseInt(trader4.getAccount()), trader4.getPassword(), host, port, closedOrdersFrom, closedOrdersTo);
//        this.equityRiskListener = new EquityRiskListenerImpl4();
//        this.trader4 = trader4;
//        this.kafkaProducer = kafkaProducer;
//        initService();
//        this.cldKafkaConsumer = new CldKafkaConsumer<>(CldKafkaConsumer.defaultProperties((Ks) SpringContextUtils.getBean(HumpLine.pascalToHump(Ks.class.getSimpleName())), this.trader4.getId()));
//    }
//
//    /**
//     * 连接服务器
//     */
//    protected void connect2Broker() throws Exception {
//        this.initPrefixSuffix = Boolean.FALSE;
//        this.quoteClient.Connect();
////        this.quoteClient.Connect(1);
//        if (this.orderUpdateHandler == null) {
//            boolean isLeader = Objects.equals(trader4.getType(), TraderTypeEnum.MASTER_REAL.getValue());
//            this.orderUpdateHandler = isLeader ? new Leader4OrderUpdateEventHandlerImpl(this, kafkaProducer) : new Copier4OrderUpdateEventHandlerImpl(this, kafkaProducer);
//            this.quoteClient.OnOrderUpdate.addListener(orderUpdateHandler);
//        }
//        if (quoteClient.OrderClient == null) {
//            this.orderClient = new OrderClient(quoteClient);
////        this.orderClientSafeCmnt = new OrderClientSafeCmnt(this.orderClient);
////        this.orderClientSafe = new OrderClientSafe(this.orderClient);
//        }
//        //账号密码绑定成功后，立刻刷新前后缀,账户数据
//        updatePrefixSuffix();
//    }
//
//    public SymbolInfo GetSymbolInfo(String symbol) throws InvalidSymbolException, ConnectException {
//        boolean anyMatch;
//        try {
//            anyMatch = Arrays.stream(quoteClient.Symbols()).anyMatch((s) -> s.equalsIgnoreCase(symbol));
//        } catch (TimeoutException e) {
//            throw new ConnectException(e, quoteClient.Log);
//        }
//        if (!anyMatch) {
//            throw new InvalidSymbolException(symbol, quoteClient.Log);
//        }
//        return quoteClient.GetSymbolInfo(symbol);
//    }
//
//    /**
//     * 重连账号
//     *
//     * @return true - 重连成功 false - 重连失败
//     */
//    public boolean reconnect() {
//        reconnectTimes++;
//        //连续尝试重连10就不重连了;
//        if (reconnectTimes > 20) {
//            return false;
//        }
//        boolean result = Boolean.FALSE;
//        try {
//            reconnectSemaphore.acquire();
//        } catch (InterruptedException e) {
//            log.error("", e);
//            return false;
//        }
//        AotfxTrader trader4 = this.getTrader4();
//        long start, end;
//        start = System.currentTimeMillis();
//        try {
//            traderService.update(Wrappers.<AotfxTrader>lambdaUpdate().set(AotfxTrader::getLatestUnconnect, LocalDateTime.now()).set(AotfxTrader::getConnected, Boolean.FALSE).set(AotfxTrader::getStatusExtra, "账号掉线").eq(AotfxTrader::getId, trader4.getId()));
//            try {
//                log.error("[MT4账号：{}-{}-{}]在{}:{}掉线后立刻开始重连", trader4.getId(), trader4.getAccount(), trader4.getServerName(), this.quoteClient.Host, this.quoteClient.Port);
//                Abstract4ApiTrader.this.connect2Broker();
//                result = Boolean.TRUE;
//                reconnectTimes = 0;
//                end = System.currentTimeMillis();
//                log.info("[MT4账号：{}-{}-{}]掉线后在{}:{}立刻重连成功,耗时{}秒", trader4.getId(), trader4.getAccount(), trader4.getServerName(), this.quoteClient.Host, this.quoteClient.Port, (end - start) / 1000.0);
//                traderService.update(Wrappers.<AotfxTrader>lambdaUpdate().set(AotfxTrader::getLatestConnect, LocalDateTime.now()).set(AotfxTrader::getConnected, Boolean.TRUE).set(AotfxTrader::getStatusExtra, "账号在线").eq(AotfxTrader::getId, trader4.getId()));
//            } catch (Exception ex) {
//                log.error("", ex);
//                traderService.update(Wrappers.<AotfxTrader>lambdaUpdate().set(AotfxTrader::getLatestUnconnect, LocalDateTime.now()).set(AotfxTrader::getConnected, Boolean.FALSE).set(AotfxTrader::getStatusExtra, "账号掉线").eq(AotfxTrader::getId, trader4.getId()));
//                log.error("[MT4账号：{}-{}-{}]在{}:{}重连失败", trader4.getId(), trader4.getAccount(), trader4.getServerName(), this.quoteClient.Host, this.quoteClient.Port);
//            }
//        } catch (Exception ex) {
//            result = Boolean.FALSE;
//            end = System.currentTimeMillis();
//            log.error("[MT4账号：{}-{}-{}]掉线，立刻重连失败:{},耗时{}秒", trader4.getId(), trader4.getAccount(), trader4.getServerName(), ex.getMessage(), (end - start) / 1000.0);
//        } finally {
//            reconnectSemaphore.release();
//        }
//        return result;
//    }
//
//    public void updatePrefixSuffix() throws TimeoutException, ConnectException {
//        if (!initPrefixSuffix) {
//            prefixSuffixList.clear();
//            // initPrefixSuffix变量的作用是：如果已经初始化则不需要继续判断，解决周末货币品种不可交易的情况；
//            // 即周末(未开市)计算不出合理的前后缀，等到周一(开始后)计算成功后就不需要再反复计算了。
//
//            //1-过滤出包含EURUSD（不区分大小写）的品种名
//            List<String> eurusds = Arrays.stream(this.quoteClient.Symbols()).filter(symbol -> StrUtil.containsAnyIgnoreCase(symbol, EURUSD)).sorted(Comparator.comparingInt(String::length)).collect(Collectors.toList());
//
//            //2-遍历包含了EURUSD的所有货币对
//            for (String symbol : eurusds) {
//                //2.1-判断该货币对是否是正常可交易的
//                try {
//                    //通过TRADEALLOWED来判断该品种是否可交易
//                    if (!tradeAllowed(symbol)) {
//                        continue;
//                    } else {
//                        eurusd = symbol;
//                        this.quoteClient.Subscribe(eurusd);
//                        //成功初始化
//                        initPrefixSuffix = Boolean.TRUE;
//                    }
//                } catch (Exception e) {
//                    log.error("", e);
//                }
//
//                //2.2-不区分大小写找出EURUSD的准确位置，之前的就是前缀，之后的就是后缀
//                Matcher matcher = pattern.matcher(symbol);
//                while (matcher.find()) {
//                    String p = symbol.substring(0, matcher.start());
//                    String s = symbol.substring(matcher.end());
//                    String psItem = (ObjectUtils.isEmpty(p) ? EMPTY : p) + StrUtil.COMMA + (ObjectUtils.isEmpty(s) ? EMPTY : s);
//                    //前后缀配对的方式存储
//                    if (!prefixSuffixList.contains(psItem)) {
//                        prefixSuffixList.add((ObjectUtils.isEmpty(p) ? EMPTY : p) + StrUtil.COMMA + (ObjectUtils.isEmpty(s) ? EMPTY : s));
//                    }
//                }
//            }
//            AotfxTrader build = new AotfxTrader();
//            build.setId(trader4.getId());
//            build.setPrefixSuffix(prefixSuffixList.isEmpty() ? EMPTY + StrUtil.COMMA + EMPTY : String.join("@", prefixSuffixList));
//            prefixSuffix = build.getPrefixSuffix();
//            traderService.updateById(build);
//
//            // 调整强制映射数据
//            AotfxTrader eaTrader = traderService.getById(this.trader4.getId());
//            if (!ObjectUtils.isEmpty(eaTrader)) {
//                List<String> oriSymbolList = Arrays.stream(this.quoteClient.Symbols()).collect(Collectors.toList());
//                updateModSymbolMapping(getPrefixSuffixList(), oriSymbolList);
//            }
//        } else {
//            log.info(" {}'s initPrefixSuffix is {}", trader4.getAccount(), initPrefixSuffix);
//        }
//    }
//
//    /**
//     * 判断品种是否可以交易
//     *
//     * @param symbol 交易品种
//     * @return true 可以交易 false 不可以交易
//     */
//    public boolean tradeAllowed(String symbol) {
//        SymbolInfoEx symbolInfoEx;
//        try {
//            symbolInfoEx = this.GetSymbolInfo(symbol).Ex;
//            switch (symbolInfoEx.trade) {
//                case 0:
//                    log.debug("DISABLED");
//                    break;
//                case 1:
//                    log.debug("CLOSE_ONLY");
//                    break;
//                case 2:
//                    log.debug("FULL_ACCESS");
//                    return Boolean.TRUE;
//                case 3:
//                    log.debug("LONG_ONLY");
//                    break;
//                case 4:
//                    log.debug("SHORT_ONLY");
//                    break;
//                default:
//                    throw new IllegalStateException("Unexpected value: " + symbolInfoEx.trade);
//            }
//        } catch (InvalidSymbolException | ConnectException e) {
//            return Boolean.FALSE;
//        }
//
//
//        return Boolean.FALSE;
//    }
//
//    public boolean tradeSession(String symbol) {
//        SymbolInfoEx symbolInfoEx;
//        try {
//            symbolInfoEx = this.GetSymbolInfo(symbol).Ex;
//        } catch (InvalidSymbolException | ConnectException e) {
//            return Boolean.FALSE;
//        }
//        LocalDateTime localDateTime = this.quoteClient.ServerTime();
//        long minutes = Duration.between(localDateTime.toLocalDate().atStartOfDay(), localDateTime).toMinutes();
//
//        if (!ObjectUtils.isEmpty(symbolInfoEx)) {
//            if (symbolInfoEx.trade == 2) {
//                ConSessions session = symbolInfoEx.sessions[this.quoteClient.ServerTime().getDayOfWeek().getValue() % 7];
//                for (ConSession conSession : session.trade) {
//                    if (minutes >= conSession.open && minutes < conSession.close) {
//                        return Boolean.TRUE;
//                    }
//                }
//            }
//        }
//        return Boolean.FALSE;
//    }
//
//
//    public void updateTraderInfo() {
//        if (trader4.getType().contains("MASTER")) {
//            List<String> bs = Arrays.asList("tmgm_v2_schema", "jiarui_v2_schema", "gendan99_v2_schema", "mwt_v2_schema", "zhiying_v2_schema", "homefx_v2_schema", "followin_v2_schema", "tongshenglian_v2_schema", "rarlon_v2_schema", "bs");
//            for (String schema : bs) {
//                try {
//                    OffLineTypeVO offLineTypeVO = traderService.offLine(schema);
//                    log.info(String.valueOf(offLineTypeVO));
//                } catch (Exception e) {
//                    log.error("", e);
//                }
//            }
//
//            List<String> huichuang_v2_schema = Arrays.asList("huichuang_v2_schema");
//            for (String schema : huichuang_v2_schema) {
//                try {
//                    OffLineTypeVO offLineTypeVO = traderService.offLine2(schema);
//                    log.info(String.valueOf(offLineTypeVO));
//                } catch (Exception e) {
//                    log.error("", e);
//                }
//            }
//
//            List<String> itrader_v2_schema = Arrays.asList("hehuoren_v2_schema", "itrader_v2_schema", "yingsheng_v2_schema", "huiju_v2_schema", "fenxingzhe_v2_schema", "jinniu_v2_schema", "niuren_v2_schema", "dix_v2_schema");
//            for (String schema : itrader_v2_schema) {
//                try {
//                    OffLineTypeVO offLineTypeVO = traderService.offLine3(schema);
//                    log.info(String.valueOf(offLineTypeVO));
//                } catch (Exception e) {
//                    log.error("", e);
//                }
//            }
//
//        }
//        adjust(trader4);
//        int loopTimes = 0;
//        try {
//            boolean connected = quoteClient.Connected();
//            if (!connected) {
//                throw new ConnectException("Not Connected.", quoteClient.Log);
//            } else {
//                this.quoteClient.Subscribe(eurusd);
//                while (quoteEventArgs == null && quoteClient.Connected()) {
//                    quoteEventArgs = this.quoteClient.GetQuote(eurusd);
//                    if (++loopTimes > 20) {
//                        break;
//                    } else {
//                        Thread.sleep(500);
//                    }
//                }
//                try {
//                    if (trader4.getType().equalsIgnoreCase(TraderTypeEnum.MASTER_REAL.getValue()) && quoteClient.AccountMode() != 1) {
//                        this.orderClient.OrderSend(eurusd, Op.Buy, 0.0, quoteEventArgs == null ? 1.0 : quoteEventArgs.Ask, Integer.MAX_VALUE, 0, 0, "daW7iAEt", 0, null);
//                    }
//                } catch (Exception serverException) {
//                    if (availableException4.contains(serverException.getMessage())) {
//                        log.debug("[MT4{}:{}-{}-{}]抛出不需要重连的异常：{}", trader4.getType().contains("MASTER") ? "喊单者" : "跟单者", trader4.getId(), trader4.getAccount(), trader4.getServerName(), serverException.getMessage());
//                    } else {
//                        throw serverException;
//                    }
//                }
//            }
//            freshTimeWhenConnected();
//        } catch (Exception e) {
//            log.error("[MT4{}:{}-{}-{}-{}] {}抛出需要重连的异常：{}", trader4.getType().contains("MASTER") ? "喊单者" : "跟单者", trader4.getId(), trader4.getAccount(), trader4.getServerName(), trader4.getPassword(), eurusd, e.getMessage());
//            //如果出现了掉线状况，直接就返回；执行后续逻辑无任何意义；
//            traderService.update(Wrappers.<AotfxTrader>lambdaUpdate().set(AotfxTrader::getConnected, Boolean.FALSE).set(AotfxTrader::getLatestUnconnect, LocalDateTime.now()).set(AotfxTrader::getStatusExtra, "账号掉线").eq(AotfxTrader::getId, trader4.getId()));
//            AotfxServer server = serverService.getById(trader4.getServerId());
//            if (server == null) {
//                log.error("server is null");
//                return;
//            }
//            List<ServersDatIniUtil.Address> addressList = ServersDatIniUtil.mt4ServersWithIni(server);
//            if (addressList.isEmpty()) {
//                log.error("[MT4账号：{}-{}-{}]重连失败,网络不可达", trader4.getId(), trader4.getAccount(), trader4.getServerName());
//            } else {
//                for (ServersDatIniUtil.Address address : addressList) {
//                    try {
////                        this.mt4Api.OnOrderUpdate.removeAllListeners();
////                        this.mt4Api.OnConnect.removeAllListeners();
//                        this.quoteClient.Disconnect();
////                        this.mt4Api = new Mt4Api(Integer.parseInt(trader4.getAccount()), trader4.getPassword(), address.getHost(), address.getPort());
//                        this.quoteClient.Password = trader4.getPassword();
//                        this.quoteClient.Host = address.getHost();
//                        this.quoteClient.Port = address.getPort();
//                    } catch (Exception ex) {
//                        log.error("", ex);
//                    }
//                    if (reconnect()) {
//                        break;
//                    }
//                }
//            }
//        }
//    }
//
//    void freshTimeWhenConnected() throws ConnectException, TimeoutException {
//        LambdaUpdateWrapper<AotfxTrader> lambdaUpdateWrapper = Wrappers.<AotfxTrader>lambdaUpdate().set(AotfxTrader::getCurrency, quoteClient.Account().currency).set(AotfxTrader::getLeverage, quoteClient.AccountLeverage()).set(AotfxTrader::getBalance, quoteClient.AccountBalance()).set(AotfxTrader::getDemo, quoteClient.IsDemoAccount()).set(AotfxTrader::getConnected, Boolean.TRUE).set(AotfxTrader::getLatestConnect, LocalDateTime.now()).set(AotfxTrader::getStatusExtra, "账号在线").set(AotfxTrader::getPasswordType, quoteClient.AccountMode() != 1 ? 0 : 1).eq(AotfxTrader::getId, trader4.getId());
//
//        if (jfx.isCommission()) {
//            Date commissionEndDate = new Date();
//            long between = commissionBeginDate == null ? 100 : DateUtil.between(commissionBeginDate, commissionEndDate, DateUnit.MINUTE, true);
//            if (between > 30) {
//                commissionBeginDate = new Date();
//                this.threeStrategyThreadPoolExecutor.submit(new CommissionCallable(threeStrategyThreadPoolExecutor, trader4, this));
//            }
//        }
//
//        Date apiAnalysisEndDate = new Date();
//        long between = apiAnalysisBeginDate == null ? 100 : DateUtil.between(apiAnalysisBeginDate, apiAnalysisEndDate, DateUnit.MINUTE, true);
//        if (between > 5) {
//            apiAnalysisBeginDate = new Date();
//            analysisThreeStrategyThreadPoolExecutor.submit(new ApiAnalysisCallable(analysisThreeStrategyThreadPoolExecutor, trader4, this, new SynInfo(trader4.getId(), false)));
//        }
//
//        //刷新净值
//        LocalDate today = quoteClient.ServerTime().toLocalDate();
//        BigDecimal equity = BigDecimal.valueOf(quoteClient.AccountEquity());
//        if (equityMap.get(today) == null) {
//            updateEquity(today, equity);
//        } else {
//            BigDecimal bigDecimal = equityMap.get(today);
//            if (bigDecimal.compareTo(equity) > 0) {
//                updateEquity(today, equity);
//            }
//        }
//
//        //  此处加上try的原因是，某些经济商不支持读取服务器时间的获取。或者市场关闭(ERR_MARKET_CLOSED 132)。获取失败后会抛出异常。
//        try {
//            lambdaUpdateWrapper.set(AotfxTrader::getDiff, quoteClient.ServerTimeZone() / 60);
//        } catch (Exception exception) {
//            log.error("", exception);
//        } finally {
//            traderService.update(lambdaUpdateWrapper);
//        }
//    }
//
//    private void updateEquity(LocalDate today, BigDecimal equity) {
////        EaProfitLossDayEquity dayEquity = profitLossDayEquityService.getOne(Wrappers.<EaProfitLossDayEquity>lambdaUpdate()
////                .eq(EaProfitLossDayEquity::getOfDate, today)
////                .eq(EaProfitLossDayEquity::getAccount, trader4.getAccount())
////                .eq(EaProfitLossDayEquity::getServerName, trader4.getServerName()));
////        if (dayEquity == null || dayEquity.getEquity().compareTo(equity) > 0) {
////            profitLossDayEquityService.saveOrUpdate(
////                    EaProfitLossDayEquity.builder()
////                            .ofDate(today)
////                            .equity(equity)
////                            .account(trader4.getAccount()).serverName(trader4.getServerName()).build(),
////                    Wrappers.<EaProfitLossDayEquity>lambdaUpdate()
////                            .eq(EaProfitLossDayEquity::getOfDate, today)
////                            .eq(EaProfitLossDayEquity::getAccount, trader4.getAccount())
////                            .eq(EaProfitLossDayEquity::getServerName, trader4.getServerName()));
////            equityMap.put(today, equity);
////        }
//
//    }
//
//    /**
//     * 开始交易
//     *
//     * @param subscriptions 订阅的主题
//     * @return Boolean
//     */
//    public abstract Boolean startTrade(List<String> subscriptions);
//
//    /**
//     * 开始执行相关业务操作：开始消费KAFKA数据，
//     *
//     * @return true-成功 false-失败
//     */
//    public abstract Boolean startTrade();
//
//    /**
//     * 停止交易
//     *
//     * @return Boolean
//     */
//    public Boolean stopTrade() {
//        if (orderUpdateHandler != null) {
//            try {
//                orderUpdateHandler.setRunning(Boolean.FALSE);
//            } catch (Exception e) {
//                log.error("设置orderUpdateHandler running 为false", e);
//            }
//        }
//        // 跟单者对象关闭定时任务
//        if (!ObjectUtils.isEmpty(updateTradeInfoFuture)) {
//            try {
//                updateTradeInfoFuture.cancel(Boolean.TRUE);
//            } catch (Exception e) {
//                log.error("updateTradeInfoFuture cancel", e);
//            }
//        }
//
//        if (!ObjectUtils.isEmpty(cycleCloseOrderTask)) {
//            try {
//                cycleCloseOrderTask.setRunning(Boolean.FALSE);
//            } catch (Exception e) {
//                log.error("cycleCloseOrderTask running false", e);
//            }
//        }
//        try {
//            quoteClient.OnOrderUpdate.removeAllListeners();
//            quoteClient.OnConnect.removeAllListeners();
//            quoteClient.OnDisconnect.removeAllListeners();
//            quoteClient.OnQuoteHistory.removeAllListeners();
//            quoteClient.OnDisconnect.removeAllListeners();
//            quoteClient.Disconnect();
//            log.info("关闭mtapi");
//        } catch (Exception e) {
//            log.error("", e);
//        }
//        return this.cldKafkaConsumer.stopConsume();
//    }
//
//    /**
//     * 获取MT4品种的信息
//     *
//     * @param symbol 品种
//     * @param cache  是否从缓存中获取
//     * @return EaSymbolInfo
//     */
//    public EaSymbolInfo symbolInfo(String symbol, boolean cache) throws InvalidSymbolException, ConnectException, TimeoutException {
//        EaSymbolInfo eaSymbolInfo;
//        if (cache) {
//            eaSymbolInfo = symbolInfoMap.get(symbol);
//            if (ObjectUtils.isEmpty(eaSymbolInfo)) {
//                eaSymbolInfo = symbolInfo(symbol);
//            }
//        } else {
//            eaSymbolInfo = symbolInfo(symbol);
//            symbolInfoMap.put(symbol, eaSymbolInfo);
//        }
//        return eaSymbolInfo;
//    }
//
//    private EaSymbolInfo symbolInfo(String symbol) throws InvalidSymbolException, ConnectException, TimeoutException {
//        EaSymbolInfo eaSymbolInfo;
//        boolean anyMatch = Arrays.stream(this.quoteClient.Symbols()).anyMatch((s) -> s.equalsIgnoreCase(symbol));
//        if (!anyMatch) {
//            throw new InvalidSymbolException(symbol, this.quoteClient.Log);
//        }
//        SymbolInfo symbolInfo = this.GetSymbolInfo(symbol);
//        ConGroupSec conGroupSec = this.quoteClient.GetSymbolGroupParams(symbol);
//        eaSymbolInfo = new EaSymbolInfo(symbolInfo, conGroupSec);
//        eaSymbolInfo.setTradeTickSize(this.quoteClient.GetTickSize(symbol));
//        eaSymbolInfo.setTradeTickValue(this.quoteClient.GetTickValue(symbol, 10000));
//        return eaSymbolInfo;
//    }
//
//    public Order[] downloadOrderHistory(LocalDateTime from, LocalDateTime to) throws Exception {
//        Order[] orders = quoteClient.DownloadOrderHistory(from, to);
//        return Arrays.stream(orders).filter(order -> !order.CloseTime.isBefore(from) && order.CloseTime.isBefore(to)).toArray(Order[]::new);
//    }
//
//    /**
//     * 开启定时任务——更新liveOrders数组（持仓订单的盈亏）
//     */
//    public void startSyncLiveOrders() {
//        if (null == syncLiveOrdersTask) {
//            synchronized (lock) {
//                if (null == syncLiveOrdersTask) {
//                    syncLiveOrdersTask = new SyncLiveOrdersTask(this);
//                    syncLiveOrdersTask.start();
//                }
//            }
//        }
//    }
//
//    /**
//     * 取消定时任务——更新liveOrders数组（持仓订单的盈亏）
//     */
//    public void cancelSyncLiveOrders() {
//        if (null != syncLiveOrdersTask) {
//            synchronized (lock) {
//                if (null != syncLiveOrdersTask) {
//                    syncLiveOrdersTask.cancel();
//                    syncLiveOrdersTask = null;
//                }
//            }
//        }
//    }
//
//    public void unbind(AotfxTrader copier4) {
//
//    }
//
////    /**
////     * 判断当前账户净值是否小于设置的最小风控净值，
////     * <p>
////     * 1-如果是则进行风控干预
////     * 2-如果不是则不干预
////     */
////    public void riskControl(AotfxTrader traderFromDb) {
////        BigDecimal riskEquity = traderFromDb == null ? BigDecimal.ZERO : traderFromDb.getMinEquity();
////        if (riskEquity != null && riskEquity.compareTo(BigDecimal.ZERO) != 0) {
////            equityRiskListener.onTriggered(this, riskEquity);
////            this.updateTraderInfo();
////        }
////    }
//}

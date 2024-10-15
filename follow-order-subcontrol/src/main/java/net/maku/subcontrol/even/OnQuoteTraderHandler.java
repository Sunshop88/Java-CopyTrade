package net.maku.subcontrol.even;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.service.FollowTraderService;
import net.maku.followcom.service.impl.FollowTraderServiceImpl;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.followcom.vo.FollowRedisTraderVO;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.subcontrol.trader.AbstractApiTrader;
import online.mtapi.mt4.QuoteClient;
import online.mtapi.mt4.QuoteEventArgs;
import online.mtapi.mt4.QuoteEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static online.mtapi.mt4.Op.Buy;
import static online.mtapi.mt4.Op.Sell;

public class OnQuoteTraderHandler implements QuoteEventHandler {
    private static final Logger log = LoggerFactory.getLogger(OnQuoteTraderHandler.class);
    protected FollowTraderEntity leader;
    protected AbstractApiTrader abstractApiTrader;
    protected ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;
    protected FollowTraderService followTraderService;
    protected Boolean running = Boolean.TRUE;
    protected RedisCache redisCache;

    private final FollowRedisTraderVO followRedisTraderVO=new FollowRedisTraderVO();
    // 设定时间间隔，单位为毫秒
    private final long interval = 5000; // 5秒间隔

    // 记录上次执行时间
    private long lastInvokeTimeTrader = 0;
    // 设定时间间隔，单位为毫秒
    private final long intervalTrader = 300000; // 五分钟间隔
    // 用于存储每个 symbol 上次执行时间
    private static final ConcurrentHashMap<String, Long> symbolLastInvokeTimeMap = new ConcurrentHashMap<>();


    public OnQuoteTraderHandler(AbstractApiTrader abstractApiTrader ) {
        this.abstractApiTrader=abstractApiTrader;
        this.scheduledThreadPoolExecutor = ThreadPoolUtils.getScheduledExecute();;
        followTraderService=SpringContextUtils.getBean(FollowTraderServiceImpl.class);
        redisCache=SpringContextUtils.getBean(RedisCache.class);
    }


    public void invoke(Object sender, QuoteEventArgs quote) {

        // 获取当前系统时间
        long currentTime = System.currentTimeMillis();
        // 获取该symbol上次执行时间
        long lastSymbolInvokeTime = symbolLastInvokeTimeMap.getOrDefault(quote.Symbol, 0L);
        if (currentTime - lastSymbolInvokeTime  >= interval) {
            // 更新该symbol的上次执行时间为当前时间
            symbolLastInvokeTimeMap.put(quote.Symbol, currentTime);
            QuoteClient qc = (QuoteClient) sender;
            try {
                //缓存经常变动的三个值信息
                followRedisTraderVO.setTraderId(abstractApiTrader.getTrader().getId());
                followRedisTraderVO.setBalance(BigDecimal.valueOf(qc.AccountBalance()));
                followRedisTraderVO.setEuqit(BigDecimal.valueOf(qc.AccountEquity()));
                followRedisTraderVO.setFreeMargin(BigDecimal.valueOf(qc.FreeMargin));
                if (BigDecimal.valueOf(qc.AccountMargin()).compareTo(BigDecimal.ZERO) != 0) {
                    followRedisTraderVO.setMarginProportion(BigDecimal.valueOf(qc.AccountEquity()).divide(BigDecimal.valueOf(qc.AccountMargin()),4, RoundingMode.HALF_UP));
                }else {
                    followRedisTraderVO.setMarginProportion(BigDecimal.ZERO);
                }
                followRedisTraderVO.setTotal((int)Arrays.stream(qc.GetOpenedOrders()).filter(order ->order.Type == Buy||order.Type == Sell).count());
                followRedisTraderVO.setBuyNum((int)Arrays.stream(qc.GetOpenedOrders()).filter(order ->order.Type == Buy).count());
                followRedisTraderVO.setSellNum((int)Arrays.stream(qc.GetOpenedOrders()).filter(order ->order.Type == Sell).count());
                redisCache.set(Constant.TRADER_USER+abstractApiTrader.getTrader().getId(),followRedisTraderVO);
            } catch (Exception e) {
                System.err.println("Error during quote processing: " + e.getMessage());
                e.printStackTrace();
            }
        }
        // 判断当前时间与上次执行时间的间隔是否达到设定的间隔时间
        if (currentTime - lastInvokeTimeTrader >= intervalTrader) {
            // 更新上次执行时间为当前时间
            lastInvokeTimeTrader = currentTime;
            updateTraderInfo();
        }
    }

    /**
     * 更新 FollowTraderEntity 信息，每5分钟执行一次
     */
    private void updateTraderInfo() {
        try {
            FollowRedisTraderVO followRedisTraderVO = (FollowRedisTraderVO) redisCache.get(Constant.TRADER_USER + abstractApiTrader.getTrader().getId());
            if (ObjectUtil.isNotEmpty(followRedisTraderVO)){
                log.info("每5分钟更新一次数据库 traderId: {}", abstractApiTrader.getTrader().getId());
                followTraderService.update(Wrappers.<FollowTraderEntity>lambdaUpdate()
                        .eq(FollowTraderEntity::getId, abstractApiTrader.getTrader().getId())
                        .set(FollowTraderEntity::getEuqit, followRedisTraderVO.getEuqit())
                        .set(FollowTraderEntity::getMarginProportion,followRedisTraderVO.getMarginProportion())
                        .set(FollowTraderEntity::getBalance, followRedisTraderVO.getBalance()));
            }
        } catch (Exception e) {
            log.error("Error updating trader info: {}", e.getMessage(), e);
        }
    }
}

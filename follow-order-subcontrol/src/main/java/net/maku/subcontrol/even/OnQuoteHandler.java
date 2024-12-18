package net.maku.subcontrol.even;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import net.maku.followcom.entity.FollowOrderSendEntity;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.service.FollowOrderSendService;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.utils.JsonUtils;
import net.maku.subcontrol.trader.AbstractApiTrader;
import net.maku.subcontrol.vo.FollowOrderSendSocketVO;
import net.maku.subcontrol.websocket.TraderOrderSendWebSocket;
import online.mtapi.mt4.QuoteClient;
import online.mtapi.mt4.QuoteEventArgs;
import online.mtapi.mt4.QuoteEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class OnQuoteHandler implements QuoteEventHandler {
    private static final Logger log = LoggerFactory.getLogger(OnQuoteHandler.class);
    protected FollowTraderEntity leader;
    protected AbstractApiTrader abstractApiTrader;
    private TraderOrderSendWebSocket traderOrderSendWebSocket;
    private FollowOrderSendService followOrderSendService;
    private RedisCache redisCache;


    // 设定时间间隔，单位为毫秒
    private final long interval = 3000; // 3秒间隔
    // 用于存储每个 symbol 上次执行时间
    private static final ConcurrentHashMap<String, Long> symbolLastInvokeTimeMap = new ConcurrentHashMap<>();

    public OnQuoteHandler(AbstractApiTrader abstractApiTrader ) {
        this.abstractApiTrader=abstractApiTrader;
        traderOrderSendWebSocket=SpringContextUtils.getBean(TraderOrderSendWebSocket.class);
        followOrderSendService=SpringContextUtils.getBean(FollowOrderSendService.class);
        redisCache=SpringContextUtils.getBean(RedisCache.class);
    }


    public void invoke(Object sender, QuoteEventArgs quote) {
        // 判断当前时间与上次执行时间的间隔是否达到设定的间隔时间
        // 获取当前系统时间
        long currentTime = System.currentTimeMillis();
        // 获取该symbol上次执行时间
        long lastSymbolInvokeTime = symbolLastInvokeTimeMap.getOrDefault(quote.Symbol, 0L);
        if (currentTime - lastSymbolInvokeTime  >= interval) {
            symbolLastInvokeTimeMap.put(quote.Symbol, currentTime);
            QuoteClient qc = (QuoteClient) sender;
            try {
                handleQuote(qc, quote);
            } catch (Exception e) {
                System.err.println("Error during quote processing: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void handleQuote(QuoteClient qc, QuoteEventArgs quote) {

        //账户信息
        log.info("OnQuote监听：" +abstractApiTrader.getTrader().getId()+ quote.Symbol+quote.Bid+"dd"+quote.Ask);

//        List<FollowOrderSendEntity> list;
//        if (ObjectUtil.isEmpty(redisCache.get(Constant.TRADER_ORDER + abstractApiTrader.getTrader().getId()))){
//            list = followOrderSendService.list(new LambdaQueryWrapper<FollowOrderSendEntity>().eq(FollowOrderSendEntity::getTraderId,abstractApiTrader.getTrader().getId()));
//            redisCache.set(Constant.TRADER_ORDER + abstractApiTrader.getTrader().getId(),list);
//        }else {
//            list = (List<FollowOrderSendEntity>) redisCache.get(Constant.TRADER_ORDER + abstractApiTrader.getTrader().getId());
//        }
//        //查看当前账号订单完成进度
//        FollowOrderSendSocketVO  followOrderSendSocketVO = new FollowOrderSendSocketVO();
//        followOrderSendSocketVO.setSellPrice(quote.Bid);
//        followOrderSendSocketVO.setBuyPrice(quote.Ask);
//        followOrderSendSocketVO.setStatus(CloseOrOpenEnum.OPEN.getValue());
//
//        if (ObjectUtil.isNotEmpty(list)) {
//            FollowOrderSendEntity followOrderSendEntity = list.stream()
//                    .filter(o -> o.getStatus().equals(CloseOrOpenEnum.CLOSE.getValue()) && o.getSymbol().equals(quote.Symbol))
//                    .findFirst().orElse(null);
//            if (followOrderSendEntity != null) {
//                followOrderSendSocketVO.setStatus(followOrderSendEntity.getStatus());
//                followOrderSendSocketVO.setScheduleNum(followOrderSendEntity.getTotalNum());
//                followOrderSendSocketVO.setScheduleSuccessNum(followOrderSendEntity.getSuccessNum());
//                followOrderSendSocketVO.setScheduleFailNum(followOrderSendEntity.getFailNum());
//            }
//
//        }
//
//        traderOrderSendWebSocket.pushMessage(abstractApiTrader.getTrader().getId().toString(),quote.Symbol, JsonUtils.toJsonString(followOrderSendSocketVO));
    }



}

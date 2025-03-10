package net.maku.mascontrol.websocket;

import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.google.common.util.concurrent.AtomicDouble;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import net.maku.followcom.entity.FollowOrderDetailEntity;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.entity.FollowTraderUserEntity;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.enums.TraderCloseEnum;
import net.maku.followcom.query.FollowTraderUserQuery;
import net.maku.followcom.service.DashboardService;
import net.maku.followcom.service.FollowTraderService;
import net.maku.followcom.service.FollowTraderUserService;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.followcom.vo.*;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.common.utils.ThreadPoolUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.rmi.ServerException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static dm.jdbc.util.DriverUtil.log;

/**
 * Author:  zsd
 * Date:  2025/2/24/周一 16:46
 */
@Component
@ServerEndpoint("/socket/bargain")
public class BargainWebSocket {

    private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private Map<String, ScheduledFuture<?>> scheduledFutureMap = new HashMap<>();
    private RedisCache redisCache = SpringContextUtils.getBean(RedisCache.class);
    private final FollowTraderUserService followTraderUserService=SpringContextUtils.getBean(FollowTraderUserService.class);
    private final FollowTraderService followTraderService=SpringContextUtils.getBean(FollowTraderService.class);

    @OnOpen
    public void onOpen(Session session) throws IOException {
    }
    // 当接收到客户端的消息时调用
    @OnMessage
    public void onMessage(String message, Session session) throws ServerException {
        try {
            String id = session.getId();
            JSONObject jsonObj = JSONObject.parseObject(message);
            //当前选中的id [{"account":111,"platformId"},{"account":111,"platformId"}}
            JSONArray accountVos = jsonObj.getJSONArray("accountVos");
          //  Long currentAccountId = jsonObj.getLong("traderUserId");
            String traderUserJson = jsonObj.getString("traderUserQuery");
            String symbol = jsonObj.getString("symbol");
            FollowTraderUserQuery traderUserQuery = JSONObject.parseObject(traderUserJson, FollowTraderUserQuery.class);
            traderUserQuery.setAccountVos(accountVos);
            ScheduledFuture<?> st = scheduledFutureMap.get(id);
            if (st != null) {
                st.cancel(true);
            }
          ScheduledFuture scheduledFuture = scheduledExecutorService.scheduleAtFixedRate(() -> {
            try {
                BargainAccountVO bargainAccountVO = new BargainAccountVO();
              //  traderUserQuery.setHangStatus(CloseOrOpenEnum.OPEN.getValue());
                //账号列表
               // long startTime = System.currentTimeMillis();
                TraderUserStatVO traderUserStatVO = followTraderUserService.searchPage(traderUserQuery);
                PageResult<FollowTraderUserVO> followTraderUserVOPageResult = traderUserStatVO.getPageResult();
              /*  long endTime = System.currentTimeMillis();
                long duration = (endTime - startTime);
                System.out.println("运行时间: " + duration + " 毫秒");*/
                bargainAccountVO.setTraderUserPage(followTraderUserVOPageResult);
                bargainAccountVO.setAccountNum(traderUserStatVO.getTotal());
                bargainAccountVO.setAccountConnectedNum(traderUserStatVO.getConNum());
                bargainAccountVO.setAccountDisconnectedNum(traderUserStatVO.getTotal()-traderUserStatVO.getConNum());
                bargainAccountVO.setParagraph(traderUserStatVO.getParagraph().getValue().setScale(2, BigDecimal.ROUND_HALF_UP));
                //选中当前账号的持仓
             /*  if(ObjectUtil.isNotEmpty(accountVos)) {
                    getActive(accountVos,bargainAccountVO);
                }*/
                //概览 BigDecimal
                AtomicDouble aDouble = new AtomicDouble();
                List<List<BigDecimal>> statList = new ArrayList<>();
                List<BigDecimal> ls1 = Arrays.asList(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
                List<BigDecimal> ls2 = Arrays.asList(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
                List<FollowTraderEntity> traders = followTraderService.list();
                Map<String,FollowTraderEntity> map = new ConcurrentHashMap<>();
                Map<String,FollowTraderEntity> sucessmap = new ConcurrentHashMap<>();
                Map<String,FollowTraderEntity> errmap = new ConcurrentHashMap<>();
                Map<String, AtomicBigDecimal> totalJson = new ConcurrentHashMap();
                Map<String,AtomicBigDecimal> currentJson = new ConcurrentHashMap();
                totalJson.put("tBuyNum",new AtomicBigDecimal(BigDecimal.ZERO));
                totalJson.put("tSellNum",new AtomicBigDecimal(BigDecimal.ZERO));
                totalJson.put("tProfit",new AtomicBigDecimal(BigDecimal.ZERO));

                currentJson.put("BuyNum",new AtomicBigDecimal(BigDecimal.ZERO));
                currentJson.put("SellNum",new AtomicBigDecimal(BigDecimal.ZERO));
                currentJson.put("Profit",new AtomicBigDecimal(BigDecimal.ZERO));
                CountDownLatch  countDownLatch = new CountDownLatch(traders.size());
                traders.forEach(t->{

                    ThreadPoolUtils.getExecutor().execute(()->{
                        FollowTraderEntity json = map.get(t.getAccount() + "-" + t.getPlatformId());
                        if(json == null) {
                            //判断是否连接成功
                            if(t.getStatus()==CloseOrOpenEnum.CLOSE.getValue()){
                                addData(t, totalJson, sucessmap, accountVos, currentJson,symbol);

                            }else{
                                errmap.put(t.getAccount() + "-" + t.getPlatformId(), t);
                            }
                        }
                        countDownLatch.countDown();
                    });
               });
                try {
                    countDownLatch.await();
                } catch (InterruptedException e) {
                    log.error("推送异常："+e);
                }
                errmap.forEach((k,v)->{
                    FollowTraderEntity followTraderEntity = sucessmap.get(k);
                    if(followTraderEntity==null){
                        addData(v, totalJson, sucessmap, accountVos, currentJson,symbol);
                    }
                });
                ls1.set(0, totalJson.get("tBuyNum").getValue());
                ls1.set(1, totalJson.get("tSellNum").getValue());
                ls1.set(2, totalJson.get("tProfit").getValue());
                ls2.set(0, currentJson.get("BuyNum")==null?BigDecimal.ZERO:currentJson.get("BuyNum").getValue());
                ls2.set(1, currentJson.get("SellNum")==null?BigDecimal.ZERO:currentJson.get("SellNum").getValue());
                ls2.set(2, currentJson.get("Profit")==null?BigDecimal.ZERO:currentJson.get("Profit").getValue());
                statList.add(ls1);
                statList.add(ls2);
               /*   long end = System.currentTimeMillis();

               duration = (end - startTime);
                System.out.println("最后运行时间: " + duration + " 毫秒");*/
                bargainAccountVO.setStatList(statList);
                ObjectMapper objectMapper = new ObjectMapper();
                JavaTimeModule javaTimeModule = new JavaTimeModule();
                //格式化时间格式
                javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                objectMapper.registerModule(javaTimeModule);
                String s = objectMapper.writeValueAsString(bargainAccountVO);
                session.getBasicRemote().sendText(s);
            } catch (Exception e) {
                e.printStackTrace();
                log.error("推送异常:{}", e);

            }
        }, 0, 1, TimeUnit.SECONDS);
        scheduledFutureMap.put(id, scheduledFuture);
    } catch (Exception e) {
        log.error(e.getMessage());
        throw new ServerException(e.getMessage());

    }
    }


    private void addData(FollowTraderEntity t, Map<String,AtomicBigDecimal> totalJson, Map<String, FollowTraderEntity> sucessmap, JSONArray accountVos,  Map<String,AtomicBigDecimal> currentJson,String symbol) {
        AtomicBigDecimal tBuyNum = totalJson.get("tBuyNum");
        AtomicBigDecimal tSellNum = totalJson.get("tSellNum");
        AtomicBigDecimal tProfit = totalJson.get("tProfit");
     //   FollowRedisTraderVO o1 = (FollowRedisTraderVO)redisCache.get(Constant.TRADER_USER + t.getId());
        Object o2 = redisCache.get(Constant.TRADER_ACTIVE + t.getId());
        List<OrderActiveInfoVO> orderActiveInfoList =new ArrayList<>();

        if (ObjectUtil.isNotEmpty(o2)){
            orderActiveInfoList = JSONObject.parseArray(o2.toString(), OrderActiveInfoVO.class);
        }
        if(ObjectUtil.isNotEmpty(o2)){
            orderActiveInfoList.forEach(o->{
                if(ObjectUtil.isNotEmpty(symbol)){
                    if(symbol.equals(o.getSymbol())){
                        if(o.getType().equals("Buy")){
                            //多单
                            tBuyNum.add(new BigDecimal(o.getLots()));
                        }else{
                            //空单
                            tSellNum.add(new BigDecimal(o.getLots()));
                        }
                        //总盈亏
                        tProfit.add(new BigDecimal(o.getProfit()));
                      /*  if(t.getAccount().toString().equals("301351769")){
                            System.out.println("aa");
                        }*/
                        addCurrent(t, accountVos, currentJson, o);
                    }
                }else{
                    if(o.getType().equals("Buy")){
                        //多单
                        tBuyNum.add(new BigDecimal(o.getLots()));
                    }else{
                        //空单
                        tSellNum.add(new BigDecimal(o.getLots()));
                    }
                    //总盈亏
                    tProfit.add(new BigDecimal(o.getProfit()));
                    addCurrent(t, accountVos, currentJson, o);
                }

            });
        }



        totalJson.put("tBuyNum",tBuyNum);
        totalJson.put("tSellNum",tSellNum);
        totalJson.put("tProfit",tProfit);
        sucessmap.put(t.getAccount() + "-" + t.getPlatformId(), t);
      
    }

    private static void addCurrent(FollowTraderEntity t, JSONArray accountVos, Map<String, AtomicBigDecimal> currentJson, OrderActiveInfoVO o) {
        if(ObjectUtil.isNotEmpty(accountVos)) {
            boolean flag = accountVos.stream().anyMatch(od -> {
                JSONObject obj = JSONObject.parseObject(od.toString());
                Integer account = obj.getInteger("account");
                Integer platformId = obj.getInteger("platformId");
                return account.toString().equals(t.getAccount().toString()) && platformId.toString().equals(t.getPlatformId().toString());
            });
            if(flag){
                AtomicBigDecimal buyNum = currentJson.get("BuyNum");
                AtomicBigDecimal sellNum = currentJson.get("SellNum");
                AtomicBigDecimal profit = currentJson.get("Profit");
                if(o.getType().equals("Buy")){
                    //多单
                    buyNum.add(new BigDecimal(o.getLots()));
                }else{
                    //空单
                    sellNum.add(new BigDecimal(o.getLots()));
                }
                //总盈亏
                profit.add(new BigDecimal(o.getProfit()));
                currentJson.put("BuyNum",buyNum);
                currentJson.put("SellNum",sellNum);
                currentJson.put("Profit",profit);
            }
        }
    }


    // 当客户端断开连接时调用
    @OnClose
    public void onClose(Session session) {
        try {
            String id = session.getId();
            ScheduledFuture<?> scheduledFuture = scheduledFutureMap.get(id);
            if (scheduledFuture != null && !scheduledFuture.isCancelled()) {
                scheduledFuture.cancel(true);
            }
            if (session != null && session.getBasicRemote() != null) {
                session.close();
            }

        } catch (IOException e) {
            log.error("关闭链接异常{}", e);
            throw new RuntimeException(e);
        }

    }

    // 当发生错误时调用
    @OnError
    public void onError(Session session, Throwable throwable) {
        System.out.println("Error occurred: " + throwable.getMessage());
        throwable.printStackTrace();
    }

}

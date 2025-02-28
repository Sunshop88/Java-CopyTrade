package net.maku.mascontrol.websocket;

import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.entity.FollowTraderUserEntity;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.query.FollowTraderUserQuery;
import net.maku.followcom.service.DashboardService;
import net.maku.followcom.service.FollowTraderService;
import net.maku.followcom.service.FollowTraderUserService;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.followcom.vo.BargainAccountVO;
import net.maku.followcom.vo.FollowRedisTraderVO;
import net.maku.followcom.vo.FollowTraderUserVO;
import net.maku.followcom.vo.OrderActiveInfoVO;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.utils.PageResult;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.rmi.ServerException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static dm.jdbc.util.DriverUtil.log;

/**
 * Author:  zsd
 * Date:  2025/2/24/周一 16:46
 */
@Component
@ServerEndpoint("/socket/bargain")
public class BargainWebSocket {
    private final DashboardService dashboardService = SpringContextUtils.getBean(DashboardService.class);
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
            Long currentAccountId = jsonObj.getLong("traderUserId");
            String traderUserJson = jsonObj.getString("traderUserQuery");
            FollowTraderUserQuery traderUserQuery = JSONObject.parseObject(traderUserJson, FollowTraderUserQuery.class);

            ScheduledFuture<?> st = scheduledFutureMap.get(id);
            if (st != null) {
                st.cancel(true);
            }
          ScheduledFuture scheduledFuture = scheduledExecutorService.scheduleAtFixedRate(() -> {
            try {
                BargainAccountVO bargainAccountVO = new BargainAccountVO();
                //账号列表
                PageResult<FollowTraderUserVO> followTraderUserVOPageResult = followTraderUserService.searchPage(traderUserQuery).getPageResult();
                bargainAccountVO.setTraderUserPage(followTraderUserVOPageResult);
                //选中当前账号的持仓
                if(ObjectUtil.isNotEmpty(currentAccountId)) {
                    getActive(currentAccountId,bargainAccountVO);
                }
                //概览
                List<List<BigDecimal>> statList = new ArrayList<>();
                List<BigDecimal> ls1 = Arrays.asList(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
                List<BigDecimal> ls2 = Arrays.asList(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
                List<FollowTraderEntity> traders = followTraderService.list();
                Map<String,FollowTraderEntity> map = new ConcurrentHashMap<>();
                Map<String,FollowTraderEntity> sucessmap = new ConcurrentHashMap<>();
                Map<String,FollowTraderEntity> errmap = new ConcurrentHashMap<>();
                JSONObject totalJson = new JSONObject();
                JSONObject currentJson = new JSONObject();
                totalJson.put("tBuyNum",BigDecimal.ZERO);
                totalJson.put("tSellNum",BigDecimal.ZERO);
                totalJson.put("tProfit",BigDecimal.ZERO);

                currentJson.put("BuyNum",BigDecimal.ZERO);
                currentJson.put("SellNum",BigDecimal.ZERO);
                currentJson.put("Profit",BigDecimal.ZERO);
                traders.forEach(t->{
                    FollowTraderEntity json = map.get(t.getAccount() + "-" + t.getPlatformId());
                    if(json == null) {
                        //判断是否连接成功
                        if(t.getStatus()==CloseOrOpenEnum.CLOSE.getValue()){
                            addData(t, totalJson, sucessmap, accountVos, currentJson);

                        }else{
                            errmap.put(t.getAccount() + "-" + t.getPlatformId(), t);
                        }
                    }
                });
                errmap.forEach((k,v)->{
                    FollowTraderEntity followTraderEntity = sucessmap.get(k);
                    if(followTraderEntity==null){
                        addData(v, totalJson, sucessmap, accountVos, currentJson);
                    }
                });
                ls1.set(0, totalJson.getBigDecimal("tBuyNum"));
                ls1.set(1, totalJson.getBigDecimal("tSellNum"));
                ls1.set(2, totalJson.getBigDecimal("tProfit"));
                ls2.set(0, totalJson.getBigDecimal("BuyNum"));
                ls2.set(1, totalJson.getBigDecimal("SellNum"));
                ls2.set(2, totalJson.getBigDecimal("Profit"));
                statList.add(ls1);
                statList.add(ls2);
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

    private void addData(FollowTraderEntity t, JSONObject totalJson, Map<String, FollowTraderEntity> sucessmap, JSONArray accountVos, JSONObject currentJson) {
        BigDecimal tBuyNum = totalJson.getBigDecimal("tBuyNum");
        BigDecimal tSellNum = totalJson.getBigDecimal("tSellNum");
        BigDecimal tProfit = totalJson.getBigDecimal("tProfit");
        FollowRedisTraderVO o1 = (FollowRedisTraderVO)redisCache.get(Constant.TRADER_USER + t.getId());
        //多单
        tBuyNum = tBuyNum.add(new BigDecimal(o1.getBuyNum()));
        //空单
        tSellNum=  tSellNum.add(new BigDecimal(o1.getSellNum()));
        //总盈亏
        tProfit = tProfit.add(o1.getProfit());
        totalJson.put("tBuyNum",tBuyNum);
        totalJson.put("tSellNum",tSellNum);
        totalJson.put("tProfit",tProfit);
        sucessmap.put(t.getAccount() + "-" + t.getPlatformId(), t);
        if(ObjectUtil.isNotEmpty(accountVos)){
            boolean flag = accountVos.stream().anyMatch(o -> {
                JSONObject obj = JSONObject.parseObject(o.toString());
                Integer account = obj.getInteger("account");
                Integer platformId = obj.getInteger("platformId");
                return account.equals(t.getAccount()) && platformId.equals(t.getPlatformId());
            });
           if(flag){
               BigDecimal buyNum = currentJson.getBigDecimal("BuyNum");
               BigDecimal sellNum = currentJson.getBigDecimal("SellNum");
               BigDecimal profit = currentJson.getBigDecimal("Profit");
               buyNum = buyNum.add(new BigDecimal(o1.getBuyNum()));
               //空单
               sellNum=  sellNum.add(new BigDecimal(o1.getSellNum()));
               //总盈亏
               profit = profit.add(o1.getProfit());
               currentJson.put("BuyNum",tBuyNum);
               currentJson.put("SellNum",tSellNum);
               currentJson.put("Profit",tProfit);
           }
        }
    }

    private void getActive(Long currentAccountId,BargainAccountVO bargainAccountVO){
       FollowTraderUserEntity traderUser = followTraderUserService.getById(currentAccountId);
       List<FollowTraderEntity> list = followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getAccount, traderUser.getAccount()).eq(FollowTraderEntity::getPlatformId, traderUser.getPlatformId()));
       AtomicLong traderId=new AtomicLong(0);
       list.forEach(o->{
           if(o.getStatus().equals(CloseOrOpenEnum.CLOSE.getValue())){
               traderId.set(o.getId());
           }
       });
       if(traderId.get()==0l){
           traderId.set(list.get(0).getId());
       }
       Object o1 = redisCache.get(Constant.TRADER_ACTIVE + traderId.get());
       List<OrderActiveInfoVO> orderActiveInfoList =new ArrayList<>();
       if (ObjectUtil.isNotEmpty(o1)){
           orderActiveInfoList = JSONObject.parseArray(o1.toString(), OrderActiveInfoVO.class);

       }
       bargainAccountVO.setOrderActiveInfoList(orderActiveInfoList);
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

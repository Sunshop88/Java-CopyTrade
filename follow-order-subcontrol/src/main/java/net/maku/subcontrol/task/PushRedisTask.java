package net.maku.subcontrol.task;

import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.convert.FollowTraderConvert;
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.enums.ConCodeEnum;
import net.maku.followcom.enums.PlacedTypeEnum;
import net.maku.followcom.enums.TraderTypeEnum;
import net.maku.followcom.service.*;
import net.maku.followcom.service.impl.FollowOrderDetailServiceImpl;
import net.maku.followcom.service.impl.FollowPlatformServiceImpl;
import net.maku.followcom.service.impl.FollowTraderServiceImpl;
import net.maku.followcom.service.impl.FollowVpsServiceImpl;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.followcom.vo.*;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.cache.RedissonLockUtil;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.subcontrol.trader.*;
import online.mtapi.mt4.ConGroup;
import online.mtapi.mt4.Op;
import online.mtapi.mt4.Order;
import online.mtapi.mt4.QuoteClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Author:  zsd
 * Date:  2024/12/26/周四 10:25
 */
@Slf4j
@Component
@AllArgsConstructor
@NoArgsConstructor
public class PushRedisTask {

    private FollowTraderService followTraderService = SpringContextUtils.getBean(FollowTraderServiceImpl .class);
    private FollowTraderSubscribeService followTraderSubscribeService = SpringContextUtils.getBean(FollowTraderSubscribeService.class);
    private FollowPlatformService followPlatformService = SpringContextUtils.getBean(FollowPlatformServiceImpl .class);
    private RedisUtil redisUtil = SpringContextUtils.getBean(RedisUtil.class);
    private LeaderApiTradersAdmin leaderApiTradersAdmin = SpringContextUtils.getBean(LeaderApiTradersAdmin.class);
    private CopierApiTradersAdmin copierApiTradersAdmin = SpringContextUtils.getBean(CopierApiTradersAdmin.class);
    private FollowVpsService followVpsService = SpringContextUtils.getBean(FollowVpsServiceImpl.class);
    private FollowOrderDetailService followOrderDetailService = SpringContextUtils.getBean(FollowOrderDetailServiceImpl.class);
    private static volatile boolean mflag=true;
    private RedissonLockUtil redissonLockUtil=SpringContextUtils.getBean(RedissonLockUtil.class);
/*   @PostConstruct
   public void init(){
       execute();

   }*/
  @Scheduled(cron = "0/5 * * * * ?")
  public void execute(){
      execute(null,null);
  }
  //  @Scheduled(cron = "0/5 * * * * ?")
    public void execute(FollowTraderEntity trader,Integer type){
                //  FollowConstant.LOCAL_HOST FollowConstant.LOCAL_HOST
                //"39.98.109.212" FollowConstant.LOCAL_HOST FollowConstant.LOCAL_HOST
               FollowVpsEntity one = followVpsService.getOne(new LambdaQueryWrapper<FollowVpsEntity>().eq(FollowVpsEntity::getIpAddress, FollowConstant.LOCAL_HOST).eq(FollowVpsEntity::getDeleted, 0));
               // FollowVpsEntity one = followVpsService.getOne(new LambdaQueryWrapper<FollowVpsEntity>().eq(FollowVpsEntity::getIpAddress,"39.101.133.150").eq(FollowVpsEntity::getDeleted,0));
                if (one != null) {
                    pushCache(one.getId(),trader,type);
                    pushRepair(one.getId());
                } else {
                    List<FollowVpsEntity> vpsLists = followVpsService.list(new LambdaQueryWrapper<FollowVpsEntity>().eq(FollowVpsEntity::getIpAddress, FollowConstant.LOCAL_HOST));
                    vpsLists.forEach(v -> {
                        redisUtil.delSlaveRedis(Integer.toString(v.getId()));
                        redisUtil.delSlaveRedis(Integer.toString(v.getId()) + "-Compare");
                    });

                }




    }

    /***
     * 推送redis漏单
     * */
    private  void  pushRepair(Integer vpsId){
        ThreadPoolUtils.execute(() -> {
            //查询当前vpsId所有账号
            List<FollowTraderEntity> followTraderList = followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getServerId, vpsId).eq(FollowTraderEntity::getType,TraderTypeEnum.SLAVE_REAL.getType()));
            List<FollowTraderSubscribeEntity> list = followTraderSubscribeService.list();
            Map<Long, FollowTraderSubscribeEntity> subscribeMap = list.stream().collect(Collectors.toMap(FollowTraderSubscribeEntity::getSlaveId, Function.identity()));
            List<RepairCacheVO> repairs=new ArrayList<>();
            followTraderList.forEach(t->{
                FollowTraderSubscribeEntity followTraderSubscribeEntity = subscribeMap.get(t.getId());
                if(followTraderSubscribeEntity!=null){
                RepairCacheVO repairCacheVO = new RepairCacheVO();
                repairCacheVO.setFollowId(followTraderSubscribeEntity.getSlaveId());
                repairCacheVO.setSourceId(followTraderSubscribeEntity.getMasterId());
                repairCacheVO.setSourceUser(Long.valueOf(followTraderSubscribeEntity.getMasterAccount()));
                repairCacheVO.setFollowUser(Long.valueOf(followTraderSubscribeEntity.getSlaveAccount()));
                //漏开
                Object o1 = redisUtil.hGetStr(Constant.REPAIR_SEND + followTraderSubscribeEntity.getMasterAccount() + ":" + followTraderSubscribeEntity.getMasterId(), followTraderSubscribeEntity.getSlaveAccount());
                Map<Integer, JSONObject> opens = new HashMap();
               List<OrderCacheVO> openList = new ArrayList<>();
                if (o1 != null && o1.toString().trim().length() > 0) {
                    opens = JSONObject.parseObject(o1.toString(), Map.class);
                    opens.forEach((k,o)->{
                        OrderRepairInfoVO v = JSONObject.parseObject(String.valueOf(o), OrderRepairInfoVO.class);
                        OrderCacheVO orderCacheVO = new OrderCacheVO();
                        orderCacheVO.setId(null);
                        orderCacheVO.setLogin(repairCacheVO.getFollowUser());
                        orderCacheVO.setTicket(v.getMasterTicket());
                        orderCacheVO.setOpenTime(v.getMasterOpenTime());
                        orderCacheVO.setCloseTime(v.getMasterCloseTime());
                        if( v.getMasterType().equals("Buy")){
                            orderCacheVO.setType(Op.Buy);
                        }else {
                            orderCacheVO.setType(Op.Sell);
                        }
                        orderCacheVO.setLots(v.getMasterLots());
                        orderCacheVO.setSymbol(v.getMasterSymbol());
                        orderCacheVO.setOpenPrice(v.getMasterOpenPrice());
                        orderCacheVO.setMagicNumber(v.getMasterTicket());
                        orderCacheVO.setPlaceType("Client");
                        FollowOrderDetailEntity one = followOrderDetailService.getOne(new LambdaQueryWrapper<FollowOrderDetailEntity>().eq(FollowOrderDetailEntity::getOrderNo, v.getMasterTicket()).eq(FollowOrderDetailEntity::getAccount,followTraderSubscribeEntity.getMasterAccount()));
                       /* orderCacheVO.setStopLoss(one.gets);
                        orderCacheVO.setTakeProfit(one.);*/
                        if(one!=null){
                            orderCacheVO.setSwap(one.getSwap().doubleValue());
                            orderCacheVO.setCommission(one.getCommission().doubleValue());
                            orderCacheVO.setComment(one.getComment());
                            if(one.getClosePrice()!=null){
                                orderCacheVO.setClosePrice(one.getClosePrice().doubleValue());
                            }
                            orderCacheVO.setProfit(one.getProfit().doubleValue());
                        }

                        openList.add(orderCacheVO);
                    });
                }
                repairCacheVO.setOpen(openList);
                //漏平
                Object o2 = redisUtil.hGetStr(Constant.REPAIR_CLOSE + followTraderSubscribeEntity.getMasterAccount() + ":" + followTraderSubscribeEntity.getMasterId(), followTraderSubscribeEntity.getSlaveAccount());
                Map<Integer, JSONObject> close = new HashMap();
                List<OrderCacheVO> closeList = new ArrayList<>();
                if (o2 != null && o2.toString().trim().length() > 0) {
                    close = JSONObject.parseObject(o2.toString(), Map.class);
                    close.forEach((k,o)->{
                        OrderRepairInfoVO v = JSONObject.parseObject(String.valueOf(o), OrderRepairInfoVO.class);
                        OrderCacheVO orderCacheVO = new OrderCacheVO();
                        orderCacheVO.setId(null);
                        orderCacheVO.setLogin(repairCacheVO.getFollowUser());
                        orderCacheVO.setTicket(v.getMasterTicket());
                        orderCacheVO.setOpenTime(v.getMasterOpenTime());
                        orderCacheVO.setCloseTime(v.getMasterCloseTime());
                        if( v.getMasterType().equals("Buy")){
                            orderCacheVO.setType(Op.Buy);
                        }else {
                            orderCacheVO.setType(Op.Sell);
                        }
                        orderCacheVO.setLots(v.getMasterLots());
                        orderCacheVO.setSymbol(v.getMasterSymbol());
                        orderCacheVO.setOpenPrice(v.getMasterOpenPrice());
                        orderCacheVO.setMagicNumber(v.getMasterTicket());
                        orderCacheVO.setPlaceType("Client");
                        FollowOrderDetailEntity one = followOrderDetailService.getOne(new LambdaQueryWrapper<FollowOrderDetailEntity>().eq(FollowOrderDetailEntity::getOrderNo, v.getMasterTicket()).eq(FollowOrderDetailEntity::getAccount, followTraderSubscribeEntity.getMasterAccount()));
                       /* orderCacheVO.setStopLoss(one.gets);
                        orderCacheVO.setTakeProfit(one.);*/
                        if(one!=null){
                            orderCacheVO.setSwap(one.getSwap().doubleValue());
                            orderCacheVO.setCommission(one.getCommission().doubleValue());
                            orderCacheVO.setComment(one.getComment());
                            orderCacheVO.setClosePrice(one.getClosePrice().doubleValue());
                            orderCacheVO.setProfit(one.getProfit().doubleValue());
                        }

                        closeList.add(orderCacheVO);
                    });
                }
                repairCacheVO.setClose(closeList);
                repairs.add(repairCacheVO);
                }
            });
            //设置redis
            try {
                ObjectMapper objectMapper = new ObjectMapper();

                JavaTimeModule javaTimeModule = new JavaTimeModule();
                //格式化时间格式
                javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                objectMapper.registerModule(javaTimeModule);
                String    json = objectMapper.writeValueAsString(repairs);
                redisUtil.setSlaveRedis(Integer.toString(vpsId)+"-Compare", json);
            } catch (JsonProcessingException e) {
               log.error("外部接口redis漏单数据推送异常"+e);
            }
        });
    }

    /**
     * 推送redis缓存
     */
    private void pushCache(Integer vpsId,FollowTraderEntity trader,Integer type) {

      ThreadPoolUtils.execute(() -> {
           String localHost = FollowConstant.LOCAL_HOST;
           String keyl="LOCK:" + localHost;
           boolean lock = redissonLockUtil.lock(keyl, 3, -1, TimeUnit.SECONDS);
           try {
               if (lock) {

            //查询当前vpsId所有账号
            List<FollowTraderEntity> followTraderList = followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getServerId, vpsId));
            //获取
            List<FollowTraderSubscribeEntity> list = followTraderSubscribeService.list();
            Map<Long, FollowTraderSubscribeEntity> subscribeMap = list.stream().collect(Collectors.toMap(FollowTraderSubscribeEntity::getSlaveId, Function.identity()));
            //根据vpsId账号分组
            Map<Integer, List<FollowTraderEntity>> map = new HashMap<>();
            //查询所有平台
            List<FollowPlatformEntity> platformList = followPlatformService.list();
            Map<Long, List<FollowPlatformEntity>> platformMap = platformList.stream().collect(Collectors.groupingBy(FollowPlatformEntity::getId));
            String key = "VPS:PUSH:";
            followTraderList.sort((o1,o2)->{
                return   o1.getType().compareTo(o2.getType());
            });

            map.put(vpsId, followTraderList);


            map.forEach((k, v) -> {
                //多线程写
             //   boolean flag = redisUtil.setnx(key + k, k, 2);
                boolean flag = true;
                StringBuilder sbb=new StringBuilder();
                //设置成功过表示超过2秒内
                if (flag) {
                    List<AccountCacheVO> accounts = new Vector<>();
                    //遍历账号获取持仓订单
                    CountDownLatch countDownLatch = new CountDownLatch(v.size());
                    for (FollowTraderEntity h : v) {

                        ThreadPoolUtils.getExecutor().execute(()->{


                            AccountCacheVO accountCache = FollowTraderConvert.INSTANCE.convertCache(h);

                            if (h.getType().equals(TraderTypeEnum.SLAVE_REAL.getType())){
                                accountCache.setType("FOLLOW");
                            }else{
                                accountCache.setType("SOURCE");
                            }
                            accountCache.setServer(h.getIpAddr());
                            List<OrderCacheVO> orderCaches = new ArrayList<>();
                            //根据id
                            String akey = (h.getType() == 0 ? "S" : "F") + h.getId();
                            accountCache.setKey(akey);

                            if (h.getType().equals(TraderTypeEnum.MASTER_REAL.getType())){
                                String group = h.getId() + " " + h.getAccount();
                                accountCache.setGroup(group);
                            }else{
                                FollowTraderSubscribeEntity sb = subscribeMap.get(h.getId());
                                if(sb!=null) {
                                    String desc = PlacedTypeEnum.getDesc(sb.getPlacedType() == null ? 0 : sb.getPlacedType());
                                    accountCache.setPlacedTypeString(desc);
                                    accountCache.setStatus(sb.getFollowStatus() == 0 ? false : true);
                                    if (sb != null) {
                                        String group = sb.getMasterId() + " " + sb.getMasterAccount();
                                        accountCache.setGroup(group);
                                    }
                                }
                            }


                            List<FollowPlatformEntity> followPlatformEntities = platformMap.get(Long.valueOf(h.getPlatformId()));
                            if(followPlatformEntities!=null && followPlatformEntities.size()>0) {
                                String platformType = followPlatformEntities.get(0).getPlatformType();
                                accountCache.setPlatformType(platformType);
                            }

                            //订单信息
                            QuoteClient quoteClient = null;
                            try {
                                quoteClient= getQuoteClient(h.getId(),h,quoteClient);
                            } catch (Exception e) {

                            }
                            //所有持仓
                            try {
                                if (ObjectUtil.isNotEmpty(quoteClient)) {
                                    Order[] orders = quoteClient.GetOpenedOrders();
                                    //账号信息
                                    ConGroup account = quoteClient.Account();
                                    accountCache.setTimeZone(quoteClient.ServerTimeZone());
                                    accountCache.setCredit(quoteClient.Credit);
                                    Map<Op, List<Order>> orderMap = Arrays.stream(orders).collect(Collectors.groupingBy(order -> order.Type));
                                    accountCache.setLots(0.00);
                                    accountCache.setCount(0);
                                    accountCache.setBuy(0.00);
                                    accountCache.setSell(0.00);
                                    accountCache.setProfit(0.00);
                                    accountCache.setFreeMargin(quoteClient.FreeMargin);
                                    if(quoteClient.Margin!=0) {
                                        BigDecimal v1 = new BigDecimal(quoteClient.Equity).divide(new BigDecimal(quoteClient.Margin), 5, BigDecimal.ROUND_HALF_UP).multiply(new BigDecimal(100));
                                        accountCache.setMarginLevel(v1);
                                    }

                                    accountCache.setMargin(BigDecimal.valueOf(quoteClient.Margin));
                                    // accountCache.setCredit(quoteClient.Credit);
                                    if (h.getType().equals(TraderTypeEnum.SLAVE_REAL.getType())){
                                        FollowTraderSubscribeEntity followTraderSubscribeEntity = subscribeMap.get(h.getId());
                                        if(followTraderSubscribeEntity!=null) {
                                            String direction = followTraderSubscribeEntity.getFollowDirection() == 0 ? "正" : "反";
                                            //  0-固定手数 1-手数比例 2-净值比例
                                            String mode = null;
                                            switch (followTraderSubscribeEntity.getFollowMode()) {
                                                case (0):
                                                    mode = "固定";
                                                    break;
                                                case (1):
                                                    mode = "手";
                                                    break;
                                                case (2):
                                                    mode = "净";
                                                    break;
                                            }
                                            accountCache.setModeString(direction + "|全部|" + mode + "*" + followTraderSubscribeEntity.getFollowParam());
                                        }
                                    }
                                    if(ObjectUtil.isEmpty(accountCache.getModeString())){
                                        accountCache.setModeString("");
                                    }
                                    if(quoteClient.Connected()){
                                        accountCache.setManagerStatus("Connected");
                                    }else{
                                        accountCache.setManagerStatus("Disconnected");
                                    }

                                    orderMap.forEach((a, b) -> {
                                      /*  switch (a) {
                                            case Buy:
                                                accountCache.setBuy(ObjectUtil.isEmpty(b) ? 0 : b.size());
                                                break;
                                            case Sell:
                                                accountCache.setSell(ObjectUtil.isEmpty(b) ? 0 : b.size()) ;
                                                break;
                                            default:
                                                Integer count = ObjectUtil.isEmpty(b) ? 0 : b.size();
                                                accountCache.setCount(accountCache.getCount() + count);
                                                break;
                                        }*/
                                        if (ObjectUtil.isNotEmpty(b)) {
                                            b.forEach(x -> {
                                                OrderCacheVO orderCacheVO = new OrderCacheVO();
                                                //  orderCacheVO.setId(x.);
                                                //    orderCacheVO.setLogin(x.);
                                                orderCacheVO.setTicket(x.Ticket);

                                                ZonedDateTime openTimeUtc = x.OpenTime
                                                        .atZone(ZoneId.of("Asia/Shanghai")) // 强制指定北京时区
                                                        .withZoneSameInstant(ZoneOffset.UTC);
                                                orderCacheVO.setOpenTime(openTimeUtc.toLocalDateTime());

                                                ZonedDateTime closeTimeUtc = x.CloseTime
                                                        .atZone(ZoneId.of("Asia/Shanghai")) // 强制指定北京时区
                                                        .withZoneSameInstant(ZoneOffset.UTC);
                                                orderCacheVO.setCloseTime(closeTimeUtc.toLocalDateTime());
                                                orderCacheVO.setType(x.Type);
                                                orderCacheVO.setLots(x.Lots);
                                                orderCacheVO.setSymbol(x.Symbol);
                                                orderCacheVO.setOpenPrice(x.OpenPrice);
                                                orderCacheVO.setStopLoss(x.StopLoss);
                                                orderCacheVO.setTakeProfit(x.TakeProfit);
                                                orderCacheVO.setClosePrice(x.ClosePrice);
                                                orderCacheVO.setMagicNumber(x.MagicNumber);
                                                orderCacheVO.setSwap(x.Swap);
                                                orderCacheVO.setCommission(x.Commission);
                                                orderCacheVO.setComment(x.Comment);
                                                orderCacheVO.setProfit(x.Profit);
                                                if (h.getType().equals(TraderTypeEnum.SLAVE_REAL.getType())){
                                                    FollowTraderSubscribeEntity followTraderSubscribeEntity = subscribeMap.get(h.getId());
                                                 //  orderCacheVO.setPlaceType(x.p);
                                                  //  accountCache.setPlacedTypeString(PlacedTypeEnum.getDesc(followTraderSubscribeEntity.getPlacedType()));
                                                }
                                                orderCacheVO.setLogin(Long.parseLong(h.getAccount()));
                                                  orderCacheVO.setPlaceType(accountCache.getPlacedTypeString());
                                                orderCaches.add(orderCacheVO);
                                                accountCache.setLots(accountCache.getLots() + x.Lots);
                                                switch (a) {
                                                    case Buy:
                                                        accountCache.setBuy(accountCache.getBuy() + x.Lots);
                                                        accountCache.setCount(accountCache.getCount() + 1);
                                                        break;
                                                    case Sell:
                                                        accountCache.setSell(accountCache.getSell() + x.Lots);
                                                        accountCache.setCount(accountCache.getCount() + 1);
                                                        break;
                                                    default:
                                                       // Integer count = ObjectUtil.isEmpty(b) ? 0 : b.size();
                                                       // accountCache.setCount(accountCache.getCount() + count);
                                                        break;
                                                }
                                                accountCache.setProfit(accountCache.getProfit() + x.Profit);
                                            });
                                        }
                                        accountCache.setOrders(orderCaches);
                                    });
                                }else{
                                    accountCache.setTimeZone(0);
                                    accountCache.setCredit(0.00);
                                    accountCache.setLots(0.00);
                                    accountCache.setCount(0);
                                    accountCache.setBuy(0.00);
                                    accountCache.setSell(0.00);
                                    accountCache.setProfit(0.00);
                                    accountCache.setFreeMargin(0.00);
                                    if (h.getType().equals(TraderTypeEnum.SLAVE_REAL.getType())){
                                        FollowTraderSubscribeEntity followTraderSubscribeEntity = subscribeMap.get(h.getId());
                                        if(followTraderSubscribeEntity!=null) {
                                            accountCache.setPlacedTypeString(PlacedTypeEnum.getDesc(followTraderSubscribeEntity.getPlacedType()));
                                            String direction = followTraderSubscribeEntity.getFollowDirection() == 0 ? "正" : "反";
                                            //  0-固定手数 1-手数比例 2-净值比例
                                            String mode = null;
                                            switch (followTraderSubscribeEntity.getFollowMode()) {
                                                case (0):
                                                    mode = "固定";
                                                    break;
                                                case (1):
                                                    mode = "手";
                                                    break;
                                                case (2):
                                                    mode = "净";
                                                    break;
                                            }
                                            accountCache.setModeString(direction + "|全部|" + mode + "*" + followTraderSubscribeEntity.getFollowParam());
                                        }
                                    }
                                    if(ObjectUtil.isEmpty(accountCache.getModeString())){
                                        accountCache.setModeString("");
                                    }
                                    accountCache.setManagerStatus("Disconnected");
                                    OrderCacheVO orderCacheVO = new OrderCacheVO();
                                    orderCaches.add(orderCacheVO);
                                    accountCache.setLots(0.00);
                                    accountCache.setProfit(0.00);
                                }
                            } catch (Exception e) {
                               log.error("推送redis异常："+e);
                            }

                            if(ObjectUtil.isEmpty(accountCache.getOrders())){
                                accountCache.setOrders(new ArrayList<>());
                            }
                            sbb.append(accountCache.getId()+",");
                            accounts.add(accountCache);
                            countDownLatch.countDown();
                        });

                  //      });
                    }
                    try {
                        countDownLatch.await();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    List<AccountCacheVO> collect = accounts.stream().sorted(Comparator.comparing(AccountCacheVO::getId, Comparator.nullsLast(Long::compareTo))
                                    .reversed().thenComparing(AccountCacheVO::getId, Comparator.nullsLast(Long::compareTo)).reversed())
                            .collect(Collectors.toList());

                    //转出json格式
                    String json = convertJson(collect);
                    log.info("redis推送数据账号数量:{},数据{},排序{}",v.size(),collect.size(),sbb.toString());
                    List<FollowTraderEntity> traders = followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getServerId, vpsId));
                    if(traders.size()==collect.size()){
                        redisUtil.setSlaveRedis(Integer.toString(k), json);
                    }

                 //   redissonLockUtil.unlock(keyl);

                }
            });
               }
           }finally {
               redissonLockUtil.unlock(keyl);
           }
        });
    }

    private QuoteClient getQuoteClient(Long traderId, FollowTraderEntity followTraderVO, QuoteClient quoteClient) {
        AbstractApiTrader abstractApiTrader;
        if (followTraderVO.getType().equals(TraderTypeEnum.MASTER_REAL.getType())){
            abstractApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(traderId.toString());
            if (ObjectUtil.isEmpty(abstractApiTrader) || ObjectUtil.isEmpty(abstractApiTrader.quoteClient) || !abstractApiTrader.quoteClient.Connected()) {
                leaderApiTradersAdmin.removeTrader(traderId.toString());
                ConCodeEnum conCodeEnum = leaderApiTradersAdmin.addTrader(followTraderVO);
                if (conCodeEnum == ConCodeEnum.SUCCESS ) {
                    quoteClient =leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(traderId.toString()).quoteClient;
                    LeaderApiTrader leaderApiTrader1 = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(followTraderVO.getId().toString());
                    leaderApiTrader1.startTrade();
                }else if (conCodeEnum == ConCodeEnum.AGAIN){
                    long maxWaitTimeMillis = 10000; // 最多等待10秒
                    long startTime = System.currentTimeMillis();
                    LeaderApiTrader leaderApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(traderId.toString());
                    // 开始等待直到获取到copierApiTrader1
                    while (leaderApiTrader == null && (System.currentTimeMillis() - startTime) < maxWaitTimeMillis) {
                        try {
                            // 每次自旋等待500ms后再检查
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            // 处理中断
                            Thread.currentThread().interrupt();
                            break;
                        }
                        leaderApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(traderId.toString());
                    }
                    //重复提交
                    if (ObjectUtil.isNotEmpty(leaderApiTrader)){
                        log.info(traderId+"重复提交并等待完成");
                        quoteClient = leaderApiTrader.quoteClient;
                    }else {
                        log.info(traderId+"重复提交并等待失败");
                    }
                }
            } else {
                quoteClient = abstractApiTrader.quoteClient;
            }
        }else {
            abstractApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(traderId.toString());
            if (ObjectUtil.isEmpty(abstractApiTrader) || ObjectUtil.isEmpty(abstractApiTrader.quoteClient) || !abstractApiTrader.quoteClient.Connected()) {
                copierApiTradersAdmin.removeTrader(followTraderVO.getId().toString());
                ConCodeEnum conCodeEnum = copierApiTradersAdmin.addTrader(followTraderVO);
                if (conCodeEnum == ConCodeEnum.SUCCESS) {
                    quoteClient =copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(traderId.toString()).quoteClient;
                    CopierApiTrader copierApiTrader1 = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(followTraderVO.getId().toString());
                    copierApiTrader1.setTrader(followTraderVO);
                    copierApiTrader1.startTrade();
                }else if (conCodeEnum == ConCodeEnum.AGAIN){
                    long maxWaitTimeMillis = 10000; // 最多等待10秒
                    long startTime = System.currentTimeMillis();
                    CopierApiTrader copierApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(followTraderVO.getId().toString());
                    // 开始等待直到获取到copierApiTrader1
                    while (copierApiTrader == null && (System.currentTimeMillis() - startTime) < maxWaitTimeMillis) {
                        try {
                            // 每次自旋等待500ms后再检查
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            // 处理中断
                            Thread.currentThread().interrupt();
                            break;
                        }
                        copierApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(followTraderVO.getId().toString());
                    }
                    //重复提交
                    if (ObjectUtil.isNotEmpty(copierApiTrader)){
                        log.info(followTraderVO.getId()+"重复提交并等待完成");
                        quoteClient = copierApiTrader.quoteClient;
                    }else {
                        log.info(followTraderVO.getId()+"重复提交并等待失败");
                    }
                }
            } else {
                quoteClient = abstractApiTrader.quoteClient;
            }
        }
        return quoteClient;
    }
    /**
     * 转成成json
     */
    private String convertJson(List<AccountCacheVO> accounts) {
        //设置从redis数据
        FollowTraderCacheVO cacheVO = new FollowTraderCacheVO();
        cacheVO.setAccounts(accounts);
        cacheVO.setUpdateAt(new Date());
        cacheVO.setStatus(true);
        ObjectMapper objectMapper = new ObjectMapper();
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        //格式化时间格式
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        objectMapper.registerModule(javaTimeModule);
        String json = null;
        try {
            json = objectMapper.writeValueAsString(cacheVO);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return json;
    }

    public void add(Integer id) {
        String localHost = FollowConstant.LOCAL_HOST;
        String keyl="LOCK:" + localHost;
        boolean lock = redissonLockUtil.lock(keyl, 5, -1, TimeUnit.SECONDS);
        FollowTraderEntity h = followTraderService.getById(Long.valueOf(id));
       
        try {
            if (lock) {
                long startTime = System.currentTimeMillis();
                Object json = redisUtil.getSlaveRedis(h.getServerId().toString());
                JSONObject jsonObject = JSONObject.parseObject(json.toString());
                List<AccountCacheVO> accounts = JSONArray.parseArray(jsonObject.getString("Accounts"), AccountCacheVO.class);
                AccountCacheVO accountCache = FollowTraderConvert.INSTANCE.convertCache(h);
                FollowTraderSubscribeEntity followTraderSubscribeEntity =null;
                if (h.getType().equals(TraderTypeEnum.SLAVE_REAL.getType())){
                    accountCache.setType("FOLLOW");
                }else{
                    accountCache.setType("SOURCE");
                }
                accountCache.setServer(h.getIpAddr());
                List<OrderCacheVO> orderCaches = new ArrayList<>();
                //根据id
                String akey = (h.getType() == 0 ? "S" : "F") + h.getId();
                accountCache.setKey(akey);

                if (h.getType().equals(TraderTypeEnum.MASTER_REAL.getType())){
                    String group = h.getId() + " " + h.getAccount();
                    accountCache.setGroup(group);
                }else{
                    List<FollowTraderSubscribeEntity> list = followTraderSubscribeService.list(new LambdaQueryWrapper<FollowTraderSubscribeEntity>().eq(FollowTraderSubscribeEntity::getSlaveId, h.getId()));
                    followTraderSubscribeEntity= list.get(0);
                    if(followTraderSubscribeEntity!=null) {
                        String desc = PlacedTypeEnum.getDesc(followTraderSubscribeEntity.getPlacedType() == null ? 0 : followTraderSubscribeEntity.getPlacedType());
                        accountCache.setPlacedTypeString(desc);
                        accountCache.setStatus(followTraderSubscribeEntity.getFollowStatus() == 0 ? false : true);
                        if (followTraderSubscribeEntity != null) {
                            String group = followTraderSubscribeEntity.getMasterId() + " " + followTraderSubscribeEntity.getMasterAccount();
                            accountCache.setGroup(group);
                        }
                    }
                }




                    FollowPlatformEntity platform = followPlatformService.getById(h.getPlatformId());
                    String platformType = platform.getPlatformType();
                    accountCache.setPlatformType(platformType);


                //订单信息
                QuoteClient quoteClient = null;

                //所有持仓
                try {
                        accountCache.setCredit(0.00);
                        accountCache.setLots(0.00);
                        accountCache.setCount(0);
                        accountCache.setBuy(0.00);
                        accountCache.setSell(0.00);
                        accountCache.setProfit(0.00);
                        accountCache.setFreeMargin(0.00);
                        if (h.getType().equals(TraderTypeEnum.SLAVE_REAL.getType())){
                            if(followTraderSubscribeEntity!=null) {
                                accountCache.setPlacedTypeString(PlacedTypeEnum.getDesc(followTraderSubscribeEntity.getPlacedType()));
                                String direction = followTraderSubscribeEntity.getFollowDirection() == 0 ? "正" : "反";
                                //  0-固定手数 1-手数比例 2-净值比例
                                String mode = null;
                                switch (followTraderSubscribeEntity.getFollowMode()) {
                                    case (0):
                                        mode = "固定";
                                        break;
                                    case (1):
                                        mode = "手";
                                        break;
                                    case (2):
                                        mode = "净";
                                        break;
                                }
                                accountCache.setModeString(direction + "|全部|" + mode + "*" + followTraderSubscribeEntity.getFollowParam());
                            }
                        }
                        if(ObjectUtil.isEmpty(accountCache.getModeString())){
                            accountCache.setModeString("");
                        }
                        accountCache.setManagerStatus("Connected");
                        OrderCacheVO orderCacheVO = new OrderCacheVO();
                        orderCaches.add(orderCacheVO);
                        accountCache.setLots(0.00);
                        accountCache.setProfit(0.00);

                } catch (Exception e) {
                    log.error("推送redis异常："+e);
                }

                if(ObjectUtil.isEmpty(accountCache.getOrders())){
                    accountCache.setOrders(new ArrayList<>());
                }
                accounts.add(accountCache);
                List<AccountCacheVO> collect = accounts.stream().sorted(Comparator.comparing(AccountCacheVO::getId, Comparator.nullsLast(Long::compareTo))
                                .reversed().thenComparing(AccountCacheVO::getId, Comparator.nullsLast(Long::compareTo)).reversed())
                        .collect(Collectors.toList());
                //转出json格式
                String js = convertJson(collect);
                redisUtil.setSlaveRedis(Integer.toString(h.getServerId()), json);


            }

      }finally {
            redissonLockUtil.unlock(keyl);
     }
    }

    public void del(SourceDelVo vo) {
        String localHost = FollowConstant.LOCAL_HOST;
        String keyl="LOCK:" + localHost;
        boolean lock = redissonLockUtil.lock(keyl, 3, -1, TimeUnit.SECONDS);
        try {
            if (lock) {
                Object json = redisUtil.getSlaveRedis(vo.getServerId().toString());
                JSONObject jsonObject = JSONObject.parseObject(json.toString());
                List<AccountCacheVO> accounts = JSONArray.parseArray(jsonObject.getString("Accounts"), AccountCacheVO.class);
                List<AccountCacheVO> list = accounts.stream().filter(a -> !a.getKey().equals("S" + vo.getId()) && !a.getKey().equals("F" + vo.getId())).toList();
                String js = convertJson(list);
                redisUtil.setSlaveRedis(Integer.toString(vo.getServerId()), js);
            }
            }finally {
                redissonLockUtil.unlock(keyl);
            }



    }
}

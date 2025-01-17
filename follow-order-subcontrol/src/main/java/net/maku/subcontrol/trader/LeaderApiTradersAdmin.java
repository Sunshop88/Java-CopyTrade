package net.maku.subcontrol.trader;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cld.message.pubsub.kafka.IKafkaProducer;
import com.cld.message.pubsub.kafka.properties.Ks;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.enums.ConCodeEnum;
import net.maku.followcom.enums.TraderStatusEnum;
import net.maku.followcom.enums.TraderTypeEnum;
import net.maku.followcom.service.FollowBrokeServerService;
import net.maku.followcom.service.FollowTraderService;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.followcom.vo.FollowRedisTraderVO;
import net.maku.followcom.vo.FollowTraderVO;
import net.maku.followcom.vo.OrderActiveInfoVO;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.cache.RedissonLockUtil;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.subcontrol.task.UpdateAllTraderInfoTask;
import net.maku.subcontrol.task.UpdateTraderInfoTask;
import net.maku.subcontrol.vo.FollowOrderActiveSocketVO;
import online.mtapi.mt4.Exception.ConnectException;
import online.mtapi.mt4.Exception.TimeoutException;
import online.mtapi.mt4.Order;
import online.mtapi.mt4.QuoteClient;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static online.mtapi.mt4.Op.Buy;
import static online.mtapi.mt4.Op.Sell;

/**
 * @author Samson Bruce
 */
@Component
@Slf4j
@Data
@EqualsAndHashCode(callSuper = true)
public class LeaderApiTradersAdmin extends AbstractApiTradersAdmin {

    public static Map<String, QuoteClient> quoteClientMap = new ConcurrentHashMap<>();
    /**
     * 信号量来控制，连接任务最多支持的并发数
     */
    private Semaphore semaphore;

    /**
     * 显示mt4账户管理器是否启动完成
     */
    private Boolean launchOn = false;

    private final RedisUtil redisUtil;
    public LeaderApiTradersAdmin(FollowTraderService eaTraderService, FollowBrokeServerService eaBrokerService, RedisUtil redisUtil) {
        this.redisUtil = redisUtil;
        this.followTraderService = eaTraderService;
        this.followBrokeServerService = eaBrokerService;
        this.semaphore = new Semaphore(10);
    }

    /**
     * 所有的mt4账户并发连接mt4服务端，连接成功后创建主题。
     *
     * @throws Exception 异常
     */
    @Override
    public void startUp() throws Exception {
        List<FollowTraderEntity> leaders = followTraderService.list(Wrappers.<FollowTraderEntity>lambdaQuery().eq(FollowTraderEntity::getIpAddr, FollowConstant.LOCAL_HOST).eq(FollowTraderEntity::getType, TraderTypeEnum.MASTER_REAL.getType()).eq(FollowTraderEntity::getDeleted, CloseOrOpenEnum.CLOSE.getValue()));
        log.info("{}个MT4喊单账户开始连接经纪商服务器", leaders.size());
        traderCount = leaders.size();
        //倒计时门栓，大小为mt5账户个数，添加完成一个mt5账户倒计时门栓就减1，直到所有都添加完成。
        CountDownLatch countDownLatch = new CountDownLatch(leaders.size());
        for (FollowTraderEntity leader : leaders) {
            ThreadPoolUtils.getExecutor().submit(() -> {
                try {
                    ConCodeEnum conCodeEnum = addTrader(leader);
                    LeaderApiTrader leaderApiTrader = leader4ApiTraderConcurrentHashMap.get(leader.getId().toString());
                    if (conCodeEnum != ConCodeEnum.SUCCESS&&conCodeEnum != ConCodeEnum.AGAIN) {
                        leader.setStatus(TraderStatusEnum.ERROR.getValue());
                        followTraderService.updateById(leader);
                        log.error("喊单者:[{}-{}-{}]启动失败，请校验", leader.getId(), leader.getAccount(), leader.getServerName());
                    } else {
                        log.info("喊单者:[{}-{}-{}-{}]在[{}:{}]启动成功", leader.getId(), leader.getAccount(), leader.getServerName(), leader.getPassword(), leaderApiTrader.quoteClient.Host, leaderApiTrader.quoteClient.Port);
                        leaderApiTrader.startTrade();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    countDownLatch.countDown();
                }
            });
        }
        //需要所有addLeader()函数执行完后才可以
        countDownLatch.await();
        log.info("所有的mt4喊单者结束连接服务器");
        this.launchOn = true;
        //定时更新
//        CompletableFuture.runAsync(() -> {
//            while (!Thread.currentThread().isInterrupted()) {
//                try {
//                    new UpdateAllTraderInfoTask(TraderTypeEnum.MASTER_REAL.getType());
//                    TimeUnit.SECONDS.sleep(120); // 固定延迟
//                } catch (InterruptedException e) {
//                    Thread.currentThread().interrupt();
//                } catch (Exception e) {
//                    log.error("定时任务异常: ", e);
//                }
//            }
//        }, ThreadPoolUtils.getExecutor()); // 使用虚拟线程
    }

    /**
     * 指定mt4账户并发连接mt4服务端，连接成功后创建主题。
     */
    @Override
    public void startUp(List<FollowTraderEntity> list){
        for (FollowTraderEntity leader : list) {
            ThreadPoolUtils.getExecutor().submit(() -> {
                try {
                    ConCodeEnum conCodeEnum = addTrader(leader);
                    LeaderApiTrader leaderApiTrader = leader4ApiTraderConcurrentHashMap.get(leader.getId().toString());
                    if (conCodeEnum != ConCodeEnum.SUCCESS &&conCodeEnum != ConCodeEnum.AGAIN) {
                        leader.setStatus(TraderStatusEnum.ERROR.getValue());
                        followTraderService.updateById(leader);
                        log.error("喊单者:[{}-{}-{}]启动失败，请校验", leader.getId(), leader.getAccount(), leader.getServerName());
                    } else {
                        log.info("喊单者:[{}-{}-{}-{}]在[{}:{}]启动成功", leader.getId(), leader.getAccount(), leader.getServerName(), leader.getPassword(), leaderApiTrader.quoteClient.Host, leaderApiTrader.quoteClient.Port);
                        leaderApiTrader.startTrade();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
    }

    @Override
    public ConCodeEnum addTrader(FollowTraderEntity leader) {
        ConCodeEnum conCodeEnum = ConCodeEnum.TRADE_NOT_ALLOWED;
        if (redissonLockUtil.tryLockForShortTime("addTrader" + leader.getId(), 0, 10, TimeUnit.SECONDS)) {
            try {
                log.info("进入登录！！！" + leader.getId());
                //VPS判断
                if (!leader.getIpAddr().equals(FollowConstant.LOCAL_HOST)) {
                    log.info("启动校验异常" + leader.getId());
                    return ConCodeEnum.EXCEPTION;
                }
                String serverNode;
                //优先查看平台默认节点
                if (ObjectUtil.isNotEmpty(redisUtil.hGet(Constant.VPS_NODE_SPEED + leader.getServerId(), leader.getPlatform()))) {
                    serverNode = (String) redisUtil.hGet(Constant.VPS_NODE_SPEED + leader.getServerId(), leader.getPlatform());
                } else {
                    FollowPlatformEntity followPlatformServiceOne = followPlatformService.getOne(new LambdaQueryWrapper<FollowPlatformEntity>().eq(FollowPlatformEntity::getServer, leader.getPlatform()));
                    serverNode = followPlatformServiceOne.getServerNode();
                }
                if (ObjectUtil.isNotEmpty(serverNode)) {
                    //处理节点格式
                    String[] split = serverNode.split(":");
                    conCodeEnum = connectTrader(leader, conCodeEnum, split[0], Integer.valueOf(split[1]));
                    if (conCodeEnum == ConCodeEnum.TRADE_NOT_ALLOWED) {
                        //循环连接
                        FollowVpsEntity vps = followVpsService.getVps(FollowConstant.LOCAL_HOST);
                        // 先查询 test_id 最大值
                        FollowTestDetailEntity followTestDetailEntity = followTestDetailService.getOne(
                                new LambdaQueryWrapper<FollowTestDetailEntity>()
                                        .eq(FollowTestDetailEntity::getServerName, leader.getPlatform())
                                        .eq(FollowTestDetailEntity::getVpsId, vps.getId())
                                        .orderByDesc(FollowTestDetailEntity::getTestId)
                                        .select(FollowTestDetailEntity::getTestId)
                                        .last("LIMIT 1")
                        );
                        Integer maxTestId = followTestDetailEntity != null ? followTestDetailEntity.getTestId() : null;
                        if (ObjectUtil.isNotEmpty(maxTestId)) {
                            //测速记录
                            List<FollowTestDetailEntity> list = followTestDetailService.list(new LambdaQueryWrapper<FollowTestDetailEntity>().eq(FollowTestDetailEntity::getServerName, leader.getPlatform()).eq(FollowTestDetailEntity::getVpsId, vps.getId()).eq(FollowTestDetailEntity::getTestId, maxTestId).orderByAsc(FollowTestDetailEntity::getSpeed));
                            for (FollowTestDetailEntity address : list) {
                                // 如果当前状态已不是TRADE_NOT_ALLOWED，则跳出循环
                                String[] strings = address.getServerNode().split(":");
                                conCodeEnum = connectTrader(leader, conCodeEnum, strings[0], Integer.valueOf(strings[1]));
                                if (conCodeEnum != ConCodeEnum.TRADE_NOT_ALLOWED) {
                                    break;
                                }
                            }
                        } else {
                            List<FollowBrokeServerEntity> serverEntityList = followBrokeServerService.listByServerName(leader.getPlatform());
                            for (FollowBrokeServerEntity address : serverEntityList) {
                                // 如果当前状态已不是TRADE_NOT_ALLOWED，则跳出循环
                                conCodeEnum = connectTrader(leader, conCodeEnum, address.getServerNode(), Integer.valueOf(address.getServerPort()));
                                if (conCodeEnum != ConCodeEnum.TRADE_NOT_ALLOWED) {
                                    break;
                                }
                            }
                        }
                    }
                }
            } finally {
                if (redissonLockUtil.isLockedByCurrentThread("addTrader" + leader.getId())) {
                    redissonLockUtil.unlock("addTrader" + leader.getId());
                }
            }
        }else {
            log.info("重复提交"+leader.getId());
            return ConCodeEnum.AGAIN;
        }
        return conCodeEnum;

    }
    private  ConCodeEnum connectTrader(FollowTraderEntity leader, ConCodeEnum conCodeEnum, String serverNode, Integer serverport) {
        try {
            //查看是否已存在leaderApiTrader
            if (ObjectUtil.isNotEmpty(leader4ApiTraderConcurrentHashMap.get(String.valueOf(leader.getId())))){
                log.info("重复登录"+leader.getId());
                return ConCodeEnum.AGAIN;
            }
            LeaderApiTrader leaderApiTrader = new LeaderApiTrader(leader, serverNode, Integer.valueOf(serverport));
            ConnectionTask connectionTask = new ConnectionTask(leaderApiTrader, this.semaphore,redissonLockUtil);
            FutureTask<ConnectionResult> submit = (FutureTask<ConnectionResult>) ThreadPoolUtils.getExecutor().submit(connectionTask);
            ConnectionResult result = submit.get();
            FollowTraderEntity traderUpdateEn = new FollowTraderEntity();
            traderUpdateEn.setId(leader.getId());
            // 判断连接结果是否成功
            if (result.code == ConCodeEnum.SUCCESS) {
                leaderApiTrader.setTrader(leader);
                leader4ApiTraderConcurrentHashMap.put(String.valueOf(leader.getId()), leaderApiTrader);
                traderUpdateEn.setStatus(TraderStatusEnum.NORMAL.getValue());
                traderUpdateEn.setStatusExtra("启动成功");
                followTraderService.updateById(traderUpdateEn);
                conCodeEnum = ConCodeEnum.SUCCESS;
            }else if (result.code == ConCodeEnum.PASSWORD_FAILURE) {
                traderUpdateEn.setStatus(TraderStatusEnum.ERROR.getValue());
                traderUpdateEn.setStatusExtra("账户密码错误");
                followTraderService.updateById(traderUpdateEn);
                conCodeEnum = ConCodeEnum.PASSWORD_FAILURE;
            }else  if (result.code == ConCodeEnum.TRADE_NOT_ALLOWED) {
                traderUpdateEn.setStatus(TraderStatusEnum.ERROR.getValue());
                traderUpdateEn.setStatusExtra("经纪商异常");
                followTraderService.updateById(traderUpdateEn);
                conCodeEnum = ConCodeEnum.TRADE_NOT_ALLOWED;
            }
        } catch (InterruptedException | ExecutionException | IOException e) {
            log.info("连接异常"+e);
        }
        return conCodeEnum;
    }


    /**
     * @param id mt账户的id
     * @return 删除结果 删除成功返回true 失败返回false
     */
    @Override
    public boolean removeTrader(String id) {
        LeaderApiTrader leader4Trader = this.leader4ApiTraderConcurrentHashMap.get(id);

        boolean b = true;
        if (leader4Trader != null) {
            b = leader4Trader.stopTrade();
            if (b) {
                this.leader4ApiTraderConcurrentHashMap.remove(id);
            }
        }
        return b;
    }


    /**
     * 执行喊单者的连接操作做连接操作
     */
    private static class ConnectionTask implements Callable<ConnectionResult> {
        private final LeaderApiTrader leaderApiTrader;

        //信号量就是简单的获取一个就减少一个，多次获取会获取到多个。线程互斥的可重入性在此不适用。
        private final Semaphore semaphore;
        private final RedissonLockUtil redissonLockUtil;

        ConnectionTask(LeaderApiTrader leaderApiTrader, Semaphore semaphore, RedissonLockUtil redissonLockUtil) {
            this.leaderApiTrader = leaderApiTrader;
            this.semaphore = semaphore;
            this.redissonLockUtil = redissonLockUtil;
        }

        @Override
        public ConnectionResult call() {
            FollowTraderEntity leader = this.leaderApiTrader.getTrader();
            boolean aq = Boolean.FALSE;
            try {
                semaphore.acquire();
                aq = Boolean.TRUE;
                if (redissonLockUtil.tryLockForShortTime("call"+leader.getId(),0,30,TimeUnit.SECONDS)) {
                    this.leaderApiTrader.connect2Broker();
                }else {
                    log.info("执行call异常"+leader.getId());
                    return new ConnectionResult(this.leaderApiTrader, ConCodeEnum.EXCEPTION);
                }
            }catch (Exception e) {
                log.error("[MT4喊单者{}-{}-{}]连接服务器失败，失败原因：[{}]", leader.getId(), leader.getAccount(), leader.getServerName(), e.getClass().getSimpleName() + e.getMessage());
                if (e.getMessage().contains("Invalid account")){
                    //账号错误
                    return new LeaderApiTradersAdmin.ConnectionResult(this.leaderApiTrader, ConCodeEnum.PASSWORD_FAILURE);
                }
                return new ConnectionResult(this.leaderApiTrader, ConCodeEnum.ERROR);
            } finally {
                if (aq) {
                    semaphore.release();
                }
                if (redissonLockUtil.isLockedByCurrentThread("call" + leader.getId())) {
                    redissonLockUtil.unlock("call" + leader.getId());
                }
            }
            log.info("[MT4喊单者{}-{}-{}]连接服务器成功", leader.getId(), leader.getAccount(), leader.getServerName());
            return new ConnectionResult(this.leaderApiTrader, ConCodeEnum.SUCCESS);
        }
    }

    @Data
    @AllArgsConstructor
    private static class ConnectionResult {
        private LeaderApiTrader leaderApiTrader;
        //1-连接成功 2-连接失败，密码错误 3-连接失败，其他原因
        private ConCodeEnum code;
    }

    public  void  pushRedisData( FollowTraderVO followTraderVO,QuoteClient qc){
        try {
            //缓存经常变动的三个值信息
            FollowRedisTraderVO followRedisTraderVO=new FollowRedisTraderVO();
            followRedisTraderVO.setTraderId(followTraderVO.getId());
            followRedisTraderVO.setBalance(BigDecimal.valueOf(qc.AccountBalance()));
            followRedisTraderVO.setProfit(BigDecimal.valueOf(qc.Profit));
            followRedisTraderVO.setEuqit(BigDecimal.valueOf(qc.AccountEquity()));
            followRedisTraderVO.setFreeMargin(BigDecimal.valueOf(qc.FreeMargin));
            if (BigDecimal.valueOf(qc.AccountMargin()).compareTo(BigDecimal.ZERO) != 0) {
                followRedisTraderVO.setMarginProportion(BigDecimal.valueOf(qc.AccountEquity()).divide(BigDecimal.valueOf(qc.AccountMargin()),4, RoundingMode.HALF_UP));
            }else {
                followRedisTraderVO.setMarginProportion(BigDecimal.ZERO);
            }
            Order[] orders = qc.GetOpenedOrders();
            List<Order> openedOrders = Arrays.stream(orders).filter(order -> order.Type == Buy || order.Type == Sell).toList();
            int count =  openedOrders.size();
            //    log.info("{}-MT4,订单数量{},持仓数据：{}",abstractApiTrader.getTrader().getAccount(),count);
            List<OrderActiveInfoVO> orderActiveInfoList = converOrderActive(openedOrders, followTraderVO.getAccount());
            FollowOrderActiveSocketVO followOrderActiveSocketVO = new FollowOrderActiveSocketVO();
            followOrderActiveSocketVO.setOrderActiveInfoList(orderActiveInfoList);
            //存入redis
            redisUtil.set(Constant.TRADER_ACTIVE + followTraderVO.getId(), JSONObject.toJSON(orderActiveInfoList));
            followRedisTraderVO.setTotal(count);

            followRedisTraderVO.setBuyNum(Arrays.stream(orders).filter(order ->order.Type == Buy).mapToDouble(order->order.Lots).sum());
            followRedisTraderVO.setSellNum(Arrays.stream(orders).filter(order ->order.Type == Sell).mapToDouble(order->order.Lots).sum());
            //设置缓存
            followRedisTraderVO.setMargin(qc.Margin);
            followRedisTraderVO.setCredit(qc.Credit);
            followRedisTraderVO.setConnectTrader(qc.Host+":"+qc.Port);
            redisUtil.set(Constant.TRADER_USER+followTraderVO.getId(),followRedisTraderVO);
        } catch (Exception e) {
            log.info("初始化添加{}账号推送redis数据失败:{}",followTraderVO.getAccount(),e);
           e.printStackTrace();

        }
//                qc.Disconnect();
    }
    private List<OrderActiveInfoVO> converOrderActive(List<Order> openedOrders, String account) {
        List<OrderActiveInfoVO> collect = new ArrayList<>();
        for (Order o : openedOrders) {
            OrderActiveInfoVO reusableOrderActiveInfoVO = new OrderActiveInfoVO(); // 从对象池中借用对象
            resetOrderActiveInfoVO(reusableOrderActiveInfoVO, o, account); // 重用并重置对象
            collect.add(reusableOrderActiveInfoVO);
        }

        //倒序返回
        return collect.stream()
                .sorted(Comparator.comparing(OrderActiveInfoVO::getOpenTime).reversed())
                .collect(Collectors.toList());
    }
    private void resetOrderActiveInfoVO(OrderActiveInfoVO vo, Order order, String account) {
        vo.setAccount(account);
        vo.setLots(order.Lots);
        vo.setComment(order.Comment);
        vo.setOrderNo(order.Ticket);
        vo.setCommission(order.Commission);
        vo.setSwap(order.Swap);
        vo.setProfit(order.Profit);
        vo.setSymbol(order.Symbol);
        vo.setOpenPrice(order.OpenPrice);
        vo.setMagicNumber(order.MagicNumber);
        vo.setType(order.Type.name());
        //增加五小时
        vo.setOpenTime(DateUtil.toLocalDateTime(DateUtil.offsetHour(DateUtil.date(order.OpenTime), 0)));
        // vo.setOpenTime(order.OpenTime);
        vo.setStopLoss(order.StopLoss);
        vo.setTakeProfit(order.TakeProfit);
    }
}

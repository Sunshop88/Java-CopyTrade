package net.maku.subcontrol.trader;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.enums.ConCodeEnum;
import net.maku.followcom.enums.TraderStatusEnum;
import net.maku.followcom.enums.TraderTypeEnum;
import net.maku.followcom.service.FollowBrokeServerService;
import net.maku.followcom.service.FollowTraderService;
import net.maku.followcom.service.FollowTraderSubscribeService;
import net.maku.followcom.util.AesUtils;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.followcom.vo.FollowRedisTraderVO;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.cache.RedissonLockUtil;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.utils.ThreadPoolUtils;
import online.mtapi.mt4.Exception.ConnectException;
import online.mtapi.mt4.Exception.TimeoutException;
import online.mtapi.mt4.Order;
import online.mtapi.mt4.OrderClient;
import online.mtapi.mt4.PlacedType;
import online.mtapi.mt4.QuoteClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.*;

import static online.mtapi.mt4.Op.Buy;
import static online.mtapi.mt4.Op.Sell;


/**
 * 跟单者管理对象
 */
@Component
@Slf4j
@Data
@EqualsAndHashCode(callSuper = true)
public class CopierApiTradersAdmin extends AbstractApiTradersAdmin {

    private final RedisUtil redisUtil;
    /**
     * 信号量来控制，连接任务最多支持的并发数
     */
    private Semaphore semaphore;

    /**
     * 显示mt4账户管理器是否启动完成
     */
    private Boolean launchOn = false;
    private KafkaTemplate<Object, Object> kafkaTemplate = SpringContextUtils.getBean(KafkaTemplate.class);
    public CopierApiTradersAdmin(FollowTraderService followTraderService, FollowBrokeServerService followBrokeServerService, FollowTraderSubscribeService followTraderSubscribeService, RedisUtil redisUtil) {
        this.followTraderService = followTraderService;
        this.followBrokeServerService = followBrokeServerService;
        this.followTraderSubscribeService = followTraderSubscribeService;
        this.semaphore = new Semaphore(10);
        this.redisUtil = redisUtil;
    }

    /**
     * 所有的mt4账户并发连接mt4服务端，连接成功后创建主题。
     *
     * @throws Exception 异常
     */
    @Override
    public void startUp() throws Exception {
        List<FollowTraderEntity> slaves = followTraderService.list(Wrappers.<FollowTraderEntity>lambdaQuery().eq(FollowTraderEntity::getIpAddr, FollowConstant.LOCAL_HOST).eq(FollowTraderEntity::getType, TraderTypeEnum.SLAVE_REAL.getType()).eq(FollowTraderEntity::getDeleted, CloseOrOpenEnum.CLOSE.getValue()));
        log.info("{}个MT4跟单账户开始连接经纪商服务器", slaves.size());
        traderCount = slaves.size();
        //倒计时门栓，大小为mt4账户个数，添加完成一个mt4账户倒计时门栓就减1，直到所有都添加完成。
        CountDownLatch countDownLatch = new CountDownLatch(slaves.size());
        for (FollowTraderEntity slave : slaves) {
            ThreadPoolUtils.getExecutor().submit(() -> {
                try {
                    if (ObjectUtil.isNotEmpty(copier4ApiTraderConcurrentHashMap.get(slave.getId().toString()))){
                        return;
                    }
                    ConCodeEnum conCodeEnum = addTrader(slave);
                    CopierApiTrader copierApiTrader = copier4ApiTraderConcurrentHashMap.get(slave.getId().toString());
                    if (conCodeEnum != ConCodeEnum.SUCCESS&&conCodeEnum != ConCodeEnum.AGAIN) {
                        slave.setStatus(TraderStatusEnum.ERROR.getValue());
                        followTraderService.updateById(slave);
                        log.error("跟单者:[{}-{}-{}]启动失败，请校验", slave.getId(), slave.getAccount(), slave.getServerName());
                    }else if (conCodeEnum == ConCodeEnum.AGAIN){
                        long maxWaitTimeMillis = 10000; // 最多等待10秒
                        long startTime = System.currentTimeMillis();
                        copierApiTrader = getCopier4ApiTraderConcurrentHashMap().get(slave.getId().toString());
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
                            copierApiTrader = getCopier4ApiTraderConcurrentHashMap().get(slave.getId().toString());
                        }
                        //重复提交
                        if (ObjectUtil.isNotEmpty(copierApiTrader)){
                            log.info(slave.getId().toString()+"重复提交并等待完成");
                        }else {
                            log.info(slave.getId()+"重复提交并等待失败");
                        }
                    } else {
                        log.info("跟单者:[{}-{}-{}-{}]在[{}:{}]启动成功", slave.getId(), slave.getAccount(), slave.getServerName(), slave.getPassword(), copierApiTrader.quoteClient.Host, copierApiTrader.quoteClient.Port);
                        copierApiTrader.startTrade();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    countDownLatch.countDown();
                }
            });
        }
        //需要所有addCopier()函数执行完后才可以
        countDownLatch.await();
        log.info("所有的mt4跟单者结束连接服务器");
        this.launchOn = true;
//        //定时更新
//        CompletableFuture.runAsync(() -> {
//            while (!Thread.currentThread().isInterrupted()) {
//                try {
//                    new UpdateAllTraderInfoTask(TraderTypeEnum.SLAVE_REAL.getType()).run();
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
        for (FollowTraderEntity copier : list) {
            ThreadPoolUtils.getExecutor().submit(() -> {
                try {
                    ConCodeEnum conCodeEnum = addTrader(copier);
                    CopierApiTrader copierApiTrader = copier4ApiTraderConcurrentHashMap.get(copier.getId().toString());
                    if (conCodeEnum != ConCodeEnum.SUCCESS &&conCodeEnum != ConCodeEnum.AGAIN) {
                        copier.setStatus(TraderStatusEnum.ERROR.getValue());
                        followTraderService.updateById(copier);
                        log.error("跟单者:[{}-{}-{}]启动失败，请校验", copier.getId(), copier.getAccount(), copier.getServerName());
                    } else if (conCodeEnum == ConCodeEnum.AGAIN){
                        long maxWaitTimeMillis = 10000; // 最多等待10秒
                        long startTime = System.currentTimeMillis();
                        copierApiTrader = getCopier4ApiTraderConcurrentHashMap().get(copier.getId().toString());
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
                            copierApiTrader = getCopier4ApiTraderConcurrentHashMap().get(copier.getId().toString());
                        }
                        //重复提交
                        if (ObjectUtil.isNotEmpty(copierApiTrader)){
                            log.info(copier.getId().toString()+"重复提交并等待完成");
                        }else {
                            log.info(copier.getId()+"重复提交并等待失败");
                        }
                    }else {
                        log.info("跟单者:[{}-{}-{}-{}]在[{}:{}]启动成功", copier.getId(), copier.getAccount(), copier.getServerName(), copier.getPassword(), copierApiTrader.quoteClient.Host, copierApiTrader.quoteClient.Port);
                        copierApiTrader.startTrade();
                        if (ObjectUtil.isEmpty(redisUtil.get(Constant.TRADER_USER+copierApiTrader.getTrader().getId()))){
                            setTraderOrder(copierApiTrader);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
    }

    private void setTraderOrder(CopierApiTrader copierApiTrader) {
        try {
            QuoteClient qc = copierApiTrader.quoteClient;
            //启动完成后写入用户信息
            FollowRedisTraderVO followRedisTraderVO = new FollowRedisTraderVO();
            followRedisTraderVO.setTraderId(copierApiTrader.getTrader().getId());
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
            followRedisTraderVO.setTotal(count);
            followRedisTraderVO.setBuyNum(Arrays.stream(orders).filter(order ->order.Type == Buy).mapToDouble(order->order.Lots).sum());
            followRedisTraderVO.setSellNum(Arrays.stream(orders).filter(order ->order.Type == Sell).mapToDouble(order->order.Lots).sum());
            //设置缓存
            followRedisTraderVO.setMargin(qc.Margin);
            followRedisTraderVO.setCredit(qc.Credit);
            followRedisTraderVO.setConnectTrader(qc.Host+":"+qc.Port);
            followRedisTraderVO.setLeverage(qc.Leverage);
            redisUtil.set(Constant.TRADER_USER+copierApiTrader.getTrader().getId(),followRedisTraderVO);
        } catch (ConnectException e) {
            throw new RuntimeException(e);
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param id mt账户的id
     * @return 删除结果 删除成功返回true 失败返回false
     */
    @Override
    public boolean removeTrader(String id) {
        CopierApiTrader copier4Trader = this.copier4ApiTraderConcurrentHashMap.get(id);

        boolean b = true;
        if (copier4Trader != null) {
            b = copier4Trader.stopTrade();
            if (b) {
                this.copier4ApiTraderConcurrentHashMap.remove(id);
            }
        }
        return b;
    }

    @Override
    public ConCodeEnum addTrader(FollowTraderEntity copier) {
        ConCodeEnum conCodeEnum = ConCodeEnum.TRADE_NOT_ALLOWED;
        if (redissonLockUtil.tryLockForShortTime("addTrader" + copier.getId(), 0, 120, TimeUnit.SECONDS)) {
            try{
                //查看账号是否存在
                FollowTraderEntity followById = followTraderService.getById(copier.getId());
                if (ObjectUtil.isEmpty(followById)){
                    log.info("启动账号校验异常" + copier.getId());
                    return ConCodeEnum.EXCEPTION;
                }
                if (!copier.getIpAddr().equals(FollowConstant.LOCAL_HOST)) {
                    log.info("启动校验异常" + copier.getId());
                    return ConCodeEnum.EXCEPTION;
                }
                if (ObjectUtil.isNotEmpty(getCopier4ApiTraderConcurrentHashMap().get(copier.getId().toString()))) {
                    log.info("登录数据存在" + copier.getId());
                    return ConCodeEnum.EXCEPTION;
                }
                String serverNode;
                //优先查看平台默认节点
                if (redisUtil.hKeys(Constant.VPS_NODE_SPEED+copier.getServerId()).contains(copier.getPlatform())&&ObjectUtil.isNotEmpty(redisUtil.hGet(Constant.VPS_NODE_SPEED + copier.getServerId(), copier.getPlatform()))){
                    serverNode = (String) redisUtil.hGet(Constant.VPS_NODE_SPEED + copier.getServerId(), copier.getPlatform());
                }  else {
                    FollowPlatformEntity followPlatformServiceOne = followPlatformService.getOne(new LambdaQueryWrapper<FollowPlatformEntity>().eq(FollowPlatformEntity::getServer, copier.getPlatform()));
                    serverNode = followPlatformServiceOne.getServerNode();
                }
                if (ObjectUtil.isNotEmpty(serverNode)) {
                    //处理节点格式
                    String[] split = serverNode.split(":");
                    log.info(copier.getAccount()+"开始连接账号"+split[0]+":"+Integer.valueOf(split[1]));
                    conCodeEnum = connectTrader(copier, conCodeEnum, split[0], Integer.valueOf(split[1]));
                    if (conCodeEnum == ConCodeEnum.TRADE_NOT_ALLOWED) {
                        //循环连接
                        FollowVpsEntity vps = followVpsService.getVps(FollowConstant.LOCAL_HOST);
                        // 先查询 test_id 最大值
                        FollowTestDetailEntity followTestDetailEntity = followTestDetailService.getOne(
                                new LambdaQueryWrapper<FollowTestDetailEntity>()
                                        .eq(FollowTestDetailEntity::getServerName, copier.getPlatform())
                                        .eq(FollowTestDetailEntity::getVpsId, vps.getId())
                                        .orderByDesc(FollowTestDetailEntity::getTestId)
                                        .select(FollowTestDetailEntity::getTestId)
                                        .last("LIMIT 1")
                        );
                        Integer maxTestId = followTestDetailEntity != null ? followTestDetailEntity.getTestId() : null;
                        if (ObjectUtil.isNotEmpty(maxTestId)) {
                            //测速记录
                            List<FollowTestDetailEntity> list = followTestDetailService.list(new LambdaQueryWrapper<FollowTestDetailEntity>().eq(FollowTestDetailEntity::getServerName, copier.getPlatform()).eq(FollowTestDetailEntity::getVpsId, vps.getId()).eq(FollowTestDetailEntity::getTestId, maxTestId).orderByAsc(FollowTestDetailEntity::getSpeed));
                            for (FollowTestDetailEntity address : list) {
                                // 如果当前状态已不是TRADE_NOT_ALLOWED，则跳出循环
                                String[] strings = address.getServerNode().split(":");
                                log.info(copier.getAccount()+"开始连接账号"+strings[0]+":"+Integer.valueOf(strings[1]));
                                conCodeEnum = connectTrader(copier, conCodeEnum, strings[0], Integer.valueOf(strings[1]));
                                if (conCodeEnum != ConCodeEnum.TRADE_NOT_ALLOWED) {
                                    break;
                                }
                            }
                        } else {
                            //循环连接
                            List<FollowBrokeServerEntity> serverEntityList = followBrokeServerService.listByServerName(copier.getPlatform());
                            for (FollowBrokeServerEntity address : serverEntityList) {
                                // 如果当前状态已不是TRADE_NOT_ALLOWED，则跳出循环
                                log.info(copier.getAccount()+"开始连接账号"+address.getServerNode()+":"+Integer.valueOf(address.getServerPort()));
                                conCodeEnum = connectTrader(copier, conCodeEnum, address.getServerNode(), Integer.valueOf(address.getServerPort()));
                                if (conCodeEnum != ConCodeEnum.TRADE_NOT_ALLOWED) {
                                    break;
                                }
                            }
                        }
                    }
                }
            }finally {
                if (redissonLockUtil.isLockedByCurrentThread("addTrader" + copier.getId())) {
                    redissonLockUtil.unlock("addTrader" + copier.getId());
                }
            }
        }else {
            log.info(copier.getId()+"重复登录");
            return ConCodeEnum.AGAIN;
        }

        if (conCodeEnum==ConCodeEnum.SUCCESS){
            FollowTraderSubscribeEntity followTraderSubscribeEntity = followTraderSubscribeService.getOne(new LambdaQueryWrapper<FollowTraderSubscribeEntity>().eq(FollowTraderSubscribeEntity::getSlaveId, copier.getId()));
            if (ObjectUtil.isNotEmpty(followTraderSubscribeEntity)){
                QuoteClient quoteClient = copier4ApiTraderConcurrentHashMap.get(copier.getId().toString()).quoteClient;
                quoteClient.OrderClient.PlacedType= PlacedType.forValue(followTraderSubscribeEntity.getPlacedType());
            }
        }
        return conCodeEnum;
    }


    private ConCodeEnum connectTrader(FollowTraderEntity copier,ConCodeEnum conCodeEnum,String serverNode,Integer serverport){
        try {
            //查看是否已存在copierApiTrader
            if (ObjectUtil.isNotEmpty(copier4ApiTraderConcurrentHashMap.get(String.valueOf(copier.getId())))){
                log.info("重复登录"+copier.getId());
                return ConCodeEnum.AGAIN;
            }
            CopierApiTrader copierApiTrader = new CopierApiTrader(copier,serverNode, Integer.valueOf(serverport));
            CopierApiTradersAdmin.ConnectionTask connectionTask = new CopierApiTradersAdmin.ConnectionTask(copierApiTrader, this.semaphore,redissonLockUtil);
            FutureTask<CopierApiTradersAdmin.ConnectionResult> submit = (FutureTask<CopierApiTradersAdmin.ConnectionResult>) ThreadPoolUtils.getExecutor().submit(connectionTask);
            CopierApiTradersAdmin.ConnectionResult result = submit.get();
            FollowTraderEntity traderUpdateEn = new FollowTraderEntity();
            traderUpdateEn.setId(copier.getId());
            // 判断连接结果是否成功
            if (result.code == ConCodeEnum.SUCCESS) {
                copierApiTrader.setTrader(copier);
                copier4ApiTraderConcurrentHashMap.put(String.valueOf(copier.getId()), copierApiTrader);
                traderUpdateEn.setStatus(TraderStatusEnum.NORMAL.getValue());
                traderUpdateEn.setStatusExtra("启动成功");
                traderUpdateEn.setLoginNode(serverNode + ":" + serverport);
                followTraderService.updateById(traderUpdateEn);
                conCodeEnum = ConCodeEnum.SUCCESS;
                kafkaTemplate.send("order-repair-listener",String.valueOf(copier.getId()));
            }else if (result.code == ConCodeEnum.PASSWORD_FAILURE) {
                traderUpdateEn.setStatus(TraderStatusEnum.ERROR.getValue());
                traderUpdateEn.setStatusExtra("账户密码错误");
                followTraderService.updateById(traderUpdateEn);
                conCodeEnum = ConCodeEnum.PASSWORD_FAILURE;
            }else {
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
     * 执行跟单者的连接操作做连接操作
     */
    private static class ConnectionTask implements Callable<CopierApiTradersAdmin.ConnectionResult> {
        private final CopierApiTrader copierApiTrader;

        //信号量就是简单的获取一个就减少一个，多次获取会获取到多个。线程互斥的可重入性在此不适用。
        private final Semaphore semaphore;
        private final RedissonLockUtil redissonLockUtil;
        ConnectionTask(CopierApiTrader copierApiTrader, Semaphore semaphore, RedissonLockUtil redissonLockUtil) {
            this.copierApiTrader = copierApiTrader;
            this.semaphore = semaphore;
            this.redissonLockUtil = redissonLockUtil;
        }

        @Override
        public ConnectionResult call() {
            FollowTraderEntity leader = this.copierApiTrader.getTrader();
            boolean aq = Boolean.FALSE;
            try {
                semaphore.acquire();
                aq = Boolean.TRUE;
                if (redissonLockUtil.tryLockForShortTime("call"+leader.getId(),0,30,TimeUnit.SECONDS)) {
                    this.copierApiTrader.connect2Broker();
                }else {
                    log.info("执行call异常"+leader.getId());
                }
            } catch (Exception e) {
                log.error("[MT4跟单者{}-{}-{}-{}]连接服务器失败，失败原因：[{}]", leader.getId(), leader.getAccount(),leader.getPassword(), leader.getServerName(), e.getClass().getSimpleName() + e.getMessage());
                if (e.getMessage().contains("Invalid account")){
                    //账号错误
                    return new ConnectionResult(this.copierApiTrader, ConCodeEnum.PASSWORD_FAILURE);
                }
                return new ConnectionResult(this.copierApiTrader, ConCodeEnum.ERROR);
            } finally {
                if (aq) {
                    semaphore.release();
                }
                if (redissonLockUtil.isLockedByCurrentThread("call" + leader.getId())) {
                    redissonLockUtil.unlock("call" + leader.getId());
                }
            }
       
            log.info("[MT4跟单者{}-{}-{}]连接服务器成功", leader.getId(), leader.getAccount(), leader.getServerName());
            return new ConnectionResult(this.copierApiTrader, ConCodeEnum.SUCCESS);
        }
    }

    @Data
    @AllArgsConstructor
    private static class ConnectionResult {
        private CopierApiTrader copierApiTrader;
        //1-连接成功 2-连接失败，密码错误 3-连接失败，其他原因
        private ConCodeEnum code;
    }


}

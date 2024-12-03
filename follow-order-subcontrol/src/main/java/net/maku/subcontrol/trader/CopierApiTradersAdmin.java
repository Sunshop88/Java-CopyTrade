package net.maku.subcontrol.trader;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cld.message.pubsub.kafka.IKafkaProducer;
import com.cld.message.pubsub.kafka.properties.Ks;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowBrokeServerEntity;
import net.maku.followcom.entity.FollowPlatformEntity;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.enums.ConCodeEnum;
import net.maku.followcom.enums.TraderStatusEnum;
import net.maku.followcom.enums.TraderTypeEnum;
import net.maku.followcom.service.FollowBrokeServerService;
import net.maku.followcom.service.FollowTraderService;
import net.maku.followcom.service.FollowTraderSubscribeService;
import net.maku.followcom.util.FollowConstant;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.exception.ServerException;
import online.mtapi.mt4.Exception.ConnectException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;


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
            scheduledExecutorService.submit(() -> {
                try {
                    ConCodeEnum conCodeEnum = addTrader(slave);
                    CopierApiTrader copierApiTrader = copier4ApiTraderConcurrentHashMap.get(slave.getId().toString());
                    if (conCodeEnum != ConCodeEnum.SUCCESS && !slave.getStatus().equals(TraderStatusEnum.ERROR.getValue())) {
                        slave.setStatus(TraderStatusEnum.ERROR.getValue());
                        followTraderService.updateById(slave);
                        log.error("跟单者:[{}-{}-{}]启动失败，请校验", slave.getId(), slave.getAccount(), slave.getServerName());
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
    }

    /**
     * 指定mt4账户并发连接mt4服务端，连接成功后创建主题。
     */
    @Override
    public void startUp(List<FollowTraderEntity> list){
        for (FollowTraderEntity copier : list) {
            scheduledExecutorService.submit(() -> {
                try {
                    ConCodeEnum conCodeEnum = addTrader(copier);
                    CopierApiTrader copierApiTrader = copier4ApiTraderConcurrentHashMap.get(copier.getId().toString());
                    if (conCodeEnum != ConCodeEnum.SUCCESS && !copier.getStatus().equals(TraderStatusEnum.ERROR.getValue())) {
                        copier.setStatus(TraderStatusEnum.ERROR.getValue());
                        followTraderService.updateById(copier);
                        log.error("跟单者:[{}-{}-{}]启动失败，请校验", copier.getId(), copier.getAccount(), copier.getServerName());
                    } else {
                        log.info("跟单者:[{}-{}-{}-{}]在[{}:{}]启动成功", copier.getId(), copier.getAccount(), copier.getServerName(), copier.getPassword(), copierApiTrader.quoteClient.Host, copierApiTrader.quoteClient.Port);
                        copierApiTrader.startTrade();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
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
        String serverNode;
        //优先查看平台默认节点
        if (ObjectUtil.isNotEmpty(redisUtil.hGet(Constant.VPS_NODE_SPEED+copier.getServerId(),copier.getPlatform()))){
            serverNode=(String)redisUtil.hGet(Constant.VPS_NODE_SPEED+copier.getServerId(),copier.getPlatform());
        }else {
            FollowPlatformEntity followPlatformServiceOne = followPlatformService.getOne(new LambdaQueryWrapper<FollowPlatformEntity>().eq(FollowPlatformEntity::getServer, copier.getPlatform()));
            serverNode=followPlatformServiceOne.getServerNode();
        }
        if (ObjectUtil.isNotEmpty(serverNode)) {
            //处理节点格式
            String[] split = serverNode.split(":");
            conCodeEnum = connectTrader(copier, conCodeEnum, split[0], Integer.valueOf(split[1]));
            if (conCodeEnum == ConCodeEnum.TRADE_NOT_ALLOWED) {
                //循环连接
                List<FollowBrokeServerEntity> serverEntityList = followBrokeServerService.listByServerName(copier.getPlatform());
                for (FollowBrokeServerEntity address : serverEntityList) {
                    // 如果当前状态已不是TRADE_NOT_ALLOWED，则跳出循环
                    conCodeEnum = connectTrader(copier, conCodeEnum, address.getServerNode(), Integer.valueOf(address.getServerPort()));
                    if (conCodeEnum != ConCodeEnum.TRADE_NOT_ALLOWED) {
                        break;
                    }
                }
            }
        }
        return conCodeEnum;
    }


    private ConCodeEnum connectTrader(FollowTraderEntity copier,ConCodeEnum conCodeEnum,String serverNode,Integer serverport){
        try {
            CopierApiTrader copierApiTrader = new CopierApiTrader(copier,serverNode, Integer.valueOf(serverport));
            CopierApiTradersAdmin.ConnectionTask connectionTask = new CopierApiTradersAdmin.ConnectionTask(copierApiTrader, this.semaphore);
            FutureTask<CopierApiTradersAdmin.ConnectionResult> submit = (FutureTask<CopierApiTradersAdmin.ConnectionResult>) scheduledExecutorService.submit(connectionTask);
            CopierApiTradersAdmin.ConnectionResult result = submit.get();
            FollowTraderEntity traderUpdateEn = new FollowTraderEntity();
            traderUpdateEn.setId(copier.getId());
            // 判断连接结果是否成功
            if (result.code == ConCodeEnum.SUCCESS) {
                copierApiTrader.setTrader(copier);
                copier4ApiTraderConcurrentHashMap.put(String.valueOf(copier.getId()), copierApiTrader);
                traderUpdateEn.setStatus(TraderStatusEnum.NORMAL.getValue());
                traderUpdateEn.setStatusExtra("启动成功");
                followTraderService.updateById(traderUpdateEn);
                conCodeEnum = ConCodeEnum.SUCCESS;
            }else if (result.code == ConCodeEnum.PASSWORD_FAILURE) {
                traderUpdateEn.setStatus(TraderStatusEnum.ERROR.getValue());
                traderUpdateEn.setStatusExtra("账户密码错误");
                conCodeEnum = ConCodeEnum.PASSWORD_FAILURE;
            }else {
                traderUpdateEn.setStatus(TraderStatusEnum.ERROR.getValue());
                traderUpdateEn.setStatusExtra("经纪商异常");
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

        ConnectionTask(CopierApiTrader copierApiTrader, Semaphore semaphore) {
            this.copierApiTrader = copierApiTrader;
            this.semaphore = semaphore;
        }

        @Override
        public ConnectionResult call() {
            FollowTraderEntity leader = this.copierApiTrader.getTrader();
            boolean aq = Boolean.FALSE;
            try {
                semaphore.acquire();
                aq = Boolean.TRUE;
                this.copierApiTrader.connect2Broker();
            } catch (ConnectException e) {
                log.error("[MT4跟单者{}-{}-{}]连接服务器失败，失败原因：[{}]", leader.getId(), leader.getAccount(), leader.getServerName(), e.getClass().getSimpleName() + e.getMessage());
                return new ConnectionResult(this.copierApiTrader, ConCodeEnum.PASSWORD_FAILURE);
            } catch (Exception e) {
                log.error("[MT4跟单者{}-{}-{}]连接服务器失败，失败原因：[{}]", leader.getId(), leader.getAccount(), leader.getServerName(), e.getClass().getSimpleName() + e.getMessage());
                return new ConnectionResult(this.copierApiTrader, ConCodeEnum.ERROR);
            } finally {
                if (aq) {
                    semaphore.release();
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

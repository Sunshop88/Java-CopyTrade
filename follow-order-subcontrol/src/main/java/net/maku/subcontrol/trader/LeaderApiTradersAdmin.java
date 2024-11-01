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
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.enums.ConCodeEnum;
import net.maku.followcom.enums.TraderStatusEnum;
import net.maku.followcom.enums.TraderTypeEnum;
import net.maku.followcom.service.FollowBrokeServerService;
import net.maku.followcom.service.FollowTraderService;
import net.maku.followcom.entity.FollowPlatformEntity;
import net.maku.followcom.service.FollowPlatformService;
import net.maku.followcom.util.FollowConstant;
import net.maku.subcontrol.constants.KafkaTopicPrefixSuffix;
import net.maku.subcontrol.util.KafkaTopicUtil;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DeleteTopicsResult;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.common.KafkaFuture;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * @author Samson Bruce
 */
@Component
@Slf4j
@Data
@EqualsAndHashCode(callSuper = true)
public class LeaderApiTradersAdmin extends AbstractApiTradersAdmin {
    /**
     * 线程池对象
     */
    private final ScheduledExecutorService scheduledExecutorService;

    /**
     * 信号量来控制，连接任务最多支持的并发数
     */
    private Semaphore semaphore;

    /**
     * 显示mt4账户管理器是否启动完成
     */
    private Boolean launchOn = false;


    public LeaderApiTradersAdmin(FollowTraderService eaTraderService, FollowBrokeServerService eaBrokerService, Ks ks, AdminClient adminClient, IKafkaProducer<String, Object> kafkaProducer, ScheduledExecutorService scheduledExecutorService) {
        this.followTraderService = eaTraderService;
        this.followBrokeServerService = eaBrokerService;
        this.ks = ks;
        this.adminClient = adminClient;
        this.kafkaProducer = kafkaProducer;
        this.semaphore = new Semaphore(10);
        this.scheduledExecutorService = scheduledExecutorService;
        this.followPlatformService=followPlatformService;
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
            scheduledExecutorService.submit(() -> {
                try {
                    //这里需要删除主题，因为有可能主题的分区配置变化了
                    ListTopicsResult listTopicsResult = adminClient.listTopics();
                    Collection<String> hostTopicNames = listTopicsResult.names().get();

                    Collection<String> topics = Arrays.asList(KafkaTopicPrefixSuffix.TENANT + KafkaTopicUtil.leaderAccountTopic(leader), KafkaTopicPrefixSuffix.TENANT + KafkaTopicUtil.leaderTradeSignalTopic(leader));
                    hostTopicNames.retainAll(topics);
                    if (!hostTopicNames.isEmpty()) {
                        DeleteTopicsResult deleteTopicsResult = adminClient.deleteTopics(hostTopicNames);
                        Map<String, KafkaFuture<Void>> map = deleteTopicsResult.topicNameValues();
                        map.values().forEach(value -> {
                            try {
                                value.get();
                            } catch (Exception ignored) {
                            }
                        });
                    }
                    ConCodeEnum conCodeEnum = addTrader(leader);
                    LeaderApiTrader leaderApiTrader = leader4ApiTraderConcurrentHashMap.get(leader.getId().toString());
                    if (conCodeEnum != ConCodeEnum.SUCCESS && !leader.getStatus().equals(TraderStatusEnum.ERROR.getValue())) {
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
    }

    /**
     * 指定mt4账户并发连接mt4服务端，连接成功后创建主题。
     */
    @Override
    public void startUp(List<FollowTraderEntity> list){
        for (FollowTraderEntity leader : list) {
            scheduledExecutorService.submit(() -> {
                try {
                    //这里需要删除主题，因为有可能主题的分区配置变化了
                    ListTopicsResult listTopicsResult = adminClient.listTopics();
                    Collection<String> hostTopicNames = listTopicsResult.names().get();

                    Collection<String> topics = Arrays.asList(KafkaTopicPrefixSuffix.TENANT + KafkaTopicUtil.leaderAccountTopic(leader), KafkaTopicPrefixSuffix.TENANT + KafkaTopicUtil.leaderTradeSignalTopic(leader));
                    hostTopicNames.retainAll(topics);
                    if (!hostTopicNames.isEmpty()) {
                        DeleteTopicsResult deleteTopicsResult = adminClient.deleteTopics(hostTopicNames);
                        Map<String, KafkaFuture<Void>> map = deleteTopicsResult.topicNameValues();
                        map.values().forEach(value -> {
                            try {
                                value.get();
                            } catch (Exception ignored) {
                            }
                        });
                    }
                    ConCodeEnum conCodeEnum = addTrader(leader);
                    LeaderApiTrader leaderApiTrader = leader4ApiTraderConcurrentHashMap.get(leader.getId().toString());
                    if (conCodeEnum != ConCodeEnum.SUCCESS && !leader.getStatus().equals(TraderStatusEnum.ERROR.getValue())) {
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
        //优先查看平台默认节点
        FollowPlatformEntity followPlatformServiceOne = followPlatformService.getOne(new LambdaQueryWrapper<FollowPlatformEntity>().eq(FollowPlatformEntity::getServer, leader.getPlatform()));
        if (ObjectUtil.isNotEmpty(followPlatformServiceOne.getServerNode())) {
            //处理节点格式
            String[] split = followPlatformServiceOne.getServerNode().split(":");
            conCodeEnum = connectTrader(leader, conCodeEnum, split[0], Integer.valueOf(split[1]),kafkaProducer);
            if (conCodeEnum == ConCodeEnum.TRADE_NOT_ALLOWED) {
                //循环连接
                List<FollowBrokeServerEntity> serverEntityList = followBrokeServerService.listByServerName(leader.getPlatform());
                for (FollowBrokeServerEntity address : serverEntityList) {
                    // 如果当前状态已不是TRADE_NOT_ALLOWED，则跳出循环
                    conCodeEnum = connectTrader(leader, conCodeEnum, address.getServerNode(), Integer.valueOf(address.getServerPort()),kafkaProducer);
                    if (conCodeEnum != ConCodeEnum.TRADE_NOT_ALLOWED) {
                        break;
                    }
                }
            }
        }
        return conCodeEnum;
    }

    private ConCodeEnum connectTrader(FollowTraderEntity leader,ConCodeEnum conCodeEnum,String serverNode,Integer serverport, IKafkaProducer<String, Object> kafkaProducer) {
        try {
            LeaderApiTrader leaderApiTrader = new LeaderApiTrader(leader,kafkaProducer, serverNode, Integer.valueOf(serverport));
            ConnectionTask connectionTask = new ConnectionTask(leaderApiTrader, this.semaphore);
            FutureTask<ConnectionResult> submit = (FutureTask<ConnectionResult>) scheduledExecutorService.submit(connectionTask);
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
            } else {
                traderUpdateEn.setStatus(TraderStatusEnum.ERROR.getValue());
                traderUpdateEn.setStatusExtra("经纪商异常");
                conCodeEnum = ConCodeEnum.TRADE_NOT_ALLOWED;
            }
        } catch (Exception e) {
            log.info("连接异常" + e);
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

        ConnectionTask(LeaderApiTrader leaderApiTrader, Semaphore semaphore) {
            this.leaderApiTrader = leaderApiTrader;
            this.semaphore = semaphore;
        }

        @Override
        public ConnectionResult call() throws InterruptedException {
            FollowTraderEntity leader = this.leaderApiTrader.getTrader();
            boolean aq = Boolean.FALSE;
            try {
                semaphore.acquire();
                aq = Boolean.TRUE;
                this.leaderApiTrader.connect2Broker();
            } catch (Exception e) {
                log.error("[MT4喊单者{}-{}-{}]连接服务器失败，失败原因：[{}]", leader.getId(), leader.getAccount(), leader.getServerName(), e.getClass().getSimpleName() + e.getMessage());
                return new ConnectionResult(this.leaderApiTrader, ConCodeEnum.PASSWORD_FAILURE);
            } finally {
                if (aq) {
                    semaphore.release();
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


}

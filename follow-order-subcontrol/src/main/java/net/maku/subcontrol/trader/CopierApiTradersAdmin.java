package net.maku.subcontrol.trader;

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
import net.maku.followcom.service.FollowTraderSubscribeService;
import net.maku.subcontrol.constants.FollowConstant;
import net.maku.subcontrol.constants.KafkaTopicPrefixSuffix;
import net.maku.subcontrol.util.KafkaTopicUtil;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.common.KafkaFuture;
import org.springframework.stereotype.Component;

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

    /**
     * 信号量来控制，连接任务最多支持的并发数
     */
    private Semaphore semaphore;

    /**
     * 显示mt4账户管理器是否启动完成
     */
    private Boolean launchOn = false;

    public CopierApiTradersAdmin(FollowTraderService followTraderService, FollowBrokeServerService followBrokeServerService, FollowTraderSubscribeService followTraderSubscribeService, Ks ks, AdminClient adminClient, IKafkaProducer<String, Object> kafkaProducer, ScheduledExecutorService scheduledExecutorService) {
        this.followTraderService = followTraderService;
        this.followBrokeServerService = followBrokeServerService;
        this.followTraderSubscribeService = followTraderSubscribeService;
        this.ks = ks;
        this.adminClient = adminClient;
        this.kafkaProducer = kafkaProducer;
        this.semaphore = new Semaphore(10);
        this.scheduledExecutorService = scheduledExecutorService;
    }

    /**
     * 所有的mt4账户并发连接NJ4X服务端，连接成功后创建主题。
     *
     * @throws Exception 异常
     */
    @Override
    public void startUp() throws Exception {
        //启动当前VPS下的账号
        List<FollowTraderEntity> copiers = followTraderService.list(Wrappers.<FollowTraderEntity>lambdaQuery().eq(FollowTraderEntity::getIpAddr, FollowConstant.LOCAL_HOST).eq(FollowTraderEntity::getType, TraderTypeEnum.SLAVE_REAL.getType()).eq(FollowTraderEntity::getDeleted, CloseOrOpenEnum.CLOSE.getValue()));
        log.info("{}个MT4跟单账户开始连接经纪商服务器", copiers.size());
        //倒计时门栓，大小为mt4账户个数，添加完成一个mt4账户倒计时门栓就减1，直到所有都添加完成。
        CountDownLatch countDownLatch = new CountDownLatch(copiers.size());
        for (FollowTraderEntity copier : copiers) {
            scheduledExecutorService.submit(() -> {
                try {
                    ListTopicsResult listTopicsResult = adminClient.listTopics();
                    Collection<String> hostTopicNames = listTopicsResult.names().get();
                    List<String> topics = Collections.singletonList(KafkaTopicPrefixSuffix.TENANT + KafkaTopicUtil.copierAccountTopic(copier));
                    hostTopicNames.retainAll(topics);
                    if (!hostTopicNames.isEmpty()) {
                        //这里需要删除主题，因为有可能主题的分区配置变化了
                        DeleteTopicsResult deleteTopicsResult = adminClient.deleteTopics(hostTopicNames);
                        Map<String, KafkaFuture<Void>> map = deleteTopicsResult.topicNameValues();
                        map.values().forEach(value -> {
                            try {
                                value.get();
                            } catch (Exception e) {
                                log.error("", e);
                            }
                        });
                    }
                    ConCodeEnum conCodeEnum = addTrader(copier, kafkaProducer);
                    CopierApiTrader copierApiTrader = copierApiTraderConcurrentHashMap.get(copier.getId().toString());
                    if (conCodeEnum != ConCodeEnum.SUCCESS && !copier.getStatus().equals(TraderStatusEnum.ERROR.getValue())) {
                        log.error("跟单者:[{}-{}-{}]启动失败，请校验", copier.getId(), copier.getAccount(), copier.getServerName());
                    } else {
                        log.info("跟单者:[{}-{}-{}-{}]在[{}:{}]启动成功", copier.getId(), copier.getAccount(), copier.getServerName(), copier.getPassword(), copierApiTrader.quoteClient.Host, copierApiTrader.quoteClient.Port);
                    }

                    copierApiTrader.startTrade();
                } catch (Exception e) {
                    log.error("", e);
                } finally {
                    countDownLatch.countDown();
                }

            });
        }
        //需要所有addLeader()函数执行完后才可以
        countDownLatch.await();
        log.debug("所有的mt4跟单者结束连接服务器");
        this.launchOn = true;
    }

    @Override
    public boolean removeTrader(String id) {
        CopierApiTrader copierApiTrader = this.copierApiTraderConcurrentHashMap.get(id);

        boolean b = Boolean.TRUE;
        if (copierApiTrader != null) {
            b = copierApiTrader.stopTrade();
            if (b) {
                this.copierApiTraderConcurrentHashMap.remove(id);
            }
        }
        return b;
    }

    @Override
    public ConCodeEnum addTrader(FollowTraderEntity copier, IKafkaProducer<String, Object> kafkaProducer) {

        List<NewTopic> newTopics = List.of(new NewTopic(KafkaTopicPrefixSuffix.TENANT + KafkaTopicUtil.copierAccountTopic(copier), ks.getPartition(), ks.getReplication()));
        CreateTopicsResult createTopicsResult = adminClient.createTopics(newTopics);
        try {
            createTopicsResult.all().get();
            log.debug("跟单者[{}-{}-{}]创建主题[{}]成功", copier.getId(), copier.getAccount(), copier.getServerName(), newTopics);
        } catch (InterruptedException | ExecutionException e) {
            log.debug("跟单者[{}-{}-{}]创建主题[{}]失败，失败原因[{}]", copier.getId(), copier.getAccount(), copier.getServerName(), newTopics, e.getMessage());
        }
        Map<String, KafkaFuture<Void>> values = createTopicsResult.values();
        values.forEach((key, value) -> log.debug(key));

        List<FollowBrokeServerEntity> serverEntityList = followBrokeServerService.listByServerName(copier.getPlatform());
        ConCodeEnum conCodeEnum=ConCodeEnum.TRADE_NOT_ALLOWED;

        for (FollowBrokeServerEntity address : serverEntityList) {
            try {
                // 创建并提交连接任务
                CopierApiTrader copierApiTrader = new CopierApiTrader(copier, kafkaProducer, address.getServerNode(), Integer.valueOf(address.getServerPort()));
                ConnectionTask connectionTask = new ConnectionTask(copierApiTrader, this.semaphore);
                FutureTask<ConnectionResult> submit = (FutureTask<ConnectionResult>) scheduledExecutorService.submit(connectionTask);
                ConnectionResult result = submit.get();
                // 判断连接结果是否成功
                if (result.code == ConCodeEnum.SUCCESS) {
                    copierApiTrader.setTrader(copier);
                    this.copierApiTraderConcurrentHashMap.put(String.valueOf(copier.getId()), copierApiTrader);

                    FollowTraderEntity traderUpdate = new FollowTraderEntity();
                    traderUpdate.setId(copier.getId());

                    // 根据账户模式更新状态
                    if (copierApiTrader.quoteClient.AccountMode() == 1) {
                        traderUpdate.setStatus(TraderStatusEnum.ERROR.getValue());
                        traderUpdate.setStatusExtra("经纪商异常");
                        conCodeEnum = ConCodeEnum.TRADE_NOT_ALLOWED;
                    } else {
                        traderUpdate.setStatus(TraderStatusEnum.NORMAL.getValue());
                        traderUpdate.setStatusExtra("启动成功");
                        conCodeEnum = ConCodeEnum.SUCCESS;
                    }

                    // 更新数据库中的跟单者信息
                    followTraderService.updateById(traderUpdate);

                } else {
                    // 如果连接未成功，也将 trader 保存到缓存中
                    this.copierApiTraderConcurrentHashMap.put(String.valueOf(copier.getId()), copierApiTrader);
                }

            } catch (Exception e) {
                log.error("", e);
                log.error("MT4跟单者账户[{}-{}-{}]连接报错，错误信息{}", copier.getId(), copier.getAccount(), address.getServerName(), e.getClass().getSimpleName());
            }
            // 如果当前状态已不是TRADE_NOT_ALLOWED，则跳出循环
            if (conCodeEnum != ConCodeEnum.TRADE_NOT_ALLOWED) {
                break;
            }
        }
        return conCodeEnum;
    }

    /**
     * 执行喊单者的连接操作做连接操作
     */
    private static class ConnectionTask implements Callable<ConnectionResult> {
        private final CopierApiTrader copierApiTrader;

        //信号量就是简单的获取一个就减少一个，多次获取会获取到多个。线程互斥的可重入性在此不适用。
        private final Semaphore semaphore;

        FollowTraderEntity copier;

        ConnectionTask(CopierApiTrader copierApiTrader, Semaphore semaphore) {
            this.copierApiTrader = copierApiTrader;
            this.semaphore = semaphore;
            copier = this.copierApiTrader.getTrader();
        }

        @Override
        public ConnectionResult call() {
            long start, end;
            start = System.currentTimeMillis();
            boolean aq = Boolean.FALSE;
            try {
                semaphore.acquire();
                aq = Boolean.TRUE;
                this.copierApiTrader.connect2Broker();
                end = System.currentTimeMillis();
            } catch (Exception e) {
                log.error("", e);
                end = System.currentTimeMillis();
                log.error("[MT4跟单者：{}-{}-{}]连接服务器失败，耗时:{}秒，失败原因：[{}]", copier.getId(), copier.getAccount(), copier.getServerName(), (end - start) / 1000.0, e.getClass().getSimpleName());
                return new ConnectionResult(this.copierApiTrader, ConCodeEnum.PASSWORD_FAILURE);
            } finally {
                if (aq) {
                    semaphore.release();
                }
            }
            log.info("[MT4跟单者：{}-{}-{}-{}]在[服务器{}:{}]连接成功，耗时：{}秒", copier.getId(), copier.getAccount(), copier.getServerName(), copier.getPassword(), copierApiTrader.quoteClient.Host, copierApiTrader.quoteClient.Port, (end - start) / 1000.0);
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

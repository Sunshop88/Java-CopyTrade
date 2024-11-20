package net.maku.subcontrol.callable;

import cn.hutool.core.util.ObjectUtil;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.enums.AcEnum;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.followcom.service.FollowBrokeServerService;
import net.maku.followcom.service.FollowTraderService;
import net.maku.followcom.service.FollowTraderSubscribeService;
import net.maku.followcom.service.impl.FollowBrokeServerServiceImpl;
import net.maku.followcom.service.impl.FollowTraderServiceImpl;
import net.maku.followcom.service.impl.FollowTraderSubscribeServiceImpl;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.subcontrol.trader.strategy.IOperationStrategy;
import online.mtapi.mt4.Exception.ConnectException;
import online.mtapi.mt4.Exception.TimeoutException;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author samson bruce
 * @since 2023-10-12
 */
@Slf4j
public class AbstractKafkaMessageCallback {

    protected Map<AcEnum, IOperationStrategy> operationStrategy = new HashMap<>(10);

    protected FollowBrokeServerService eaServerService;
    protected FollowTraderSubscribeService eaMasterSlaveService;
    protected FollowTraderService eaTraderService;
    protected ScheduledExecutorService scheduledExecutorService;
    protected RedisUtil redisUtil;
    /**
     * 跟单者开仓映射redis缓存
     */
    protected String mapKey;
    /**
     * 处理循环开仓、强制开仓时候的互斥问题。
     */
    protected ReentrantReadWriteLock reentrantReadWriteLock;

    public AbstractKafkaMessageCallback() {
        this.eaServerService = SpringContextUtils.getBean(FollowBrokeServerServiceImpl.class);
        this.scheduledExecutorService = ThreadPoolUtils.getScheduledExecute();
        this.eaMasterSlaveService = SpringContextUtils.getBean(FollowTraderSubscribeServiceImpl.class);
        this.eaTraderService = SpringContextUtils.getBean(FollowTraderServiceImpl.class);
        this.redisUtil = SpringContextUtils.getBean(RedisUtil.class);
        this.reentrantReadWriteLock = new ReentrantReadWriteLock();
    }


    /**
     * kafka 收到的信号数据
     *
     * @param consumerRecord 信号 key value
     */
    protected void tradeOperation(ConsumerRecord<String, Object> consumerRecord, Map<String, Object> status) throws ConnectException, TimeoutException {
        String key = consumerRecord.key();
        int indexOf = key.indexOf("#");
        AcEnum ac;
        try {
            ac = AcEnum.valueOf(indexOf == -1 ? key.toUpperCase() : key.substring(0, indexOf));
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            ac = AcEnum.OTHERS;
        }
        if (ObjectUtil.isNotEmpty(status)){
            if (status.get("followStatus").equals(CloseOrOpenEnum.CLOSE.getValue())){
                log.info("未开通跟单状态");
                return;
            }
            if (ac==AcEnum.NEW){
                if (status.get("followOpen").equals(CloseOrOpenEnum.CLOSE.getValue())) {
                    log.info("未开通跟单下单状态");
                    return;
                }
            }else if (ac==AcEnum.CLOSED){
                if (status.get("followClose").equals(CloseOrOpenEnum.CLOSE.getValue())) {
                    log.info("未开通跟单平仓状态");
                    return;
                }
            }
        }

        IOperationStrategy iOperationStrategy = operationStrategy.get(ac) == null ? operationStrategy.get(AcEnum.OTHERS) : operationStrategy.get(ac);
        log.info("跟单时间{}",consumerRecord.key());
        iOperationStrategy.operate(consumerRecord, 2);
    }
}

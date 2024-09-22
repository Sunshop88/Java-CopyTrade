package net.maku.subcontrol.trader;

import com.cld.message.pubsub.kafka.IKafkaProducer;
import com.cld.message.pubsub.kafka.properties.Ks;
import lombok.Data;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.enums.ConCodeEnum;
import net.maku.followcom.service.FollowBrokeServerService;
import net.maku.followcom.service.FollowTraderService;
import net.maku.followcom.service.FollowTraderSubscribeService;
import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

import java.util.concurrent.*;

/**
 * mtapi MT4管理基础类
 */
@Data
public abstract class AbstractApiTradersAdmin {
    protected ConcurrentHashMap<String, LeaderApiTrader> leader4ApiTraderConcurrentHashMap;
    protected ConcurrentHashMap<String, CopierApiTrader> copierApiTraderConcurrentHashMap;

    protected FollowBrokeServerService followBrokeServerService;
    protected FollowTraderService followTraderService;
    protected FollowTraderSubscribeService followTraderSubscribeService;
    protected IKafkaProducer<String, Object> kafkaProducer;
    protected AdminClient adminClient;
    protected Ks ks;
    protected ScheduledExecutorService scheduledExecutorService;


    public AbstractApiTradersAdmin() {
        this.leader4ApiTraderConcurrentHashMap = new ConcurrentHashMap<>();
        this.copierApiTraderConcurrentHashMap = new ConcurrentHashMap<>();
    }

    protected int traderCount = 0;
    protected ExecutorService kafkaConsumerCachedThreadPool = new ThreadPoolExecutor(0, Integer.MAX_VALUE,
            60L, TimeUnit.SECONDS,
            new SynchronousQueue<Runnable>(),
            new CustomizableThreadFactory("MT-Kafka消费线程-"));

    /**
     * 启动
     *
     * @throws Exception 异常
     */
    public abstract void startUp() throws Exception;

    /**
     * 删除账号
     *
     * @param id mt跟单账户的id
     * @return 删除成功-true 失败-false
     */
    public abstract boolean removeTrader(String id);

    /**
     * 绑定账号
     *
     * @param trader        账号信息
     * @param kafkaProducer kafka生产者
     * @return ConCodeEnum 添加结果
     */
    public abstract ConCodeEnum addTrader(FollowTraderEntity trader, IKafkaProducer<String, Object> kafkaProducer);

}

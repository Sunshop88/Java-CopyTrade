package net.maku.subcontrol.trader;

import com.cld.message.pubsub.kafka.IKafkaProducer;
import com.cld.message.pubsub.kafka.impl.CldKafkaConsumer;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.service.FollowBrokeServerService;
import net.maku.followcom.service.FollowTraderService;
import net.maku.followcom.service.FollowTraderSubscribeService;
import net.maku.followcom.service.impl.FollowBrokeServerServiceImpl;
import net.maku.followcom.service.impl.FollowTraderServiceImpl;
import net.maku.followcom.service.impl.FollowTraderSubscribeServiceImpl;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.mascontrol.service.FollowPlatformService;
import net.maku.mascontrol.service.impl.FollowPlatformServiceImpl;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.regex.Pattern;


@Data
@Slf4j
public class ApiTrader {
    public static String regex = "(#[A-Za-z0-9]+){1,2}#FO_(AUTO|REPAIR)";
    public static String EMPTY = "empty";
    protected boolean initPrefixSuffix = Boolean.FALSE;
    public static String EURUSD = "eurusd";
    protected String eurusd = "EURUSD";
    protected Pattern pattern = Pattern.compile(eurusd, Pattern.CASE_INSENSITIVE);
    @Getter
    protected IKafkaProducer<String, Object> kafkaProducer;
    protected CldKafkaConsumer<String, Object> cldKafkaConsumer;
    @Getter
    protected String prefixSuffix;
    @Getter
    protected final List<String> prefixSuffixList = new LinkedList<>();
    @Getter
    protected Map<String, String> correctSymbolMap = new HashMap<>();
    @Getter
    protected FollowTraderService traderService;
    protected FollowBrokeServerService followBrokeServerService;

    protected ScheduledExecutorService scheduledExecutorService;
    protected FollowTraderSubscribeService followTraderSubscribeService;
    protected FollowPlatformService followPlatformService;

    void initService() {
        this.traderService = SpringContextUtils.getBean(FollowTraderServiceImpl.class);
        this.followBrokeServerService=SpringContextUtils.getBean(FollowBrokeServerServiceImpl.class);
        this.scheduledExecutorService = SpringContextUtils.getBean("scheduledExecutorService", ScheduledThreadPoolExecutor.class);
        this.followTraderSubscribeService= SpringContextUtils.getBean(FollowTraderSubscribeServiceImpl.class);
        this.followPlatformService=SpringContextUtils.getBean(FollowPlatformServiceImpl.class);
    }

}

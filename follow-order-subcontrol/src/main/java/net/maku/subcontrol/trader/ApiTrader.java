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
import net.maku.followcom.service.FollowPlatformService;
import net.maku.followcom.service.impl.FollowPlatformServiceImpl;
import net.maku.framework.common.utils.ThreadPoolUtils;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
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
    protected CldKafkaConsumer<String, Object> cldKafkaConsumer;
    @Getter
    protected String prefixSuffix;
    @Getter
    protected final List<String> prefixSuffixList = new LinkedList<>();
    @Getter
    protected Map<String, String> correctSymbolMap = new HashMap<>();
    @Getter
    protected final FollowTraderService traderService=SpringContextUtils.getBean(FollowTraderServiceImpl.class);;
    protected final FollowBrokeServerService followBrokeServerService=SpringContextUtils.getBean(FollowBrokeServerServiceImpl.class);;
    protected final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(100);

    protected final FollowTraderSubscribeService followTraderSubscribeService=SpringContextUtils.getBean(FollowTraderSubscribeServiceImpl.class);;
    protected final FollowPlatformService followPlatformService=SpringContextUtils.getBean(FollowPlatformServiceImpl.class);;

}

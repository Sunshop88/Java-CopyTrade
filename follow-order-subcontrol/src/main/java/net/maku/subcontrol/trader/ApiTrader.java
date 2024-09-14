package net.maku.subcontrol.trader;

import com.cld.message.pubsub.kafka.IKafkaProducer;
import com.cld.message.pubsub.kafka.impl.CldKafkaConsumer;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.service.FollowTraderService;
import net.maku.framework.common.utils.SpringContextUtils;

import java.util.*;
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

    void initService() {
        this.traderService = SpringContextUtils.getBean(FollowTraderService.class);
    }

}

package net.maku.subcontrol.constants;

/**
 * KAFKA主题相关的前后缀
 */
public interface KafkaTopicPrefixSuffix {
    /**
     * 明确是那个租户
     * @return 租户通用名字
     */
    default String tenant() {
        return "ARBITRAGE_";
    }

    /**
     * 套利策略，作为kafka租户区分标志
     */
    String TENANT = "ARBITRAGE_";

    /**
     * 喊单者交易信号发送消息前缀
     */
    String SUBSCRIPTION = "SUBSCRIPTION_";

    /**
     * 向喊单者账号发送消息主题前缀
     */
    String LEADER = "LEADER_";

    /**
     * 向跟单者账号发送消息主题前缀
     */
    String FOLLOWER = "FOLLOWER_";

}

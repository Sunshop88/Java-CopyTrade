package net.maku.subcontrol.util;


import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.subcontrol.constants.KafkaTopicPrefixSuffix;

/**
 * 统一生成KAFKA主题的工具类，需要注意的问题有，KAFKA主题是不允许一些非法字符的，比如空格……
 *
 * @author samson bruce
 */
public class KafkaTopicUtil {

    /**
     * 将主题中，可能存在的KAFKA不允许的非法字符删除
     *
     * @param topic 主题
     * @return 删除非法字符后的主题
     */
    static String replaceInvalidChar(String topic) {
        return topic.replace(" ", "");
    }

    /**
     * 返回MT4喊单者主题
     *
     * @param leader MT账号信息
     * @return String
     */
    public static String leaderAccountTopic(FollowTraderEntity leader) {
        return replaceInvalidChar(KafkaTopicPrefixSuffix.LEADER + leader.getId().toString());
    }

    /**
     * 返回MT4喊单者主题
     *
     * @param id MT账号主键
     * @return String
     */
    public static String leaderAccountTopic(String id) {
        return replaceInvalidChar(KafkaTopicPrefixSuffix.LEADER + id);
    }

    /**
     * 返回MT4喊单者发送交易信号主题
     *
     * @param leader MT账号信息
     * @return String
     */
    public static String leaderTradeSignalTopic(FollowTraderEntity leader) {
        return replaceInvalidChar(KafkaTopicPrefixSuffix.SUBSCRIPTION + leader.getId());
    }


    /**
     * 返回MT4喊单者发送交易信号主题
     *
     * @param id MT4账号主键
     * @return String
     */
    public static String leaderTradeSignalTopic(String id) {
        return replaceInvalidChar(KafkaTopicPrefixSuffix.SUBSCRIPTION + id);
    }

    /**
     * 返回MT4跟单者主题
     *
     * @param copier MT账号信息
     * @return String
     */
    public static String copierAccountTopic(FollowTraderEntity copier) {
        return replaceInvalidChar(KafkaTopicPrefixSuffix.FOLLOWER + copier.getId().toString());
    }

    /**
     * 返回MT4跟单者主题
     *
     * @param id 主键
     * @return String
     */
    public static String copierAccountTopic(String id) {
        return replaceInvalidChar(KafkaTopicPrefixSuffix.FOLLOWER + id);
    }

}

package net.maku.framework.common.constant;

/**
 * 常量
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
public interface Constant {
    /**
     * 根节点标识
     */
    Long ROOT = 0L;
    /**
     * 当前页码
     */
    String PAGE = "page";
    /**
     * 数据权限
     */
    String DATA_SCOPE = "dataScope";
    /**
     * 超级管理员
     */
    Integer SUPER_ADMIN = 1;
    /**
     * 禁用
     */
    Integer DISABLE = 0;
    /**
     * 启用
     */
    Integer ENABLE = 1;
    /**
     * 失败
     */
    Integer FAIL = 0;
    /**
     * 成功
     */
    Integer SUCCESS = 1;
    /**
     * OK
     */
    String OK = "OK";

    /**
     * pgsql的driver
     */
    String PGSQL_DRIVER = "org.postgresql.Driver";

    /**
     * 品种规格
     */
    String SYMBOL_SPECIFICATION="symbol:specification:";

    /**
     * 订单信息
     */
    String TRADER_ORDER="trader:order:";

    /**
     * 账户信息
     */
    String TRADER_USER="trader:user:";

    /**
     * 账户下单标识
     */
    String TRADER_SEND="trader:send:";

    /**
     * 账户平仓标识
     */
    String TRADER_CLOSE="trader:close:";

    /**
     * 账户持仓订单缓存
     */
    String TRADER_ACTIVE="trader:active:";

    /**
     * 账户漏单临时缓存
     */
    String TRADER_TEMPORARILY_REPAIR="trader:temporarily:repair";

    /**
     * 账号平台信息
     */
    String TRADER_PLATFORM="trader:platform:";

    /**
     * 用户的vps
     */
    String SYSTEM_VPS_USER="system:vps:user:";

    /**
     * vps节点速度
     */
    String VPS_NODE_SPEED="vps:node:speed:";

    /**
     * 跟单关系状态开关
     */
    String FOLLOW_MASTER_SLAVE="follow:master:slave:";

    /**
     * 跟单关系记录
     */
    String FOLLOW_SUB_TRADER="follow:sub:trader:";

    /**
     * 订单关系记录
     */
    String FOLLOW_SUB_ORDER="follow:sub:order:";

    /**
     * 跟单下单情况缓存
     */
    String FOLLOW_REPAIR_SEND="follow:repair:send:";

    /**
     * 跟单平仓情况缓存
     */
    String FOLLOW_REPAIR_CLOSE="follow:repair:close:";

    /**
     * 监听幂等校验
     */
    String FOLLOW_ON_EVEN="follow:on:even:";

    /**
     *
     */
    String STATISTICS_SYMBOL_INFO="statistics:symbol:info";
   //补单的
    public static final String     REPAIR_SEND="repair:send:";
    //平仓的
    public static final String     REPAIR_CLOSE="repair:close:";
   //全局参数配置
    public static final String     SYSTEM_PARAM_LOTS_MAX="system:params";
    //最大手数
    public static final String     LOTS_MAX="max_lots";
    //飞书通知签名
    public static final String      FS_NOTICE_SECRET="secret";
    //飞书通知url
    public static final String        FS_NOTICE_URL="url";
    //
    public static final String        NOTICE_MESSAGE_BUY="漏开";
    public static final String        NOTICE_MESSAGE_SELL="漏平";
    String FOLLOW_RELATION_KEY = "follow:relation:";
}
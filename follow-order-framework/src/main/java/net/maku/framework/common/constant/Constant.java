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
     * 品种匹配数据
     */
    String TRADER_VARIETY="trader:variety:";

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
}
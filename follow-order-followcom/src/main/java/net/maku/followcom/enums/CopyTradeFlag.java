package net.maku.followcom.enums;

/**
 * @author samson bruce
 */
public interface CopyTradeFlag {
    /**
     * 成功收到开仓信号
     */
    Integer OS0 =0;
    /**
     * 跟随开仓成功
     */
    Integer OS = 1;

    /**
     * 循环开仓成功
     */
    Integer COS = 2;

    /**
     * 循环开仓成功
     */
    Integer FOS = 3;

    /**
     * 平仓成功
     */
    Integer  POS= 6;
    /**
     * 循环平仓成功
     */
    Integer  CPOS= 7;

    /**
     * 循环开仓失败
     */
    Integer COF = -20;

    /**
     * 强制开仓失败
     */
    Integer FOF = -30;

    /**
     * 跟随开仓失败，需要循环开仓
     */
    Integer OF1 = -1;

    /**
     * 跟随开仓失败，订阅关系不存在
     */
    Integer OF2 = -2;
    /**
     * 订阅关系系统暂停开仓
     */
    Integer OF3 = -3;
    /**
     * 订阅关系暂停开仓
     */
    Integer OF4 = -4;
    /**
     * 订阅关系为状态不是NORMAL
     */
    Integer OF5 = -5;
    /**
     * 喊单者账号欠费
     */
    Integer OF6 = -6;
    /**
     * 跟单者账号欠费
     */
    Integer OF7 = -7;
    /**
     * 没有订阅该COMMENT
     */
    Integer OF8 = -8;
    /**
     * 不订阅该COMMENT
     */
    Integer OF9 = -9;
    /**
     * 部分平仓触发的开仓，来源订单跟单者未跟上。
     */
    Integer OF10 = -10;

    /**
     * 平仓失败
     */
    Integer  POF= -11;
}

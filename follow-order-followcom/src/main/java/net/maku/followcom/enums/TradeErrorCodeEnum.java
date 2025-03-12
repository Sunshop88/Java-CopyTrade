package net.maku.followcom.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum TradeErrorCodeEnum implements IEnum<String> {

    INVALID_VOLUME("Invalid volume", "手数错误"),
    TRADE_DISABLED("Trade is disabled", "市场关闭"),
    NOT_EXIST("not exist", "品种异常"),
    NOT_ENOUGH_MONEY("Not enough money", "资金不足"),
    MAX_VOLUME_LIMIT_EXCEEDED("超过最大手数限制", "超过最大手数限制"),
    CONNECTION_LOST("Connection lost during order execution", "断开连接"),
    NO_REPLY_FROM_SERVER("No reply from server in 30000 ms", "请求超时"),
    MARKET_CLOSED("Market is closed", "市场关闭"),
    QUOTE_EXCEPTION("because \"quoteEventArgs\" is null", "报价异常"),
    OFF_QUOTES("Off quotes", "请求过多"),
    NOT_CONNECTED("Not connected in 30000 ms", "连接超时"),
    NO_CONNECTION("Cannot send order because no connection with server", "账号未连接"),
    QUOTE_CLIENT_NULL("because \"quoteClient.OrderClient\" is null", "账号未连接"),
    NO_SYMBOL_SPECIFICATION("主账号标准品种未配置", "主账号标准品种未配置"),
    SYMBOL_NOT_RIGHT("获取报价失败, 品种不正确", "品种不正确"),
    LOGIN_ERROR("登录异常", "登录异常"),
    SEVICE_ERRROR("服务器异常", "服务器异常"),
    LOTS_NUM_ERROR("超过最大手数限制", "超过最大手数限制");



    /**
     * 枚举对象的值
     * EnumValue注解决定实体对象写入数据中使用的值
     * JsonValue后端将实体对象以json格式数据返回时使用的值
     */
    @EnumValue
    @JsonValue
    private String value;

    /**
     * 该枚举值的描述
     */
    private String description;

    /**
     * @param val  枚举对象的常量值
     * @param desc 枚举对象的描述
     */
    TradeErrorCodeEnum(String val, String desc) {
        this.value = val;
        this.description = desc;
    }

    @Override
    public String getValue() {
        return this.value;
    }

    public static TradeErrorCodeEnum getDescription(String remark) {
        TradeErrorCodeEnum[] enums = TradeErrorCodeEnum.values();
        for (TradeErrorCodeEnum anEnum : enums) {
            if (remark.contains(anEnum.getValue())) {
                return anEnum;
            }
        }
        return null;
    }
}

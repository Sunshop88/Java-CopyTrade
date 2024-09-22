package net.maku.followcom.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * @author 休克柏
 */
@Getter
public enum AcEnum implements IEnum<String> {
    /**
     * 跟单者开仓
     */
    MO("MO", "喊单者新开订单"),
    MM("MM", "喊单者修改订单"),
    MD("MD", "喊单者删除订单"),
    MC("MC", "喊单者关闭订单"),
    MH("MC", "喊单者同步历史持仓订单"),
    SA("SA", "同步持仓订单"),
    SH("SH", "跟单同步历史持仓订单"),

    SO("SO", "跟单者新开订单"),
    SM("SM", "跟单者修改订单"),
    SD("SD", "跟单者删除订单"),
    SC("SC", "跟单者关闭订单"),

    FC("FC", "跟单者平仓失败"),
    IA("IA", "判断持仓"),
    CA("CA", "跟单者平掉持仓"),

    CSC("CSC", "订阅量"),

    NEW("NEW", "新开订单"),
    MODIFIED("MODIFIED", "修改订单"),
    DELETED("DELETED", "删除订单"),
    CLOSED("CLOSED", "关闭订单"),
    SYMBOL("SYMBOL", "品种行情"),
    OTHERS("OTHERS", "其他");

    /**
     * 键值
     */
    String key;

    /**
     * EnumValue注解决定实体对象写入数据中使用的值,
     * JsonValue后端将实体对象以json格式数据返回时使用的值
     */
    @EnumValue
    @JsonValue
    private final String value;

    AcEnum(String key, String value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }
}

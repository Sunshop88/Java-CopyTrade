package net.maku.followcom.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * mt4 mt5账号连接经纪商的时候反馈的结果
 * 使用枚举类型比直接使用整形的好处是，在传参时候使用枚举变量，代码更具有易读性。
 * mybatis-plus官方文档https://mp.baomidou.com/guide/enum.html
 * <p>
 * 注意：对于系统中的类型字段类型尽量选着varchar，这样便于解决业务上需要in查询的时候，前端通过字符串中包含多个类型值（类型之间通过逗号隔开）传递到后端。
 */
@Getter
public enum ConCodeEnum implements IEnum<String> {
    /**
     * 连接成功
     * 密码错误
     * 网络错误
     * 跟单者连接使用的是观察者密码
     * 其他错误
     */
    SUCCESS("SUCCESS", "连接成功"),
    PASSWORD_FAILURE("PASSWORD_FAILURE", "密码错误"),
    NETWORK_FAILURE("NETWORK_FAILURE", "网络错误"),
    TRADE_NOT_ALLOWED("TRADE_NOT_ALLOWED", "经纪商不允许交易"),
    EXCEPTION("EXCEPTION", "异常"),
    ERROR("ERROR", "未知错误"),
    AGAIN("AGAIN", "重复提交");


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
    ConCodeEnum(String val, String desc) {
        this.value = val;
        this.description = desc;
    }

    @Override
    public String getValue() {
        return this.value;
    }
}

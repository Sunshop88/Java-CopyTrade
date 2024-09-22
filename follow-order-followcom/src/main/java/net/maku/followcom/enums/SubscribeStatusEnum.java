package net.maku.followcom.enums;


import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 订阅关系的状态
 * 正常-
 * 订阅过期-
 * 历史订阅-
 * 解绑-解绑后不能再次绑定，及时换一个注册用户绑定也不可以。
 */
@Getter
public enum SubscribeStatusEnum implements IEnum<String> {
    //
    NORMAL(0,"NORMAL", "正在订阅"),

    CANCEL(1,"CANCEL", "取消订阅");

    private Integer type;

    //枚举对象的整型值
    @EnumValue //@EnumValue注解决定实体对象写入数据中使用的值,
    @JsonValue //@JsonValue后端将实体对象以json格式数据返回时使用的值
    private String value;

    private String description;

    /**
     * @param val  枚举对象的常量值
     * @param desc 枚举对象的描述
     */
    SubscribeStatusEnum(Integer type,String val, String desc) {
        this.type= type;
        this.value = val;
        this.description = desc;
    }

    @Override
    public String getValue() {
        return this.value;
    }
}

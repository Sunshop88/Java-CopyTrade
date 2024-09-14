package net.maku.followcom.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CloseOrOpenEnum {
    /**
     * 关闭/未开启/未删除
     */
    CLOSE(0),
    /**
     * 打开/已开启/已删除
     */
    OPEN(1);


    private final Integer value;
}

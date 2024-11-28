package net.maku.followcom.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum FollowRemainderEnum {
    /**
     * 四舍五入
     */
    ROUND_IT_UP(0),
    /**
     * 手数取余
     */
    HAND_COUNT_SURPLUS(1);


    private final Integer value;
}

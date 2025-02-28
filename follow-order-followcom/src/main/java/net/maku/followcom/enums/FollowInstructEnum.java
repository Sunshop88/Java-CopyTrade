package net.maku.followcom.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum FollowInstructEnum {
    /**
     * 分配
     */
    DISTRIBUTION(0),
    /**
     * 复制
     */
    COPY(1);


    private final Integer value;

}

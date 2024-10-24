package net.maku.followcom.enums;


import lombok.Getter;


@Getter
public enum TraderCloseEnum{

    BUY(0),
    SELL(1),
    BUYANDSELL(2);
    private Integer type;

    TraderCloseEnum(Integer type) {
        this.type = type;
    }

}

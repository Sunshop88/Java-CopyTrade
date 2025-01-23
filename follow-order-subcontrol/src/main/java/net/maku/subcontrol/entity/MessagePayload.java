package net.maku.subcontrol.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import net.maku.followcom.vo.OrderCacheVO;

@Data
public class MessagePayload {
    @JsonProperty("Account")
    private Account account;

    @JsonProperty("Type")
    private int type=1;

    @JsonProperty("User")
    private long user;

    @JsonProperty("PlatformType")
    private String platformType;

    @JsonProperty("Action")
    private int action;

    @JsonProperty("Order")
    private OrderCacheVO order;
}

package net.maku.followcom.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import online.mtapi.mt4.Op;

import java.time.LocalDateTime;

/**
 * Author:  zsd
 * Date:  2024/11/19/周二 17:43
 */
@Data
@Schema(description = "redis缓存")
public class OrderCacheVO {
    @JsonProperty(value = "Id")
    private Long id;
    @JsonProperty(value = "Login")
    private Long login;
    @JsonProperty(value = "Ticket")
    private Integer ticket;
    @JsonProperty(value = "OpenTime")
    private LocalDateTime openTime;
    @JsonProperty(value = "CloseTime")
    private LocalDateTime closeTime;
    @JsonProperty(value = "Type")
    private Op type;
    @JsonProperty(value = "Lots")
    private Double Lots;
    @JsonProperty(value = "Symbol")
    private String symbol;
    @JsonProperty(value = "OpenPrice")
    private Double openPrice;
    @JsonProperty(value = "StopLoss")
    private Double stopLoss;
    @JsonProperty(value = "TakeProfit")
    private Double takeProfit;
    @JsonProperty(value = "ClosePrice")
    private Double closePrice;
    @JsonProperty(value = "MagicNumber")
    private Integer magicNumber;
    @JsonProperty(value = "Swap")
    private Double swap;
    @JsonProperty(value = "Commission")
    private Double commission;
    @JsonProperty(value = "Comment")
    private String comment;
    @JsonProperty(value = "Profit")
    private Double profit;
    @JsonProperty(value = "PlaceType")
    private String placeType;

}

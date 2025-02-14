package net.maku.followcom.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import online.mtapi.mt4.Op;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * Author:  zsd
 * Date:  2025/2/14/周五 16:04
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OpenOrderInfoVO implements Serializable {

    @JsonProperty(value = "Id")
    private Long id;
    //没有
    @JsonProperty(value = "Login")
    private Integer login;
    @JsonProperty(value = "Ticket")
    private Integer ticket;
    @JsonProperty(value = "OpenTime")
    private LocalDateTime openTime;
    @JsonProperty(value = "CloseTime")
    private LocalDateTime closeTime;
    @JsonProperty(value = "Type")
    private Op type;
    @JsonProperty(value = "Lots")
    private double lots;
    @JsonProperty(value = "Symbol")
    private String symbol;
    @JsonProperty(value = "OpenPrice")
    private double openPrice;
    @JsonProperty(value = "StopLoss")
    private double stopLoss;
    @JsonProperty(value = "TakeProfit")
    private double takeProfit;
    @JsonProperty(value = "ClosePrice")
    private double closePrice;
    @JsonProperty(value = "MagicNumber")
    private double magicNumber;
    @JsonProperty(value = "Swap")
    private double swap;
    @JsonProperty(value = "Commission")
    private double commission;
    @JsonProperty(value = "Comment")
    private String comment;
    @JsonProperty(value = "Profit")
    private double profit;
    @JsonProperty(value = "PlaceType")
    private String placeType;


}

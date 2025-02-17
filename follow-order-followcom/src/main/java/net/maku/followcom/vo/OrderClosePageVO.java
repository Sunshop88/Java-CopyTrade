package net.maku.followcom.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * Author:  zsd
 * Date:  2024/12/23/周一 15:44
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder

public class OrderClosePageVO implements Serializable {
    @JsonProperty(value = "Orders")
    private List<OrderVo> orders;
    @JsonProperty(value = "TotalCount")
    private Long totalCount;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class OrderVo implements Serializable {
        @JsonProperty(value = "Id")
        private Long id;
        //没有
        @JsonProperty(value = "Login")
        private Long login;
        @JsonProperty(value = "Ticket")
        private Integer ticket;
        @JsonProperty(value = "OpenTime")
        private Date openTime;
        @JsonProperty(value = "CloseTime")
        private Date closeTime;
        @JsonProperty(value = "Type")
        private String type;
        @JsonProperty(value = "Lots")
        private BigDecimal lots;
        @JsonProperty(value = "Symbol")
        private String symbol;
        @JsonProperty(value = "OpenPrice")
        private BigDecimal openPrice;
        @JsonProperty(value = "StopLoss")
        private BigDecimal stopLoss;
        @JsonProperty(value = "TakeProfit")
        private BigDecimal takeProfit;
        @JsonProperty(value = "ClosePrice")
        private BigDecimal closePrice;
        @JsonProperty(value = "MagicNumber")
        private Integer magicNumber;
        @JsonProperty(value = "Swap")
        private BigDecimal swap;
        @JsonProperty(value = "Commission")
        private BigDecimal commission;
        @JsonProperty(value = "Comment")
        private String comment;
        @JsonProperty(value = "Profit")
        private BigDecimal profit;
        @JsonProperty(value = "PlaceType")
        private Integer placeType;
    }
}

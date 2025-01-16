package net.maku.followcom.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * Author:  zsd
 * Date:  2024/12/23/周一 17:05
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderSendVO  implements Serializable {

    @JsonProperty(value = "ClientId")
    private Integer clientId;
    @JsonProperty(value = "Account")
    private List<AccountModelVO> account;

    @JsonProperty(value = "Lots")
    private BigDecimal lots;
    @JsonProperty(value = "Type")
    private Integer type;
    @JsonProperty(value = "Symbol")
    private String symbol;
    @JsonProperty(value = "StopLoss")
    private BigDecimal stopLoss;
    @JsonProperty(value = "TakeProfit")
    private BigDecimal takeProfit;
    @JsonProperty(value = "Count")
    private BigDecimal count;
    @JsonProperty(value = "Comment")
    private String comment;
}

package net.maku.mascontrol.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;


@Data
@Builder
public class SlaveRepairVO implements Serializable {


    @Schema(description = "喊单订单号")
    private Integer masterTicket;

    @Schema(description = "喊单开仓时间")
    private LocalDateTime masterOpenTime;

    @Schema(description = "喊单品种")
    private String masterSymbol;

    @Schema(description = "喊单手数")
    private double masterLots;

    @Schema(description = "喊单盈亏")
    private double masterProfit;

    @Schema(description = "喊单下单类型")
    private String masterType;

    @Schema(description = "跟单订单号")
    private Integer slaveTicket;

    @Schema(description = "跟单开仓时间")
    private LocalDateTime slaveOpenTime;

    @Schema(description = "跟单品种")
    private String slaveSymbol;

    @Schema(description = "跟单手数")
    private double slaveLots;

    @Schema(description = "跟单盈亏")
    private double slaverProfit;

    @Schema(description = "跟单下单类型")
    private String slaveType;;

//    @Schema(description = "喊单平仓时间")
//    private LocalDateTime closeTime ;

    @Schema(description = "漏单类型0-跟单 1-平仓")
    private Integer repairType;
}

package net.maku.followcom.vo;


import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Author:  zsd
 * Date:  2024/11/15/周五 17:03
 * 跟单者
 */
@Data
@Schema(description = "跟单者添加")
public class FollowInsertVO implements Serializable {
    @Serial
    private static final long serialVersionUID = -4978328594296263454L;
    //客户端Id
    @JsonProperty(value = "ClientId")
    private Integer clientId;
    //喊单Id
    @JsonProperty(value = "SourceId")
    @NotNull(message = "喊单账号不能为空")
    private Long sourceId;
    //平台Id
    @JsonProperty(value = "PlatformId")
    @NotNull(message = "平台不能为空")
    private Integer platformId;
    //账户
    @JsonProperty(value = "User")
    @NotNull(message = "账号不能为空")
    private Long user;
    //密码
    @JsonProperty(value = "Password")
    @NotBlank(message = "密码不能为空")
    private String password;
    //备注
    @JsonProperty(value = "Comment")
    private String comment;
    //模式 跟单方向 0=正跟 1=反跟 ,
    @JsonProperty(value = "Direction")
    @Min(value = 0, message = "跟单方向只能为正跟0或反跟1")
    @Max(value = 1, message = "跟单方向只能为正跟0或反跟1")
    @NotNull(message = "平台不能为空")
    private Integer direction;
    //模式-类型 0=多空跟单 1=只跟多单 2=只跟空单 3=挂单 4=全部
    @JsonProperty(value = "Type")
    @Min(value = 0, message = "跟随模式只能为多空跟单0、只跟多单1、只跟空单2、挂单3或全部4")
    @Max(value = 4, message = "跟随模式只能为多空跟单0、只跟多单1、只跟空单2、挂单3或全部4")
    private Integer type;
    //模式-模式 0=资金比例(净值) 1=手数比例 2=固定手数 3=资金比例(余额) 4=自定义 ,
    @JsonProperty(value = "Mode")
    @Min(value = 0, message = "跟随模式只能为资金比例(净值)0、手数比例1、固定手数2、资金比例(余额)3或自定义4")
    @Max(value = 4, message = "跟随模式只能为资金比例(净值)0、手数比例1、固定手数2、资金比例(余额)3或自定义4")
    @NotNull(message = "跟随模式不能为空")
    private Integer mode;
    //模式-参数
    @JsonProperty(value = "ModeValue")
    private BigDecimal modeValue;
    //风控-补单价格更优
    @JsonProperty(value = "RepairyPriceHigh")
    private Integer repairyPriceHigh;
    //风控-喊单手数小于
    @JsonProperty(value = "SourceLotsLow")
    private Double sourceLotsLow;
    //风控-喊单手数大于
    @JsonProperty(value = "SourceLotsHigh")
    private Double sourceLotsHigh;
    //风控-净值低于
    @JsonProperty(value = "EquityLow")
    private Double equityLow;
    //风控-净值高于
    @JsonProperty(value = "EquityHigh")
    private Double equityHigh;
    //风控-预付款比例低于
    @JsonProperty(value = "MarginLevelLow")
    private Double marginLevelLow;
    //风控-预付款比例高于
    @JsonProperty(value = "MarginLevelHigh")
    private Double marginLevelHigh;
    //风控-持仓手数高于
    @JsonProperty(value = "OpenOrderLotsHigh")
    private Double openOrderLotsHigh;
    //风控-持仓单数高于
    @JsonProperty(value = "OpenOrderCountHigh")
    private Integer openOrderCountHigh;
    //风控-喊单获利高于
    @JsonProperty(value = "SourceProfitHigh")
    private Double sourceProfitHigh;
    //风控-喊单亏损高于
    @JsonProperty(value = "SourceLossHigh")
    private Double sourceLossHigh;
    //风控-喊单获利低于
    @JsonProperty(value = "SourceProfitLow")
    private Double sourceProfitLow;
    //风控-喊单亏损低于
    @JsonProperty(value = "SourceLossLow")
    private Double sourceLossLow;
    //风控-喊单获利高于%
    @JsonProperty(value = "SourceProfitPercentageHigh")
    private Double sourceProfitPercentageHigh;
    //风控-喊单亏损高于%
    @JsonProperty(value = "SourceLossPercentageHigh")
    private Double sourceLossPercentageHigh;
    //风控-喊单获利低于%
    @JsonProperty(value = "sourceProfitPercentageLow")
    private Double sourceProfitPercentageLow;
    //风控-喊单亏损低于%
    @JsonProperty(value = "sourceLossPercentageLow")
    private Double sourceLossPercentageLow;
    //风控-喊单获利高于 每单
    @JsonProperty(value = "SourceEachProfitHigh")
    private Double sourceEachProfitHigh;
    //风控-喊单亏损高于 每单
    @JsonProperty(value = "SourceEachLossHigh")
    private Double sourceEachLossHigh;
    //风控-喊单获利低于 每单
    @JsonProperty(value = "SourceEachProfitLow")
    private Double sourceEachProfitLow;
    // 风控-喊单亏损低于 每单
    @JsonProperty(value = "sourceEachLossLow")
    private Double sourceEachLossLow;
    //风控-喊单获利（点）高于 每单
    @JsonProperty(value = "SourceEachProfitPointHigh")
    private Integer sourceEachProfitPointHigh;
    //风控-喊单亏损高于（点）每单
    @JsonProperty(value = "SourceEachLossPointHigh")
    private Integer sourceEachLossPointHigh;
    //风控-喊单获利（点）低于 每单
    @JsonProperty(value = "SourceEachProfitPointLow")
    private Integer sourceEachProfitPointLow;
    //风控-喊单亏损低于（点）每单
    @JsonProperty(value = "SourceEachLossPointLow")
    private Integer sourceEachLossPointLow;
    //风控-喊单持仓单数低于
    @JsonProperty(value = "SourceOpenOrderCountLow")
    private Integer sourceOpenOrderCountLow;
    //风控-喊单持仓单数高于
    @JsonProperty(value = "SourceOpenOrderCountHigh")
    private Integer sourceOpenOrderCountHigh;
    //强平-亏损高于(点)：
    @JsonProperty(value = "CloseLossPointHigh")
    private Integer closeLossPointHigh;
    //强平-获利高于(点)：
    @JsonProperty(value = "CloseProfitPointHigh")
    private Integer closeProfitPointHigh;
    //强平-获利高于
    @JsonProperty(value = "CloseProfitHigh")
    private Double closeProfitHigh;
    //强平-亏损高于 ,
    @JsonProperty(value = "CloseLossHigh")
    private Double closeLossHigh;
    // 强平-获利高于 全部 ,
    @JsonProperty(value = "CloseAllProfitHigh")
    private Double closeAllProfitHigh;
    //强平-亏损高于 全部 ,
    @JsonProperty(value = "CloseAllLossHigh")
    private Double closeAllLossHigh;
    //强平-净值低于 全部 ,
    @JsonProperty(value = "CloseAllEquityLow")
    private Double closeAllequitylow;
    //强平-净值高于 全部 ,
    @JsonProperty(value = "CloseAllEquityHigh")
    private Double closeAllequityhigh;
    // 强平-持仓时长高于(秒)： 公式=小时3600+分钟60+秒
    @JsonProperty(value = "CloseAllTimeFrom")
    private Integer closeAllTimeFrom;

    //强平-获利高于%
    @JsonProperty(value = "CloseAllProfitPercentageHigh")
    private Double closeAllProfitPercentageHigh;
    //强平-亏损高于%
    @JsonProperty(value = "CloseAllLossPercentageHigh")
    private Double closeAllLossPercentageHigh;
    //风控-不跟单时段-起始 公式=小时3600+分钟60+秒 ,
    @JsonProperty(value = "TimeFrom")
    private Integer timeFrom;
    //风控-不跟单时段-结束 公式=小时3600+分钟60+秒
    @JsonProperty(value = "TimeTo")
    private Integer timeTo;
    // 风控-不跟单时段状态 ,
    @JsonProperty(value = "TimeStatus")
    private Integer timeStatus;
    //风控-不跟单时段强平状态 ,
    @JsonProperty(value = "TimeCloseAllStatus")
    private Integer timeCloseAllStatus;
    //模式-手数取余 0=四舍五入 1=取小数 ,
    @JsonProperty(value = "LotsRounding")
    private Integer lotsRounding;
    //风控-注释模板
    @JsonProperty(value = "CommentRegex")
    private String commentRegex;
    //跟单状态只能为0-未开启 1-已开启
    @JsonProperty(value = "Status")
    @Min(value = 0, message = "跟单状态只能为未开启0或已开启1")
    @Max(value = 1, message = "跟单状态只能为未开启0或已开启1")
    @NotNull(message = "跟单状态不能为空")
    private Integer status;
    //同步止盈止损状态
    @JsonProperty(value = "UpdateStopLossTakeProfitStatus")
    private Integer updateStopLossTakeProfitStatus;
    //充用
    @JsonProperty(value = "PositionComment")
    private String positionComment;
    //补单状态 0-未开启 1-开启
    @JsonProperty(value = "RepairStatus")
    @Min(value = 0, message = "跟单补单状态只能为未开启0或已开启1")
    @Max(value = 1, message = "跟单补单状态只能为未开启0或已开启1")
    @NotNull(message = "跟单补单状态不能为空")
    private Integer repairStatus;
    //开仓状态 0-未开启 1-开启
    @JsonProperty(value = "OpenOrderStatus")
    @Min(value = 0, message = "跟单开仓状态为未开启0或已开启1")
    @Max(value = 1, message = "跟单开仓状态为未开启0或已开启1")
    @NotNull(message = "跟单开仓状态不能为空")
    private Integer openOrderStatus;
    //平仓状态0-未开启 1-开启
    @JsonProperty(value = "CloseOrderStatus")
    @Min(value = 0, message = "跟单平仓状态为未开启0或已开启1")
    @Max(value = 1, message = "跟单平仓状态为未开启0或已开启1")
    @NotNull(message = "跟单平仓状态不能为空")
    private Integer closeOrderStatus;
    //同步止损加点
    @JsonProperty(value = "UpdateStopLossOffset")
    private Integer updateStopLossOffset;
    //同步止盈加点
    @JsonProperty(value = "UpdateTakeProfitOffset")
    private Integer updateTakeProfitOffset;
    //弃用 ,
    @JsonProperty(value = "SourceCloseOrderStatus")
    private Integer sourceCloseOrderStatus;
    //自定义止损加点
    @JsonProperty(value = "CustomStopLossOffset")
    private Integer customStopLossOffset;
    //自定义止盈加点 ,
    @JsonProperty(value = "customTakeProfitOffset")
    private Integer customTakeProfitOffset;
    //自定义止盈止损状态 ,
    @JsonProperty(value = "CustomStopLossTakeProfitStatus")
    private Integer customStopLossTakeProfitStatus;
    //下单类型 [Mt4] Client = 0,Expert = 1,Dealer = 2,Signal = 3,Gateway = 4,Mobile = 5,Web = 6,Api = 7,Default = 8, [Mt5] Manually = 0,Mobile = 16,Web = 17,ByExpert = 1,OnSL = 3,OnTP = 4,OnStopOut = 5,OnRollover = 6,
    // OnVmargin = 8,OnSplit = 18,ByDealer = 2,Gateway = 9,Signal = 10,Settlement = 11,Transfer = 12,Sync = 13,ExternalService = 14,Migration = 15,Default = 20
    @JsonProperty(value = "PlacedType")
    private Integer placedType;


}

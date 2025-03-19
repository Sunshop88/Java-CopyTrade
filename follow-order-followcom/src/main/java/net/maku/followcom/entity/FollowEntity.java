package net.maku.followcom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * Author:  zsd
 * Date:  2024/11/14/周四 17:42
 * 跟单者
 */
@Data
@TableName("follow")
public class FollowEntity {
    @TableId(type = IdType.AUTO)
    @TableField(value = "id")
    private Integer id;

    @TableField(value = "sourceId")
    private Integer sourceId;

    @TableField(value = "platformId")
    private Integer platformId;

    @TableField(value = "user")
    private Long user;

    @TableField(value = "password")
    private String password;

    @TableField(value = "direction")
    private Integer direction;

    @TableField(value = "type")
    private Integer type;

    @TableField(value = "mode")
    private Integer mode;

    @TableField(value = "modeValue")
    private Double modeValue;

    @TableField(value = "sourceLotsLow")
    private Double sourceLotsLow;

    @TableField(value = "sourceLotsHigh")
    private Double sourceLotsHigh;

    @TableField(value = "status")
    private Integer status;

    @TableField(value = "equityLow")
    private Double equityLow;

    @TableField(value = "equityHigh")
    private Double equityHigh;

    @TableField(value = "marginLevelLow")
    private Double marginLevelLow;

    @TableField(value = "marginLevelHigh")
    private Double marginLevelHigh;

    @TableField(value = "closeProfitHigh")
    private Double closeProfitHigh;

    @TableField(value = "closeLossHigh")
    private Double closeLossHigh;

    @TableField(value = "closeAllProfitHigh")
    private Double closeAllProfitHigh;

    @TableField(value = "closeAllLossHigh")
    private Double closeAllLossHigh;

    @TableField(value = "openOrderLotsHigh")
    private Double openOrderLotsHigh;

    @TableField(value = "openOrderCountHigh")
    private Integer openOrderCountHigh;

    @TableField(value = "comment")
    private String comment;

    @TableField(value = "timeFrom")
    private Integer timeFrom;

    @TableField(value = "timeTo")
    private Integer timeTo;

    @TableField(value = "timeStatus")
    private Integer timeStatus;

    @TableField(value = "lotsRounding")
    private Integer lotsRounding;

    @TableField(value = "commentRegex")
    private String commentRegex;

    @TableField(value = "updateStopLossTakeProfitStatus")
    private Integer updateStopLossTakeProfitStatus;

    @TableField(value = "positionComment")
    private String positionComment;

    @TableField(value = "repairyPriceHigh")
    private Integer repairyPriceHigh;

    @TableField(value = "closeAllequitylow")
    private Double closeAllequitylow;

    @TableField(value = "closeAllequityhigh")
    private Double closeAllequityhigh;

    @TableField(value = "repairStatus")
    private Integer repairStatus;

    @TableField(value = "sourceProfitHigh")
    private Double sourceProfitHigh;

    @TableField(value = "sourceLossHigh")
    private Double sourceLossHigh;

    @TableField(value = "sourceProfitLow")
    private Double sourceProfitLow;

    @TableField(value = "sourceLossLow")
    private Double sourceLossLow;

    @TableField(value = "timeCloseAllStatus")
    private Integer timeCloseAllStatus;

    @TableField(value = "openOrderStatus")
    private Integer openOrderStatus;

    @TableField(value = "closeOrderStatus")
    private Integer closeOrderStatus;

    @TableField(value = "sourceOpenOrderCountLow")
    private Integer sourceOpenOrderCountLow;

    @TableField(value = "sourceOpenOrderCountHigh")
    private Integer sourceOpenOrderCountHigh;

    @TableField(value = "updateStopLossOffset")
    private Integer updateStopLossOffset;

    @TableField(value = "updateTakeProfitOffset")
    private Integer updateTakeProfitOffset;

    @TableField(value = "closeLossPointHigh")
    private Integer closeLossPointHigh;

    @TableField(value = "closeProfitPointHigh")
    private Integer closeProfitPointHigh;

    @TableField(value = "SourceCloseOrderStatus")
    private Integer sourceCloseOrderStatus;

    @TableField(value = "sourceProfitPercentageHigh")
    private Double sourceProfitPercentageHigh;

    @TableField(value = "sourceLossPercentageHigh")
    private Double sourceLossPercentageHigh;

    @TableField(value = "sourceProfitPercentageLow")
    private Double sourceProfitPercentageLow;

    @TableField(value = "sourceLossPercentageLow")
    private Double sourceLossPercentageLow;

    @TableField(value = "sourceEachProfitHigh")
    private Double sourceEachProfitHigh;

    @TableField(value = "sourceEachLossHigh")
    private Double sourceEachLossHigh;

    @TableField(value = "sourceEachProfitLow")
    private Double sourceEachProfitLow;

    @TableField(value = "sourceEachLossLow")
    private Double sourceEachLossLow;

    @TableField(value = "sourceEachProfitPointHigh")
    private Integer sourceEachProfitPointHigh;

    @TableField(value = "sourceEachLossPointHigh")
    private Integer sourceEachLossPointHigh;

    @TableField(value = "sourceEachProfitPointLow")
    private Integer sourceEachProfitPointLow;

    @TableField(value = "sourceEachLossPointLow")
    private Integer sourceEachLossPointLow;

    @TableField(value = "closeAllProfitPercentageHigh")
    private Double closeAllProfitPercentageHigh;

    @TableField(value = "closeAllLossPercentageHigh")
    private Double closeAllLossPercentageHigh;

    @TableField(value = "closeAllTimeFrom")
    private Integer closeAllTimeFrom;

    @TableField(value = "customStopLossTakeProfitStatus")
    private Integer customStopLossTakeProfitStatus;

    @TableField(value = "customTakeProfitOffset")
    private Integer customTakeProfitOffset;

    @TableField(value = "customStopLossOffset")
    private Integer customStopLossOffset;

    @TableField(value = "placedType")
    private Integer placedType;

    @TableField(value = "clientId")
    private Integer clientId;

}
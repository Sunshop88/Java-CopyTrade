package net.maku.followcom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * Author:  zsd
 * Date:  2024/11/14/周四 17:42
 * 喊单者
 */
@Data
@TableName("source")
@Builder
public class SourceEntity implements Serializable {
    @Serial
    private static final long serialVersionUID = -4604067893708088422L;
    @TableId(type = IdType.AUTO)
    @TableField(value = "id")
    private Integer id;

    @TableField(value = "platformId")
    private Integer platformId;

    @TableField(value = "user")
    private Long user;

    @TableField(value = "password")
    private String password;

    @TableField(value = "comment")
    private String comment;

    @TableField(value = "status")
    private Integer status;

    @TableField(value = "closeLossPointHigh")
    private Integer closeLossPointHigh;

    @TableField(value = "closeProfitPointHigh")
    private Integer closeProfitPointHigh;

    @TableField(value = "closeProfitHigh")
    private Double closeProfitHigh;

    @TableField(value = "closeLossHigh")
    private Double closeLossHigh;

    @TableField(value = "closeAllProfitHigh")
    private Double closeAllProfitHigh;

    @TableField(value = "closeAllLossHigh")
    private Double closeAllLossHigh;

    @TableField(value = "closeAllEquityLow")
    private Double closeAllEquityLow;

    @TableField(value = "closeAllEquityHigh")
    private Double closeAllEquityHigh;

    @TableField(value = "closeAllMarginLevelLow")
    private Double closeAllMarginLevelLow;

    @TableField(value = "closeAllMarginLevelHigh")
    private Double closeAllMarginLevelHigh;

    @TableField(value = "clientId")
    private Integer clientId;
}

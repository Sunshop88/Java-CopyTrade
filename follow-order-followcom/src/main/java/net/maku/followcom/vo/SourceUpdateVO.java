package net.maku.followcom.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;

/**
 * Author:  zsd
 * Date:  2024/11/15/周五 15:14
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "喊单更新")
public class SourceUpdateVO implements Serializable {
    @Serial
    private static final long serialVersionUID = -162047724090123785L;
    @JsonProperty("Id")
    @NotNull(message = "Id不能为空")
    private Long id;
    @JsonProperty("ClientId")
    private Integer serverId;
    //密码
    @JsonProperty("Password")
    private String password;
    //备注
    @JsonProperty("Comment")
    private String remark;
    //状态
    @JsonProperty("Status")
    private Boolean status;

    @JsonProperty(value = "CloseLossPointHigh")
    private Integer closeLossPointHigh;

    @JsonProperty(value = "CloseProfitPointHigh")
    private Integer closeProfitPointHigh;

    @JsonProperty(value = "CloseProfitHigh")
    private Double closeProfitHigh;

    @JsonProperty(value = "CloseLossHigh")
    private Double closeLossHigh;

    @JsonProperty(value = "CloseAllProfitHigh")
    private Double closeAllProfitHigh;

    @JsonProperty(value = "CloseAllLossHigh")
    private Double closeAllLossHigh;

    @JsonProperty(value = "CloseAllEquityLow")
    private Double closeAllEquityLow;

    @JsonProperty(value = "CloseAllEquityHigh")
    private Double closeAllEquityHigh;

    @JsonProperty(value = "CloseAllMarginLevelLow")
    private Double closeAllMarginLevelLow;

    @JsonProperty(value = "CloseAllMarginLevelHigh")
    private Double closeAllMarginLevelHigh;


}

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
    //账号id
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
    //亏损高于(点)
    @JsonProperty(value = "CloseLossPointHigh")
    private Integer closeLossPointHigh;
    //获利高于(点)
    @JsonProperty(value = "CloseProfitPointHigh")
    private Integer closeProfitPointHigh;
    //获利高于$
    @JsonProperty(value = "CloseProfitHigh")
    private Double closeProfitHigh;
    //亏损高于$
    @JsonProperty(value = "CloseLossHigh")
    private Double closeLossHigh;
    //获利高于$：全部
    @JsonProperty(value = "CloseAllProfitHigh")
    private Double closeAllProfitHigh;
    //亏损高于$：全部
    @JsonProperty(value = "CloseAllLossHigh")
    private Double closeAllLossHigh;
    //净值低于$
    @JsonProperty(value = "CloseAllEquityLow")
    private Double closeAllEquityLow;
    //净值高于$
    @JsonProperty(value = "CloseAllEquityHigh")
    private Double closeAllEquityHigh;
    //预付款比例低于%
    @JsonProperty(value = "CloseAllMarginLevelLow")
    private Double closeAllMarginLevelLow;
    //预付款比例高于%
    @JsonProperty(value = "CloseAllMarginLevelHigh")
    private Double closeAllMarginLevelHigh;


}

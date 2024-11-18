package net.maku.followcom.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * Author:  zsd
 * Date:  2024/11/14/周四 15:25
 */
@Data
@Schema(description = "喊单添加")
public class SourceInsertVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 3774316922670977306L;
    @JsonProperty("ClientId")
    @NotNull(message = "ClientId不能为空")
    private Integer serverId;
    //平台Id
    @JsonProperty("PlatformId")
    @NotNull(message = "平台Id不能为空")
    private Integer platformId;
    //账户
    @JsonProperty("User")
    @NotNull(message = "账户不能为空")
    private Long account;
    //密码
    @JsonProperty("Password")
    @NotBlank(message = "密码不能为空")
    private String password;
    //备注
    @JsonProperty("Comment")
    private String remark;
    //状态
    @JsonProperty("Status")
    @NotNull(message = "状态不能为空")
    private Boolean status;

    @JsonProperty(value = "closeLossPointHigh")
    private Integer closeLossPointHigh;

    @JsonProperty(value = "closeProfitPointHigh")
    private Integer closeProfitPointHigh;

    @JsonProperty(value = "closeProfitHigh")
    private Double closeProfitHigh;

    @JsonProperty(value = "closeLossHigh")
    private Double closeLossHigh;

    @JsonProperty(value = "closeAllProfitHigh")
    private Double closeAllProfitHigh;

    @JsonProperty(value = "closeAllLossHigh")
    private Double closeAllLossHigh;

    @JsonProperty(value = "closeAllEquityLow")
    private Double closeAllEquityLow;

    @JsonProperty(value = "closeAllEquityHigh")
    private Double closeAllEquityHigh;

    @JsonProperty(value = "closeAllMarginLevelLow")
    private Double closeAllMarginLevelLow;

    @JsonProperty(value = "closeAllMarginLevelHigh")
    private Double closeAllMarginLevelHigh;

    public Integer getStatus() {
        return status ? 0 : 1;
    }
}

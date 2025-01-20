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
    //id
    @JsonProperty("Id")
    @NotNull(message = "id")
    private Integer id;
    //vps服务器id
    @JsonProperty("ClientId")
    @NotNull(message = "vps服务器不能为空")
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
    //状态  true：开启，false：关闭
    @JsonProperty("Status")
    @NotNull(message = "状态不能为空")
    private Boolean status;
    /**
     * 亏损高于(点)
     * */
    @JsonProperty(value = "CloseLossPointHigh")
    private Integer closeLossPointHigh;
    // 获利高于(点
    @JsonProperty(value = "CloseProfitPointHigh")
    private Integer closeProfitPointHigh;
    //获利高于
    @JsonProperty(value = "CloseProfitHigh")
    private Double closeProfitHigh;
   //亏损高于
    @JsonProperty(value = "CloseLossHigh")
    private Double closeLossHigh;
     //获利高于$：全部
    @JsonProperty(value = "CloseAllProfitHigh")
    private Double closeAllProfitHigh;
   //亏损高于$：全部
    @JsonProperty(value = "CloseAllLossHigh")
    private Double closeAllLossHigh;
    //净值低于
    @JsonProperty(value = "CloseAllEquityLow")
    private Double closeAllEquityLow;
    //净值高于
    @JsonProperty(value = "CloseAllEquityHigh")
    private Double closeAllEquityHigh;
   // 预付款比例低于% ,
    @JsonProperty(value = "CloseAllMarginLevelLow")
    private Double closeAllMarginLevelLow;
    /**预付款比例高于%*/
    @JsonProperty(value = "CloseAllMarginLevelHigh")
    private Double closeAllMarginLevelHigh;
    
}

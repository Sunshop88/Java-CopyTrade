package net.maku.followcom.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * Author:  zsd
 * Date:  2025/1/24/周五 10:50
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RepairOrderVO {
    @JsonProperty(value = "ClientId")
    @NotNull(message = "客户端Id不能为空")
    private Integer clientId;

    @JsonProperty(value = "AccountId")
    @NotNull(message = "账户不能为空")
    private Integer accountId;
    @JsonProperty(value = "RiskStatus")
    private Boolean riskStatus;
    @JsonProperty(value = "EnforceStatus")
    private Boolean enforceStatus;
    @JsonProperty(value = "SourceTicket")
    @Size(min = 1,message = "订单账号不能为空")
    private List<Integer> sourceTicket ;
}

package net.maku.followcom.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Author:  zsd
 * Date:  2024/12/24/周二 10:42
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderCloseAllVO {
    @JsonProperty(value = "ClientId")
    @NotNull(message = "客户端Id不能为空")
    private Integer clientId;

    @JsonProperty(value = "Account")
    @NotNull(message = "账户不能为空")
    private List<AccountModelVO> account;
}

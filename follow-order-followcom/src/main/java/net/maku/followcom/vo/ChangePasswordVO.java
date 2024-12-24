package net.maku.followcom.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.List;

/**
 * Author:  zsd
 * Date:  2024/12/24/周二 11:35
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChangePasswordVO  implements Serializable {

    @JsonProperty(value = "ClientId")
    @NotNull(message = "客户端Id不能为空")
    private Integer clientId;

    @JsonProperty(value = "Account")
    @NotNull(message = "账户不能为空")
    private List<AccountModelVO> account;
    @JsonProperty(value = "Password")
    @NotBlank(message ="密码不能为空")
    private String password;
    @JsonProperty(value = "Investor")
    @NotBlank(message ="是否投资密码不能为空")
    private Boolean investor;

}

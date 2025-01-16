package net.maku.followcom.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * Author:  zsd
 * Date:  2024/12/23/周一 17:08
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AccountModelVO  implements Serializable {
    //客户端Id
    @JsonProperty(value = "id")
    @NotNull(message = "账号id不能为空")
    private Long id;
    //客户端Id
    @JsonProperty(value = "Type")
    @NotNull(message = "账号类型不能为空")
    @Min(value = 0, message = "账号类型只能喊单或者跟单")
    @Max(value =1, message = "账号类型只能喊单或者跟单")
    private Integer type;

}
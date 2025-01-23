package net.maku.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.io.Serializable;

@Data
@NoArgsConstructor
@Schema(description = "mfa入参")
public class MfaDto implements Serializable {

    @NotBlank(message = "用户名不能为空")
    @Schema(description = "账号", example = "admin")
    private String username;

    @Schema(description = "密码")
    @NotBlank(message = "密码不能为空")
    private String password;

}

package net.maku.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.io.Serializable;

@Data
@NoArgsConstructor
@Schema(description = "mfa入参")
public class MfaDto implements Serializable {

    /**
     * 账号
     */
    @NonNull
    @Schema(description = "账号", example = "admin")
    private String username;

}

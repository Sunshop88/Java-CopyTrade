package net.maku.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.io.Serializable;

@Data
@Schema(description = "mfa认证入参")
@NoArgsConstructor
public class MfaVerifyDto implements Serializable {

    /**
     * 账号
     */
    @NonNull
    @Schema(description = "账号")
    private String username;

    /**
     * 秘钥
     */
    @Schema(description = "秘钥(第一次认证需把秘钥传过来)")
    private String secretKey;

    /**
     * 验证码
     */
    @NonNull
    @Schema(description = "验证码")
    private Integer code;

    /**
     * 用户MFA是否已认证
     */
    @NonNull
    @Schema(description = "用户MFA是否已认证（1：已认证；0：未认证）")
    private Integer isMfaVerified;

}

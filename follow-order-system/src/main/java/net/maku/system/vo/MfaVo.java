package net.maku.system.vo;

//import com.warrenstrange.googleauth.GoogleAuthenticatorKey;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "mfa出参")
public class MfaVo implements Serializable {

    /**
     * 秘钥
     */
    @Schema(description = "秘钥")
    private String secretKey;

    /**
     * 二维码
     */
    @Schema(description = "二维码")
    private String qrCode;

    /**
     * 用户MFA是否已认证
     */
    @Schema(description = "用户MFA是否已认证（1：已认证；0：未认证）")
    private Integer isMfaVerified;

}

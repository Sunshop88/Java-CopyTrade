package net.maku.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NonNull;

import java.io.Serializable;

/**
 * 账号登录
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@Schema(description = "账号登录")
public class SysAccountLoginVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "用户名")
    @NotBlank(message = "用户名不能为空")
    private String username;

    @Schema(description = "密码")
    @NotBlank(message = "密码不能为空")
    private String password;

    @Schema(description = "唯一key")
    private String key;

    @Schema(description = "验证码")
    private String captcha;

    @Schema(description = "秘钥(第一次认证需把秘钥传过来)")
    private String secretKey;

    @Schema(description = "MFA验证码")
    private Integer code;

    @Schema(description = "用户MFA是否已认证（1：已认证；0：未认证）")
    private Integer isMfaVerified;

    @Schema(description = "是否开启MFA认证（1：是；0：否）")
    private Integer isStartMfaVerify = 0;
}

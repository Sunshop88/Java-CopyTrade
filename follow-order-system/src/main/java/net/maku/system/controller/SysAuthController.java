package net.maku.system.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import net.maku.framework.common.utils.Result;
import net.maku.framework.security.utils.TokenUtils;
import net.maku.system.dto.MfaDto;
import net.maku.system.service.SysUserMfaVerifyService;
import net.maku.system.service.SysAuthService;
import net.maku.system.service.SysCaptchaService;
import net.maku.system.vo.*;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 认证管理
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@RestController
@RequestMapping("sys/auth")
@Tag(name = "认证管理")
@AllArgsConstructor
public class SysAuthController {
    private final SysCaptchaService sysCaptchaService;
    private final SysAuthService sysAuthService;
    private final SysUserMfaVerifyService mfaVerifyService;

    @GetMapping("captcha")
    @Operation(summary = "验证码")
    public Result<SysCaptchaVO> captcha() {
        SysCaptchaVO captchaVO = sysCaptchaService.generate();

        return Result.ok(captchaVO);
    }

    @GetMapping("captcha/enabled")
    @Operation(summary = "是否开启验证码")
    public Result<Boolean> captchaEnabled() {
        boolean enabled = sysCaptchaService.isCaptchaEnabled();

        return Result.ok(enabled);
    }

    @PostMapping("login")
    @Operation(summary = "账号密码登录")
    public Result<SysUserTokenVO> login(@RequestBody @Valid SysAccountLoginVO login) {
        SysUserTokenVO token = sysAuthService.loginByAccount(login);

        return Result.ok(token);
    }

    @PostMapping("send/code")
    @Operation(summary = "发送短信验证码")
    public Result<String> sendCode(String mobile) {
        boolean flag = sysAuthService.sendCode(mobile);
        if (!flag) {
            return Result.error("短信发送失败！");
        }

        return Result.ok();
    }

    @PostMapping("mobile")
    @Operation(summary = "手机号登录")
    public Result<SysUserTokenVO> mobile(@RequestBody SysMobileLoginVO login) {
        SysUserTokenVO token = sysAuthService.loginByMobile(login);

        return Result.ok(token);
    }

    @PostMapping("third")
    @Operation(summary = "第三方登录")
    public Result<SysUserTokenVO> third(@RequestBody SysThirdCallbackVO login) {
        SysUserTokenVO token = sysAuthService.loginByThird(login);

        return Result.ok(token);
    }

    @PostMapping("token")
    @Operation(summary = "获取 accessToken")
    public Result<AccessTokenVO> token(String refreshToken) {
        AccessTokenVO token = sysAuthService.getAccessToken(refreshToken);

        return Result.ok(token);
    }

    @PostMapping("logout")
    @Operation(summary = "退出")
    public Result<String> logout(HttpServletRequest request) {
        sysAuthService.logout(TokenUtils.getAccessToken(request));

        return Result.ok();
    }

    @Operation(summary = "获取MFA秘钥")
    @PostMapping(value = "/mfaVerifyShow")
    public Result<MfaVo> mfaVerifyShow(@RequestBody MfaDto mfaDto) {
        MfaVo mfaVo = mfaVerifyService.mfaVerifyShow(mfaDto);
        return Result.ok(mfaVo);
    }
}

package net.maku.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import net.maku.framework.common.utils.Result;
import net.maku.system.dto.MfaDto;
import net.maku.system.dto.MfaVerifyDto;
import net.maku.system.entity.SysUserMfaVerifyEntity;
import net.maku.system.vo.MfaVo;
import net.maku.system.vo.SysUserVO;

import java.util.List;

/**
 * <p>
 * MFA认证
 * </p>
 *
 * @Author: Calorie
 * @Date: 2024-11-29
 */
public interface SysUserMfaVerifyService extends IService<SysUserMfaVerifyEntity> {

    /**
     * 获取MFA秘钥
     *
     * @param mfaDto
     * @return
     */
    MfaVo mfaVerifyShow(MfaDto mfaDto);

    /**
     * 判断用户是否已认证
     *
     * @param mfaDto
     * @return
     */
    Integer isMfaVerify(MfaDto mfaDto);

    /**
     * MFA验证码验证
     *
     * @param mfaVerifyDto
     * @return
     */
    Result<Integer> mfaVerify(MfaVerifyDto mfaVerifyDto);

    /**
     * 获取用户认证信息
     * @return
     */
    List<SysUserMfaVerifyEntity> getMfaVerify();

    /**
     * 修改mfa认证
     * @param vo
     */
    void editMfaVerify(SysUserVO vo);

    /**
     * 获取用户认证信息
     * @return
     */
    List<SysUserMfaVerifyEntity> getMfaVerifies();
}

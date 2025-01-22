package net.maku.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.enums.MfaVerifyEnum;
import net.maku.followcom.util.GoogleGeneratorUtil;
import net.maku.framework.common.utils.Result;
import net.maku.system.dao.SysUserMfaVerifyDao;
import net.maku.system.dto.MfaDto;
import net.maku.system.dto.MfaVerifyDto;
import net.maku.system.entity.SysUserMfaVerifyEntity;
import net.maku.system.service.SysUserMfaVerifyService;
import net.maku.system.vo.MfaVo;
import net.maku.system.vo.SysUserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * <p>
 * MFA认证
 * </p>
 *
 * @Author: Calorie
 * @Date: 2024-11-29
 */
@Service
@Slf4j
public class SysUserMfaVerifyServiceImpl extends ServiceImpl<SysUserMfaVerifyDao, SysUserMfaVerifyEntity> implements SysUserMfaVerifyService {

    @Autowired
    SysUserMfaVerifyDao sysUserMfaVerifyDao;

    @Override
    public MfaVo mfaVerifyShow(MfaDto mfaDto) {

        MfaVo mfaVo = new MfaVo();
        // 用户是否已认证
        String username = mfaDto.getUsername();
        Integer isMfaVerified = sysUserMfaVerifyDao.getMfaVerifyByUsername(username);
        mfaVo.setIsMfaVerified(isMfaVerified);

        // 未认证则生成密钥
        String secretKey = "";
        String qrBarcode = "";
        if (isMfaVerified != null && isMfaVerified != 1) {
            secretKey = GoogleGeneratorUtil.generateSecretKey();
            qrBarcode = GoogleGeneratorUtil.getQRBarcode(username, secretKey);
            mfaVo.setIsMfaVerified(0);
        }
        if (isMfaVerified == null) {
            secretKey = GoogleGeneratorUtil.generateSecretKey();
            qrBarcode = GoogleGeneratorUtil.getQRBarcode(username, secretKey);
            mfaVo.setIsMfaVerified(0);
        }
        mfaVo.setSecretKey(secretKey);
        mfaVo.setQrCode(qrBarcode);

        return mfaVo;
    }

    @Override
    public Integer isMfaVerify(MfaDto mfaDto) {
        String username = mfaDto.getUsername();
        Integer isMfaVerified = sysUserMfaVerifyDao.getMfaVerifyByUsername(username);
        return isMfaVerified;
    }

    @Override
    public Result<Integer> mfaVerify(MfaVerifyDto mfaVerifyDto) {
        Integer isMfaVerified = mfaVerifyDto.getIsMfaVerified();
        if (isMfaVerified != 0 && isMfaVerified != 1) {
            return Result.error(-1, "isVerified必须为0或1");
        }
        if (isMfaVerified == 0 && mfaVerifyDto.getSecretKey() == null) {
            return Result.error(-2, "用户第一次认证秘钥必传");
        }
        Integer code = mfaVerifyDto.getCode();
        if (code >= 1000000) {
            return Result.error(-3, "MFA验证码不能大于6位数");
        }
        String username = mfaVerifyDto.getUsername();
        String secretKey = "";

        // 已认证，则从数据库获取秘钥；未认证，则取前端传过来的秘钥
        if (isMfaVerified == 1) {
            secretKey = sysUserMfaVerifyDao.getSecretKey(username);
            if (secretKey == null) {
                return Result.error(-1, "用户未认证");
            }
        } else {
            secretKey = mfaVerifyDto.getSecretKey();
        }

        // 验证验证码是否匹配
        boolean mfaFlag = GoogleGeneratorUtil.checkCode(secretKey, mfaVerifyDto.getCode());
        if (!mfaFlag) {
            return Result.error(-2, "MFA验证失败");
        }

        // 第一次认证秘钥入库
        if (isMfaVerified == 0) {
            // 清空库里的秘钥，将前端传进来的秘钥存入库中，防止出现多个秘钥
            sysUserMfaVerifyDao.deleteByUsername(username);
            SysUserMfaVerifyEntity sysUserMfaVerifyEntity = new SysUserMfaVerifyEntity();
            sysUserMfaVerifyEntity.setUsername(mfaVerifyDto.getUsername());
            sysUserMfaVerifyEntity.setSecretKey(secretKey);
            sysUserMfaVerifyEntity.setIsMfaVerified(1);
            sysUserMfaVerifyDao.insert(sysUserMfaVerifyEntity);
        }
        return Result.ok(1);
    }

    @Override
    public List<SysUserMfaVerifyEntity> getMfaVerify() {
        List<SysUserMfaVerifyEntity> mfaVerifies = sysUserMfaVerifyDao.getMfaVerifies();
        return mfaVerifies;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void editMfaVerify(SysUserVO vo) {
        // 若mfa认证传入已认证，需从库中检查是否已认证，若否，则不予修改
        Integer isMfaVerifiedEdit = vo.getIsMfaVerified();
        String username = vo.getUsername();
        Integer isMfaVerified = sysUserMfaVerifyDao.getMfaVerifyByUsername(username);
        if (isMfaVerifiedEdit != null && isMfaVerifiedEdit.equals(MfaVerifyEnum.CERTIFIED.getType())) {
            if (isMfaVerified != null && !isMfaVerified.equals(MfaVerifyEnum.CERTIFIED.getType())) {
                throw new RuntimeException("操作失败，用户未通过MFA认证，不能修改为已认证状态");
            }
            if (isMfaVerified == null) {
                throw new RuntimeException("操作失败，用户未通过MFA认证，不能修改为已认证状态");
            }
        }
        if (isMfaVerifiedEdit != null && isMfaVerifiedEdit.equals(MfaVerifyEnum.NOT_CERTIFIED.getType())) {
            sysUserMfaVerifyDao.deleteByUsername(username);
        }
    }

    @Override
    public List<SysUserMfaVerifyEntity> getMfaVerifies() {
        return sysUserMfaVerifyDao.getMfaVerifies();
    }
}

package net.maku.system.dao;

import net.maku.framework.mybatis.dao.BaseDao;
import net.maku.system.entity.SysUserMfaVerifyEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SysUserMfaVerifyDao extends BaseDao<SysUserMfaVerifyEntity> {

    /**
     * 获取秘钥
     * @param username
     * @return
     */
    String getSecretKey(String username);

    /**
     * 根据登录账号删除认证信息
     * @param username
     */
    void deleteByUsername(String username);

    /**
     * 获取用户认证信息
     * @return
     */
    List<SysUserMfaVerifyEntity> getMfaVerifies();

    /**
     * 根据用户名获取MFA认证状态
     * @param username
     * @return
     */
    Integer getMfaVerifyByUsername(String username);

    /**
     * 根据账号获取密钥
     * @param username
     * @return
     */
    String getKeyByUsername(String username);
}

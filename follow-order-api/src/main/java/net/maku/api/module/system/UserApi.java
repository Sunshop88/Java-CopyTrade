package net.maku.api.module.system;

import net.maku.api.module.entity.SysUserEntity;

import java.util.Map;

/**
 * 用户API
 */
public interface UserApi {

    SysUserEntity getUserById(String id);

}

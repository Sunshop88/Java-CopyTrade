package net.maku.system.api;

import cn.hutool.core.bean.BeanUtil;
import lombok.AllArgsConstructor;
import net.maku.api.module.entity.SysUserEntity;
import net.maku.api.module.system.UserApi;
import net.maku.system.service.SysUserService;
import org.springframework.stereotype.Component;

/**
 * 用户服务Api
 */
@Component
@AllArgsConstructor
public class UserApiImpl implements UserApi {
    private SysUserService sysUserService;

    @Override
    public SysUserEntity getUserById(String id) {
        SysUserEntity sysUserEntity = new SysUserEntity();
        BeanUtil.copyProperties(sysUserService.getById(id),sysUserEntity);
        return sysUserEntity;
    }
}

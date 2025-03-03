package net.maku.system.api;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.AllArgsConstructor;
import net.maku.api.module.entity.SysUserEntity;
import net.maku.api.module.system.UserApi;
import net.maku.system.service.SysUserService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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

    @Override
    public List<Integer> getUser(String creator) {
       List<Integer> list = sysUserService.getUser(creator);
        return list;
    }

    @Override
    public List<String> getUserId(List<Long> vpsUserVO) {
        List<String> list = sysUserService.getRealNameList(vpsUserVO);
        return list;
    }
}

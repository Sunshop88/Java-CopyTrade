package net.maku.system.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fhs.trans.service.impl.TransService;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import net.maku.followcom.entity.FollowOrderDetailEntity;
import net.maku.followcom.entity.FollowVpsUserEntity;
import net.maku.followcom.service.FollowVpsService;
import net.maku.followcom.service.FollowVpsUserService;
import net.maku.followcom.vo.VpsUserVO;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.excel.ExcelFinishCallBack;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.common.utils.ExcelUtils;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import net.maku.framework.security.cache.TokenStoreCache;
import net.maku.framework.security.user.SecurityUser;
import net.maku.framework.security.utils.TokenUtils;
import net.maku.system.convert.SysUserConvert;
import net.maku.system.dao.SysUserDao;
import net.maku.system.entity.SysLogLoginEntity;
import net.maku.system.entity.SysRoleEntity;
import net.maku.system.entity.SysUserEntity;
import net.maku.system.entity.SysUserMfaVerifyEntity;
import net.maku.system.enums.SuperAdminEnum;
import net.maku.system.query.SysRoleUserQuery;
import net.maku.system.query.SysUserQuery;
import net.maku.system.service.*;
import net.maku.system.vo.SysUserAvatarVO;
import net.maku.system.vo.SysUserBaseVO;
import net.maku.system.vo.SysUserExcelVO;
import net.maku.system.vo.SysUserVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户管理
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Service
@AllArgsConstructor
public class SysUserServiceImpl extends BaseServiceImpl<SysUserDao, SysUserEntity> implements SysUserService {
    private final SysUserRoleService sysUserRoleService;
    private final SysUserPostService sysUserPostService;
    private final SysUserTokenService sysUserTokenService;
    private final SysOrgService sysOrgService;
    private final TokenStoreCache tokenStoreCache;
    private final TransService transService;
    private final FollowVpsUserService followVpsUserService;
    private final RedisCache redisCache;
    private final SysRoleService sysRoleService;
    private final SysLogLoginService sysLogLoginService;
    @Override
    public PageResult<SysUserVO> page(SysUserQuery query) {
        // 查询参数
        Map<String, Object> params = getParams(query);
        // 分页查询
        IPage<SysUserEntity> page = getPage(query);
        params.put(Constant.PAGE, page);
        // 数据列表
        List<SysUserEntity> list = baseMapper.getList(params);
        List<SysUserVO> voList = new ArrayList<>();

        for (SysUserEntity entity : list) {
            SysUserVO vo = SysUserConvert.INSTANCE.convert(entity);
            // 添加 roleNameList
            vo.setRoleNameList(getRoleNames(entity.getId())); // 自定义方法获取角色名称
            // 查询sys_log_login中最新的那一条信息
            SysLogLoginEntity sysLogLoginEntity = sysLogLoginService.getOne(Wrappers.<SysLogLoginEntity>lambdaQuery()
                            .orderByDesc(SysLogLoginEntity::getCreateTime)
                            .last("limit 1"));
            vo.setLastLoginTime(sysLogLoginEntity.getCreateTime());
            vo.setIp(sysLogLoginEntity.getIp());
            voList.add(vo);
            //查询Vps
            List<FollowVpsUserEntity> vpsUserEntities = followVpsUserService.list(new LambdaQueryWrapper<FollowVpsUserEntity>().eq(FollowVpsUserEntity::getUserId, entity.getId()));
            if (ObjectUtil.isNotEmpty(vpsUserEntities)){
                List<VpsUserVO> vpsUserVOList = new ArrayList<>();
                vpsUserEntities.forEach(o->{
                    VpsUserVO vpsUserVO = new VpsUserVO();
                    vpsUserVO.setName(o.getVpsName());
                    vpsUserVO.setId(o.getVpsId());
                    vpsUserVOList.add(vpsUserVO);
                });
                vo.setVpsList(vpsUserVOList);
            }
        }


        // 返回分页结果
        return new PageResult<>(voList, page.getTotal());
    }

    private List<String> getRoleNames(Long id) {
        List<String> vo = new ArrayList<>();
        //通过id查询角色用户表中的role_id
        List<Long> roleIdList = sysUserRoleService.getRoleIdList(id);
        //通过roleIdList查询角色表中的name
        LambdaQueryWrapper<SysRoleEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNotNull(SysRoleEntity::getId);
        List<SysRoleEntity> sysRoleList = sysRoleService.list(wrapper);
        Map<Long, SysRoleEntity> sysRoleMap = new HashMap<>();
        if (sysRoleList != null && !sysRoleList.isEmpty()) {
            sysRoleMap = sysRoleList.stream().collect(Collectors.toMap(SysRoleEntity::getId, s -> s));
        }
        for (Long roleId : roleIdList) {
            SysRoleEntity sysRoleEntity = sysRoleMap.get(roleId);
            if (sysRoleEntity != null) {
                vo.add(sysRoleEntity.getName());
            }
        }
        return vo;
    }

    private Map<String, Object> getParams(SysUserQuery query) {
        Map<String, Object> params = new HashMap<>();
        params.put("username", query.getUsername());
        params.put("mobile", query.getMobile());
        params.put("gender", query.getGender());
        params.put("email", query.getEmail());
        // 数据权限
        params.put(Constant.DATA_SCOPE, getDataScope("t1", null));

        // 机构过滤
        if (query.getOrgId() != null) {
            // 查询子机构ID列表，包含本机构
            List<Long> orgList = sysOrgService.getSubOrgIdList(query.getOrgId());
            params.put("orgList", orgList);
        }

        return params;
    }

    /**
     * 检查邮箱唯一性
     * */
    private void checkEmailUnique(String email, Long userId) {
        if (ObjectUtil.isNotEmpty(email)){
            Long count = lambdaQuery().eq(SysUserEntity::getEmail, email).ne(userId != null, SysUserEntity::getId, userId).count();
            if (count!=null && count>0){
                throw new ServerException("邮箱已存在");
            }
        }
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(SysUserVO vo) {
        SysUserEntity entity = SysUserConvert.INSTANCE.convert(vo);
        entity.setSuperAdmin(SuperAdminEnum.NO.getValue());

        // 判断用户名是否存在
        SysUserEntity user = baseMapper.getByUsername(entity.getUsername());
        if (user != null) {
            throw new ServerException("用户名已经存在");
        }

        // 判断手机号是否存在
        user = baseMapper.getByMobile(entity.getMobile());
        if (user != null) {
            throw new ServerException("手机号已经存在");
        }
        //检查邮箱是否已存在
        checkEmailUnique(entity.getEmail(), null);

        // 检查 roleIdList 是否为空
        if (ObjectUtil.isEmpty(vo.getRoleIdList())) {
            throw new ServerException("所属角色不能为空");
        }

        // 保存用户
        baseMapper.insert(entity);

        // 保存用户角色关系
        sysUserRoleService.saveOrUpdate(entity.getId(), vo.getRoleIdList());

        // 更新用户岗位关系
        sysUserPostService.saveOrUpdate(entity.getId(), vo.getPostIdList());

        //保存VPS权限
        if (ObjectUtil.isNotEmpty(vo.getVpsList())){
            List<FollowVpsUserEntity> list=new ArrayList<>();
            vo.getVpsList().forEach(o->{
                FollowVpsUserEntity followVpsUserEntity = new FollowVpsUserEntity();
                followVpsUserEntity.setUserId(entity.getId());
                followVpsUserEntity.setVpsId(o.getId());
                followVpsUserEntity.setVpsName(o.getName());
                list.add(followVpsUserEntity);
            });
            followVpsUserService.saveBatch(list);
            redisCache.set(Constant.SYSTEM_VPS_USER+entity.getId(),vo.getVpsList());
        }
    }

    @Override
    public void update(SysUserVO vo) {
        SysUserEntity entity = SysUserConvert.INSTANCE.convert(vo);

        // 判断用户名是否存在
        SysUserEntity user = baseMapper.getByUsername(entity.getUsername());
        if (user != null && !user.getId().equals(entity.getId())) {
            throw new ServerException("用户名已经存在");
        }

        // 判断手机号是否存在
        user = baseMapper.getByMobile(entity.getMobile());
        if (user != null && !user.getId().equals(entity.getId())) {
            throw new ServerException("手机号已经存在");
        }
        //检查邮箱是否已存在
        checkEmailUnique(entity.getEmail(), entity.getId());
        // 更新用户
        updateById(entity);

        // 更新用户角色关系
        sysUserRoleService.saveOrUpdate(entity.getId(), vo.getRoleIdList());

        // 更新用户岗位关系
        sysUserPostService.saveOrUpdate(entity.getId(), vo.getPostIdList());

        // 更新用户缓存权限
        sysUserTokenService.updateCacheAuthByUserId(entity.getId());

        //保存VPS权限
        followVpsUserService.remove(new LambdaQueryWrapper<FollowVpsUserEntity>().eq(FollowVpsUserEntity::getUserId,entity.getId()));
        if (ObjectUtil.isNotEmpty(vo.getVpsList())){
            List<FollowVpsUserEntity> list=new ArrayList<>();
            vo.getVpsList().forEach(o->{
                FollowVpsUserEntity followVpsUserEntity = new FollowVpsUserEntity();
                followVpsUserEntity.setUserId(entity.getId());
                followVpsUserEntity.setVpsId(o.getId());
                followVpsUserEntity.setVpsName(o.getName());
                list.add(followVpsUserEntity);
            });
            followVpsUserService.saveBatch(list);
        }
        redisCache.delete(Constant.SYSTEM_VPS_USER+entity.getId());
    }

    @Override
    public void updateLoginInfo(SysUserBaseVO vo) {
        SysUserEntity entity = SysUserConvert.INSTANCE.convert(vo);
        // 设置登录用户ID
        entity.setId(SecurityUser.getUserId());

        // 判断手机号是否存在
        SysUserEntity user = baseMapper.getByMobile(entity.getMobile());
        if (user != null && !user.getId().equals(entity.getId())) {
            throw new ServerException("手机号已经存在");
        }

        // 更新用户
        updateById(entity);

        // 删除用户缓存
        tokenStoreCache.deleteUser(TokenUtils.getAccessToken());
    }

    @Override
    public void updateAvatar(SysUserAvatarVO avatar) {
        SysUserEntity entity = new SysUserEntity();
        entity.setId(SecurityUser.getUserId());
        entity.setAvatar(avatar.getAvatar());
        updateById(entity);

        // 删除用户缓存
        tokenStoreCache.deleteUser(TokenUtils.getAccessToken());
    }

    @Override
    public void delete(List<Long> idList) {
        // 删除用户
        removeByIds(idList);

        // 删除用户角色关系
        sysUserRoleService.deleteByUserIdList(idList);

        // 删除用户岗位关系
        sysUserPostService.deleteByUserIdList(idList);
    }

    @Override
    public List<String> getRealNameList(List<Long> idList) {
        if (idList.isEmpty()) {
            return null;
        }

        return baseMapper.selectBatchIds(idList).stream().map(SysUserEntity::getRealName).toList();
    }

    @Override
    public SysUserVO getByMobile(String mobile) {
        SysUserEntity user = baseMapper.getByMobile(mobile);

        return SysUserConvert.INSTANCE.convert(user);
    }

    @Override
    public void updatePassword(Long id, String newPassword) {
        // 修改密码
        SysUserEntity user = getById(id);
        user.setPassword(newPassword);

        updateById(user);
    }

    @Override
    public PageResult<SysUserVO> roleUserPage(SysRoleUserQuery query) {
        // 查询参数
        Map<String, Object> params = getParams(query);
        params.put("roleId", query.getRoleId());

        // 分页查询
        IPage<SysUserEntity> page = getPage(query);
        params.put(Constant.PAGE, page);

        // 数据列表
        List<SysUserEntity> list = baseMapper.getRoleUserList(params);

        return new PageResult<>(SysUserConvert.INSTANCE.convertList(list), page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void importByExcel(MultipartFile file, String password) {
        ExcelUtils.readAnalysis(file, SysUserExcelVO.class, new ExcelFinishCallBack<>() {
            @Override
            public void doSaveBatch(List<SysUserExcelVO> result) {
                ExcelUtils.parseDict(result);
                List<SysUserEntity> userList = SysUserConvert.INSTANCE.convertListEntity(result);
                userList.forEach(user -> user.setPassword(password));
                saveBatch(userList);
            }
        });
    }

    @Override
    @SneakyThrows
    public void export() {
        List<SysUserEntity> list = list(Wrappers.lambdaQuery(SysUserEntity.class).eq(SysUserEntity::getSuperAdmin, SuperAdminEnum.NO.getValue()));
        List<SysUserExcelVO> userExcelVOS = SysUserConvert.INSTANCE.convert2List(list);
        transService.transBatch(userExcelVOS);
        // 写到浏览器打开
        ExcelUtils.excelExport(SysUserExcelVO.class, "用户管理", null, userExcelVOS);
    }

    @Override
    public SysUserVO getByUsername(String username) {
        SysUserEntity user = baseMapper.selectOne(Wrappers.<SysUserEntity>lambdaQuery().eq(SysUserEntity::getUsername, username));
        return SysUserConvert.INSTANCE.convert(user);
    }

}

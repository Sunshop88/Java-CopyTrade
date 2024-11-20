package net.maku.mascontrol.controller;

import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import net.maku.followcom.convert.FollowVpsConvert;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.followcom.entity.FollowVpsEntity;
import net.maku.followcom.entity.FollowVpsUserEntity;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.enums.TraderTypeEnum;
import net.maku.followcom.query.FollowVpsQuery;
import net.maku.followcom.service.*;
import net.maku.followcom.vo.FollowVpsInfoVO;
import net.maku.followcom.vo.FollowVpsVO;
import net.maku.followcom.vo.VpsUserVO;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.common.utils.Result;
import net.maku.framework.operatelog.annotations.OperateLog;
import net.maku.framework.operatelog.enums.OperateTypeEnum;
import net.maku.framework.security.user.SecurityUser;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Objects;

/**
 * vps列表
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@RestController
@RequestMapping("/mascontrol/vps")
@Tag(name = "vps列表")
@AllArgsConstructor
public class FollowVpsController {
    private final FollowVpsService followVpsService;
    private final FollowTraderService followTraderService;
    private final FollowTraderSubscribeService followTraderSubscribeService;
    private final RedisCache redisCache;
    private final FollowVpsUserService followVpsUserService;
    private final MasControlService masControlService;

    @GetMapping("page")
    @Operation(summary = "分页")
    @PreAuthorize("hasAuthority('mascontrol:vps')")
    public Result<PageResult<FollowVpsVO>> page(@ParameterObject @Valid FollowVpsQuery query) {
        PageResult<FollowVpsVO> page = followVpsService.page(query);
        //策略数量
        page.getList().forEach(o -> {
            o.setTraderNum((int) followTraderService.count(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getIpAddr, o.getIpAddress())));
        });
        return Result.ok(page);
    }

    @GetMapping("{id}")
    @Operation(summary = "信息")
    @PreAuthorize("hasAuthority('mascontrol:vps')")
    public Result<FollowVpsVO> get(@PathVariable("id") Long id) {
        FollowVpsVO followVpsVO = followVpsService.get(id);

        return Result.ok(followVpsVO);
    }

    @PostMapping
    @Operation(summary = "保存")
    @OperateLog(type = OperateTypeEnum.INSERT)
    @PreAuthorize("hasAuthority('mascontrol:vps')")
    public Result<String> save(@RequestBody @Valid FollowVpsVO vo) {
        return masControlService.insert(vo) ? Result.ok() : Result.error();
    }

    @PutMapping
    @Operation(summary = "修改")
    @OperateLog(type = OperateTypeEnum.UPDATE)
    @PreAuthorize("hasAuthority('mascontrol:vps')")
    public Result<String> update(@RequestBody @Valid FollowVpsVO vo) {
        return masControlService.update(vo) ? Result.ok() : Result.error();
    }

    @DeleteMapping
    @Operation(summary = "删除")
    @OperateLog(type = OperateTypeEnum.DELETE)
    @PreAuthorize("hasAuthority('mascontrol:vps')")
    public Result<String> delete(@RequestBody List<Integer> idList) {
        //查看该vps是否还有用户
        List<FollowTraderEntity> list = followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().in(FollowTraderEntity::getServerId, idList));
        if (ObjectUtil.isNotEmpty(list)){
            throw new ServerException("请先清空vps内账户");
        }
        return masControlService.delete(idList) ? Result.ok() : Result.error();
    }

    @GetMapping("connect")
    @Operation(summary = "vps连接")
    @PreAuthorize("hasAuthority('mascontrol:vps')")
    public Result<Boolean> connect(@Parameter(description = "ipAddress") String ipAddress) {
        //进行连接
        try {
            InetAddress inet = InetAddress.getByName(ipAddress);
            boolean reachable = inet.isReachable(5000);
            if (!reachable) {
                return Result.error("地址错误,请检查");
            }
            // 检查端口 9001 是否可连接
            try (Socket socket = new Socket(ipAddress, 9001)) {
                // 如果可以建立连接，则返回成功
                return Result.ok(true);
            } catch (IOException e) {
                return Result.error("vps服务未启动");
            }
        } catch (UnknownHostException e) {
            throw new ServerException("地址错误,请检查");
        } catch (IOException e) {
            throw new ServerException("请求异常");
        }
    }

    @GetMapping("info")
    @Operation(summary = "vps统计")
    @PreAuthorize("hasAuthority('mascontrol:vps')")
    public Result<FollowVpsInfoVO> info() {
        return Result.ok(followVpsService.getFollowVpsInfo(followTraderService));
    }

    @GetMapping("listVps")
    @Operation(summary = "vps列表")
    @PreAuthorize("hasAuthority('mascontrol:vps')")
    public Result<List<FollowVpsVO>> listVps() {
        List<VpsUserVO> list;
        //除了admin都需要判断
        if (!ObjectUtil.equals(Objects.requireNonNull(SecurityUser.getUserId()).toString(), "10000")) {
            //查看当前用户拥有的vps
            if (ObjectUtil.isNotEmpty(redisCache.get(Constant.SYSTEM_VPS_USER + SecurityUser.getUserId()))) {
                list = (List<VpsUserVO>) redisCache.get(Constant.SYSTEM_VPS_USER + SecurityUser.getUserId());
            } else {
                List<FollowVpsUserEntity> vpsUserEntityList = followVpsUserService.list(new LambdaQueryWrapper<FollowVpsUserEntity>().eq(FollowVpsUserEntity::getUserId, SecurityUser.getUserId()));
                List<VpsUserVO> vpsUserVOS = convertoVpsUser(vpsUserEntityList);
                redisCache.set(Constant.SYSTEM_VPS_USER + SecurityUser.getUserId(), JSONObject.toJSON(vpsUserVOS));
                list = vpsUserVOS;
            }
        } else {
            list = followVpsService.list().stream().map(o -> {
                VpsUserVO vpsUserVO = new VpsUserVO();
                vpsUserVO.setName(o.getName());
                vpsUserVO.setId(o.getId());
                return vpsUserVO;
            }).toList();
        }
        if (ObjectUtil.isEmpty(list)) {
            return Result.ok(null);
        }
        List<FollowVpsEntity> listvps = followVpsService.list(new LambdaQueryWrapper<FollowVpsEntity>().in(FollowVpsEntity::getId, list.stream().map(o -> o.getId()).toList()).eq(FollowVpsEntity::getIsOpen, CloseOrOpenEnum.OPEN.getValue()).eq(FollowVpsEntity::getIsActive, CloseOrOpenEnum.OPEN.getValue()));
        List<FollowVpsVO> followVpsVOS = FollowVpsConvert.INSTANCE.convertList(listvps);
        followVpsVOS.forEach(o -> {
            o.setTraderNum((int) followTraderService.count(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getType, TraderTypeEnum.MASTER_REAL.getType()).eq(FollowTraderEntity::getIpAddr, o.getIpAddress())));
            o.setFollowNum((int) followTraderService.count(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getType, TraderTypeEnum.SLAVE_REAL.getType()).eq(FollowTraderEntity::getIpAddr, o.getIpAddress())));
        });
        return Result.ok(followVpsVOS);
    }

    @GetMapping("transferVps")
    @Operation(summary = "转移vps数据")
    @OperateLog(type = OperateTypeEnum.UPDATE)
    @PreAuthorize("hasAuthority('mascontrol:vps')")
    public Result<Boolean> transferVps(@Parameter(description = "oldId") Integer oldId, @Parameter(description = "newId") Integer newId, HttpServletRequest req) {
        //清理旧账号缓存
        followVpsService.transferVps(oldId, req);

        FollowVpsEntity followVpsEntity = followVpsService.getById(newId);
        //转移账号
        LambdaUpdateWrapper<FollowTraderEntity> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(FollowTraderEntity::getServerId, newId).
                set(FollowTraderEntity::getServerName, followVpsEntity.getName()).
                set(FollowTraderEntity::getIpAddr, followVpsEntity.getIpAddress()).
                eq(FollowTraderEntity::getServerId, oldId);
        followTraderService.update(updateWrapper);

        //发送请求到新VPS，启动账号
        followVpsService.startNewVps(newId, req);
        return Result.ok();
    }


    @GetMapping("deleteVps")
    @Operation(summary = "清除vps数据")
    @OperateLog(type = OperateTypeEnum.UPDATE)
    @PreAuthorize("hasAuthority('mascontrol:vps')")
    public Result<Boolean> deleteVps(@Parameter(description = "vpsId") Integer vpsId, HttpServletRequest req) {
        //清理旧账号缓存
        followVpsService.transferVps(vpsId, req);
        List<FollowTraderEntity> list = followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getServerId, vpsId));
        List<Long> idList = list.stream().map(FollowTraderEntity::getId).toList();
        //删除跟单关系
        followTraderSubscribeService.remove(new LambdaQueryWrapper<FollowTraderSubscribeEntity>().in(FollowTraderSubscribeEntity::getMasterId, idList).or().in(FollowTraderSubscribeEntity::getSlaveId, idList));
        //删除账号
        followTraderService.remove(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getServerId, vpsId));
        return Result.ok();
    }

    private List<VpsUserVO> convertoVpsUser(List<FollowVpsUserEntity> list) {
        return list.stream().map(o -> {
            VpsUserVO vpsUserVO = new VpsUserVO();
            vpsUserVO.setId(o.getVpsId());
            vpsUserVO.setName(o.getVpsName());
            return vpsUserVO;
        }).toList();
    }

    /**
     * 统计vps
     */
    @GetMapping("getStatByVpsId")
    @Operation(summary = "统计")
    @PreAuthorize("hasAuthority('mascontrol:vps')")
    public Result<List<List<BigDecimal>>> getStatByVpsId(@Parameter(description = "vpsId") Integer vpsId, @Parameter(description = "traderId") Long traderId) {

        return Result.ok(followVpsService.getStatByVpsId(vpsId, traderId, followTraderService));
    }

}
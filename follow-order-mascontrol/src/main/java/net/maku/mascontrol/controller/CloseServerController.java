package net.maku.mascontrol.controller;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import net.maku.followcom.convert.FollowVpsConvert;
import net.maku.followcom.entity.FollowVpsEntity;
import net.maku.followcom.service.*;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.util.RestUtil;
import net.maku.followcom.vo.*;
import net.maku.framework.common.utils.Result;
import net.maku.framework.operatelog.annotations.OperateLog;
import net.maku.framework.operatelog.enums.OperateTypeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/closeServer")
@Tag(name = "关闭服务")
@AllArgsConstructor
public class CloseServerController {
    private static final Logger log = LoggerFactory.getLogger(CloseServerController.class);
    private final FollowVpsService followVpsService;

    @DeleteMapping("shutdown")
    @Operation(summary = "关闭所有服务")
    @OperateLog(type = OperateTypeEnum.DELETE)
    @PreAuthorize("hasAuthority('mascontrol:bargain')")
    public Result<?> shutdown(@RequestParam("vpsIdList") List<Integer> vpsIdList, HttpServletRequest request) {
        List<FollowVpsVO> list;
        if (ObjectUtil.isEmpty(vpsIdList)) {
            list = followVpsService.listByVps();
        }else {
            list = FollowVpsConvert.INSTANCE.convertList(followVpsService.list(new LambdaQueryWrapper<FollowVpsEntity>().eq(FollowVpsEntity::getId,vpsIdList)).stream().toList());
        }
        HttpHeaders headerApplicationJsonAndToken = RestUtil.getHeaderApplicationJsonAndToken(request);
        list.forEach(o->{
            try {
                RestUtil.sendRequest(request, o.getIpAddress(), HttpMethod.DELETE, FollowConstant.SHUT_DOWN, null,headerApplicationJsonAndToken);
            }catch (Exception e){
                log.info("关闭VPS"+o.getIpAddress());
            }
        });
        return Result.ok();
    }
}

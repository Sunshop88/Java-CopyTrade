package net.maku.subcontrol.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import net.maku.followcom.entity.FollowVpsEntity;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.service.FollowVpsService;
import net.maku.followcom.util.FollowConstant;
import net.maku.framework.common.utils.Result;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/subcontrol/closeServer")
@Tag(name = "关闭服务")
@AllArgsConstructor
public class CloseServerController {
    private final FollowVpsService followVpsService;

    @DeleteMapping("shutdown")
    @Operation(summary = "关闭所有服务")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<?> shutdown() {
        //修改状态
        followVpsService.update(new LambdaUpdateWrapper<FollowVpsEntity>().set(FollowVpsEntity::getIsStop, CloseOrOpenEnum.OPEN.getValue()).eq(FollowVpsEntity::getIpAddress, FollowConstant.LOCAL_HOST));
        System.exit(0);
        return Result.ok();
    }
}

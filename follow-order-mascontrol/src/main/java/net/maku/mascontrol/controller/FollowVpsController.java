package net.maku.mascontrol.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.query.FollowVpsQuery;
import net.maku.followcom.service.FollowTraderService;
import net.maku.followcom.service.FollowVpsService;
import net.maku.followcom.vo.FollowVpsVO;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.common.utils.Result;
import net.maku.framework.operatelog.annotations.OperateLog;
import net.maku.framework.operatelog.enums.OperateTypeEnum;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;

/**
 * vps列表
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@RestController
@RequestMapping("/mascontrol/vps")
@Tag(name="vps列表")
@AllArgsConstructor
public class FollowVpsController {
    private final FollowVpsService followVpsService;
    private final FollowTraderService followTraderService;

    @GetMapping("page")
    @Operation(summary = "分页")
    @PreAuthorize("hasAuthority('mascontrol:vps')")
    public Result<PageResult<FollowVpsVO>> page(@ParameterObject @Valid FollowVpsQuery query){
        PageResult<FollowVpsVO> page = followVpsService.page(query);
        //策略数量
        page.getList().forEach(o->{
            o.setTraderNum((int)followTraderService.count(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getIpAddr,o.getIpAddress())));
        });
        return Result.ok(page);
    }

    @PostMapping
    @Operation(summary = "保存")
    @OperateLog(type = OperateTypeEnum.INSERT)
    @PreAuthorize("hasAuthority('mascontrol:vps')")
    public Result<String> save(@RequestBody FollowVpsVO vo){
        followVpsService.save(vo);

        return Result.ok();
    }

    @PutMapping
    @Operation(summary = "修改")
    @OperateLog(type = OperateTypeEnum.UPDATE)
    @PreAuthorize("hasAuthority('mascontrol:vps')")
    public Result<String> update(@RequestBody @Valid FollowVpsVO vo){
        followVpsService.update(vo);

        return Result.ok();
    }

    @DeleteMapping
    @Operation(summary = "删除")
    @OperateLog(type = OperateTypeEnum.DELETE)
    @PreAuthorize("hasAuthority('mascontrol:vps')")
    public Result<String> delete(@RequestBody List<Integer> idList){
        followVpsService.delete(idList);
        return Result.ok();
    }

}
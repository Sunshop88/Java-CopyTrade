package net.maku.mascontrol.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import net.maku.followcom.query.FollowOrderActiveQuery;
import net.maku.followcom.service.FollowOrderActiveService;
import net.maku.followcom.vo.FollowOrderActiveVO;
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
 * 账号持仓订单
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@RestController
@RequestMapping("/mascontrol/active")
@Tag(name="账号持仓订单")
@AllArgsConstructor
public class FollowOrderActiveController {
    private final FollowOrderActiveService followOrderActiveService;

    @GetMapping("page")
    @Operation(summary = "分页")
    @PreAuthorize("hasAuthority('mascontrol:active')")
    public Result<PageResult<FollowOrderActiveVO>> page(@ParameterObject @Valid FollowOrderActiveQuery query){
        PageResult<FollowOrderActiveVO> page = followOrderActiveService.page(query);

        return Result.ok(page);
    }


    @GetMapping("{id}")
    @Operation(summary = "信息")
    @PreAuthorize("hasAuthority('mascontrol:active')")
    public Result<FollowOrderActiveVO> get(@PathVariable("id") Long id){
        FollowOrderActiveVO data = followOrderActiveService.get(id);

        return Result.ok(data);
    }


    @PutMapping
    @Operation(summary = "修改")
    @OperateLog(type = OperateTypeEnum.UPDATE)
    @PreAuthorize("hasAuthority('mascontrol:active')")
    public Result<String> update(@RequestBody @Valid FollowOrderActiveVO vo){
        followOrderActiveService.update(vo);

        return Result.ok();
    }

    @DeleteMapping
    @Operation(summary = "删除")
    @OperateLog(type = OperateTypeEnum.DELETE)
    @PreAuthorize("hasAuthority('mascontrol:active')")
    public Result<String> delete(@RequestBody List<Long> idList){
        followOrderActiveService.delete(idList);

        return Result.ok();
    }

    @GetMapping("export")
    @Operation(summary = "导出")
    @OperateLog(type = OperateTypeEnum.EXPORT)
    @PreAuthorize("hasAuthority('mascontrol:active')")
    public void export() {
        followOrderActiveService.export();
    }

}
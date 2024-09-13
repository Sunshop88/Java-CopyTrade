package net.maku.mascontrol.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.common.utils.Result;
import net.maku.framework.operatelog.annotations.OperateLog;
import net.maku.framework.operatelog.enums.OperateTypeEnum;
import net.maku.mascontrol.convert.FollowPlatformConvert;
import net.maku.mascontrol.entity.FollowPlatformEntity;
import net.maku.mascontrol.query.FollowPlatformQuery;
import net.maku.mascontrol.service.FollowPlatformService;
import net.maku.mascontrol.vo.FollowPlatformVO;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
* 平台管理
*
* @author
* @since 1.0.0 2024-09-11
*/
@RestController
@RequestMapping("/mascontrol/platform")
@Tag(name="平台管理")
@AllArgsConstructor
public class FollowPlatformController {
    private final FollowPlatformService followPlatformService;

    @GetMapping("page")
    @Operation(summary = "分页")
    @PreAuthorize("hasAuthority('mascontrol:platform')")
    public Result<PageResult<FollowPlatformVO>> page(@ParameterObject @Valid FollowPlatformQuery query){
        PageResult<FollowPlatformVO> page = followPlatformService.page(query);

        return Result.ok(page);
    }

    @GetMapping("{id}")
    @Operation(summary = "信息")
    @PreAuthorize("hasAuthority('mascontrol:platform')")
    public Result<FollowPlatformVO> get(@PathVariable("id") Long id){
        FollowPlatformEntity entity = followPlatformService.getById(id);

        return Result.ok(FollowPlatformConvert.INSTANCE.convert(entity));
    }

    @PostMapping
    @Operation(summary = "保存")
    @OperateLog(type = OperateTypeEnum.INSERT)
    @PreAuthorize("hasAuthority('mascontrol:platform')")
    public Result<String> save(@RequestBody FollowPlatformVO vo){
        followPlatformService.save(vo);

        return Result.ok();
    }

    @PutMapping
    @Operation(summary = "修改")
    @OperateLog(type = OperateTypeEnum.UPDATE)
    @PreAuthorize("hasAuthority('mascontrol:platform')")
    public Result<String> update(@RequestBody @Valid FollowPlatformVO vo){
        followPlatformService.update(vo);

        return Result.ok();
    }



    @DeleteMapping
    @Operation(summary = "删除")
    @OperateLog(type = OperateTypeEnum.DELETE)
    @PreAuthorize("hasAuthority('mascontrol:platform')")
    public Result<String> delete(@RequestBody List<Long> idList) {
        followPlatformService.delete(idList);

        return Result.ok();
    }

    @GetMapping("export")
    @Operation(summary = "导出")
    @OperateLog(type = OperateTypeEnum.EXPORT)
    @PreAuthorize("hasAuthority('mascontrol:platform')")
    public void export() {
        followPlatformService.export();
    }


//    @GetMapping("/brokeName")
//    @Operation(summary = "查询券商名称" )
//    @PreAuthorize("hasAuthority('mascontrol:platform')")
//    //查询所有的券商名称
//    public Result<List<String>> brokeName(@RequestBody List<Long> idList){
//        List<String> list = followPlatformService.getBrokeName(idList);
//        return Result.ok(list);
//    }


    @GetMapping("list")
    @Operation(summary = "查询列表")
    public Result<List<FollowPlatformVO>> list(){
        List<FollowPlatformVO> list = followPlatformService.getList();

        return Result.ok(list);
    }
}
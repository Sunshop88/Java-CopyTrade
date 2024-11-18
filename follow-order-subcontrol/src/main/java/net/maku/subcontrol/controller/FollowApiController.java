package net.maku.subcontrol.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import net.maku.followcom.vo.*;
import net.maku.framework.common.utils.Result;
import net.maku.subcontrol.service.FollowApiService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "喊单，跟单api")
@AllArgsConstructor
public class FollowApiController {
    private final FollowApiService followApiService;

    @PostMapping("/source/insert")
    @Operation(summary = "喊单添加")
    public Result<Boolean> insertSource(@RequestBody @Valid SourceInsertVO vo) {
        return followApiService.insertSource(vo) ? Result.ok() : Result.error();
    }

    @PostMapping("/source/update")
    @Operation(summary = "喊单编辑")
    public Result<Boolean> updateSource(@RequestBody @Valid SourceUpdateVO vo) {
        return followApiService.updateSource(vo) ? Result.ok() : Result.error();
    }

    @PostMapping("/source/delete")
    @Operation(summary = "喊单删除")
    public Result<Boolean> delSource(@RequestBody @Valid SourceDelVo vo) {

        return followApiService.delSource(vo) ? Result.ok() : Result.error();
    }


    @PostMapping("/follow/insert")
    @Operation(summary = "跟单添加")
    public Result<String> insertFollow(@RequestBody @Valid FollowInsertVO vo) {

        return followApiService.insertFollow(vo) ? Result.ok() : Result.error();
    }

    @PostMapping("/follow/update")
    @Operation(summary = "跟单编辑")
    public Result<String> updateFollow(@RequestBody @Valid FollowUpdateVO vo) {

        return followApiService.updateFollow(vo) ? Result.ok() : Result.error();
    }

    @PostMapping("/follow/delete")
    @Operation(summary = "跟单编辑")
    public Result<String> delFollow(@RequestBody @Valid SourceDelVo vo) {

        return followApiService.delFollow(vo) ? Result.ok() : Result.error();
    }

}

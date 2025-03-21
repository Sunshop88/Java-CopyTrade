package net.maku.subcontrol.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import net.maku.followcom.vo.*;
import net.maku.framework.common.utils.Result;
import net.maku.subcontrol.service.FollowApiService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Tag(name = "喊单，跟单api")
@AllArgsConstructor
public class FollowApiController {
    private final FollowApiService followApiService;

    @PostMapping("/source/insert")
    @Operation(summary = "喊单添加")
    public Result<Integer> insertSource(@RequestBody @Valid SourceInsertVO vo) {
        Integer id = followApiService.insertSource(vo);
        return id!=null ? Result.ok(id) : Result.error();
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
    public Result<Integer> insertFollow(@RequestBody @Valid FollowInsertVO vo) {
        Integer id = followApiService.insertFollow(vo);
        return id!=null ? Result.ok(id) : Result.error();

    }

    @PostMapping("/follow/update")
    @Operation(summary = "跟单编辑")
    public Result<String> updateFollow(@RequestBody @Valid FollowUpdateVO vo) {

        return followApiService.updateFollow(vo) ? Result.ok() : Result.error();
    }

    @PostMapping("/follow/delete")
    @Operation(summary = "跟单删除")
    public Result<String> delFollow(@RequestBody @Valid SourceDelVo vo) {

        return followApiService.delFollow(vo) ? Result.ok() : Result.error();
    }

    @PostMapping("/orderCloseList")
    @Operation(summary = "查询平仓订单")
    public Result<OrderClosePageVO> orderCloseList(@RequestBody @Valid OrderHistoryVO vo) {

        return  Result.ok(followApiService.orderCloseList(vo)) ;
    }

    @PostMapping("/orderSend")
    @Operation(summary = "开仓")
    public Result<Boolean> orderSend(@RequestBody @Valid OrderSendVO vo) {

        return  Result.ok(followApiService.orderSend(vo)) ;
    }

    @PostMapping("/orderclose")
    @Operation(summary = "平仓")
    public Result<Boolean> orderClose(@RequestBody @Valid OrderCloseVO vo) {
        return  Result.ok(followApiService.orderClose(vo)) ;
    }
    @PostMapping("/orderCloseAll")
    @Operation(summary = "平仓")
    public Result<Boolean> orderCloseAll(@RequestBody @Valid OrderCloseAllVO vo) {
        return  Result.ok(followApiService.orderCloseAll(vo)) ;
    }

    @PostMapping("/orderCloseProfit")
    @Operation(summary = "平仓盈利")
    public Result<Boolean> orderCloseProfit(@RequestBody @Valid OrderCloseAllVO vo) {
        return  Result.ok(followApiService.orderCloseProfit(vo)) ;
    }

    @PostMapping("/orderCloseLoss")
    @Operation(summary = "平仓亏损")
    public Result<Boolean> orderCloseLoss(@RequestBody @Valid OrderCloseAllVO vo) {
        return  Result.ok(followApiService.orderCloseLoss(vo)) ;
    }

    @PostMapping("/changepassword")
    @Operation(summary = "修改密码")
    public Result<Boolean> changePassword(@RequestBody @Valid ChangePasswordVO vo) {
        return  Result.ok(followApiService.changePassword(vo)) ;
    }

    @GetMapping("/symbolParams")
    @Operation(summary = "品种规格")
    public Result<ExternalSysmbolSpecificationVO> symbolParams(@RequestParam("accountId") Long accountId,@RequestParam("accountType") Integer accountType) {
        return  Result.ok(followApiService.symbolParams(accountId,accountType)) ;
    }
}

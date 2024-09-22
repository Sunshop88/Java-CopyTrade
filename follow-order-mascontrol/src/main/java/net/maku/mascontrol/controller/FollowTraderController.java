package net.maku.mascontrol.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import net.maku.followcom.query.FollowOrderSendQuery;
import net.maku.followcom.query.FollowTraderQuery;
import net.maku.followcom.service.FollowOrderSendService;
import net.maku.followcom.service.FollowTraderService;
import net.maku.followcom.vo.FollowOrderSendVO;
import net.maku.followcom.vo.FollowOrderSlipPointVO;
import net.maku.followcom.vo.FollowTraderVO;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.common.utils.Result;
import net.maku.framework.operatelog.annotations.OperateLog;
import net.maku.framework.operatelog.enums.OperateTypeEnum;
import net.maku.followcom.query.FollowOrderSendListQuery;
import net.maku.followcom.query.FollowOrderSpliListQuery;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;

/**
 * mt4账号
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@RestController
@RequestMapping("/mascontrol/trader")
@Tag(name="mt4账号")
@AllArgsConstructor
public class FollowTraderController {
    private final FollowTraderService followTraderService;
    private final FollowOrderSendService followOrderSendService;

    @GetMapping("page")
    @Operation(summary = "分页")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<PageResult<FollowTraderVO>> page(@ParameterObject @Valid FollowTraderQuery query){
        PageResult<FollowTraderVO> page = followTraderService.page(query);

        return Result.ok(page);
    }


    @GetMapping("{id}")
    @Operation(summary = "信息")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<FollowTraderVO> get(@PathVariable("id") Long id){
        FollowTraderVO data = followTraderService.get(id);

        return Result.ok(data);
    }

    @PostMapping
    @Operation(summary = "保存")
    @OperateLog(type = OperateTypeEnum.INSERT)
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<String> save(@RequestBody FollowTraderVO vo){
        followTraderService.save(vo);

        return Result.ok();
    }

    @PutMapping
    @Operation(summary = "修改")
    @OperateLog(type = OperateTypeEnum.UPDATE)
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<String> update(@RequestBody @Valid FollowTraderVO vo){
        followTraderService.update(vo);

        return Result.ok();
    }

    @DeleteMapping
    @Operation(summary = "删除")
    @OperateLog(type = OperateTypeEnum.DELETE)
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<String> delete(@RequestBody List<Long> idList){
        followTraderService.delete(idList);

        return Result.ok();
    }


    @GetMapping("export")
    @Operation(summary = "导出")
    @OperateLog(type = OperateTypeEnum.EXPORT)
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public void export() {
        followTraderService.export();
    }


    @PostMapping("orderSend")
    @Operation(summary = "下单")
    @OperateLog(type = OperateTypeEnum.INSERT)
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<Boolean> save(@RequestBody FollowOrderSendVO vo){
        boolean result = followTraderService.orderSend(vo);
        return Result.ok(result);
    }

    @GetMapping("orderSendList")
    @Operation(summary = "下单列表")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<PageResult<FollowOrderSendVO>> orderSendList(@ParameterObject @Valid FollowOrderSendQuery query) {
        return  Result.ok(followOrderSendService.page(query));
    }

    @GetMapping("orderSlipPoint")
    @Operation(summary = "滑点分析列表")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public  Result<PageResult<FollowOrderSlipPointVO>>  expoorderSlipPointrt(@ParameterObject @Valid FollowOrderSpliListQuery query) {
       return Result.ok(followTraderService.pageSlipPoint(query));
    }

    @GetMapping("orderSlipDetail")
    @Operation(summary = "订单详情")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public void orderSlipDetail(@ParameterObject @Valid FollowOrderSendListQuery query) {
//        followTraderService.orderSlipDetail(query);
    }

}
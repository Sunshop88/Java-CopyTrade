package net.maku.mascontrol.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import net.maku.followcom.service.FollowBrokeServerService;
import net.maku.followcom.service.FollowVpsService;
import net.maku.followcom.vo.FollowBrokeServerVO;
import net.maku.followcom.vo.FollowVpsVO;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.common.utils.Result;
import net.maku.framework.operatelog.annotations.OperateLog;
import net.maku.framework.operatelog.enums.OperateTypeEnum;
import net.maku.framework.security.user.SecurityUser;
import net.maku.mascontrol.convert.FollowTestDetailConvert;
import net.maku.mascontrol.convert.FollowTestSpeedConvert;
import net.maku.mascontrol.entity.FollowTestDetailEntity;
import net.maku.mascontrol.entity.FollowTestSpeedEntity;
import net.maku.mascontrol.entity.MeasureRequestEntity;
import net.maku.mascontrol.query.FollowTestDetailQuery;
import net.maku.mascontrol.service.FollowTestDetailService;
import net.maku.mascontrol.service.FollowTestSpeedService;
import net.maku.mascontrol.query.FollowTestSpeedQuery;
import net.maku.mascontrol.vo.FollowTestDetailVO;
import net.maku.mascontrol.vo.FollowTestSpeedVO;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 测速记录
 */
@RestController
@RequestMapping("/mascontrol/speed")
@Tag(name = "测速记录")
@AllArgsConstructor
public class FollowTestSpeedController {
    private final FollowTestSpeedService followTestSpeedService;
    private final FollowTestDetailService followTestDetailService;
    private final FollowBrokeServerService followBrokeServerService;
    private final FollowVpsService followVpsService;

    @GetMapping("{id}")
    @Operation(summary = "信息")
    @PreAuthorize("hasAuthority('mascontrol:speed')")
    public Result<FollowTestSpeedVO> get(@PathVariable("id") Long id) {
        FollowTestSpeedVO data = followTestSpeedService.get(id);

        return Result.ok(data);
    }

    @PostMapping
    @Operation(summary = "保存")
    @OperateLog(type = OperateTypeEnum.INSERT)
    @PreAuthorize("hasAuthority('mascontrol:speed')")
    public Result<String> save(@RequestBody FollowTestSpeedVO vo) {
        followTestSpeedService.save(vo);

        return Result.ok();
    }

    @PutMapping
    @Operation(summary = "修改")
    @OperateLog(type = OperateTypeEnum.UPDATE)
    @PreAuthorize("hasAuthority('mascontrol:speed')")
    public Result<String> update(@RequestBody @Valid FollowTestSpeedVO vo) {
        followTestSpeedService.update(vo);

        return Result.ok();
    }

    @DeleteMapping
    @Operation(summary = "删除")
    @OperateLog(type = OperateTypeEnum.DELETE)
    @PreAuthorize("hasAuthority('mascontrol:speed')")
    public Result<String> delete(@RequestBody List<Long> idList) {
        followTestSpeedService.delete(idList);

        return Result.ok();
    }


    @GetMapping("export")
    @Operation(summary = "导出")
    @OperateLog(type = OperateTypeEnum.EXPORT)
    @PreAuthorize("hasAuthority('mascontrol:speed')")
    public void export() {
        followTestSpeedService.export();
    }



    @PostMapping("measure")
    @Operation(summary = "测速")
    @PreAuthorize("hasAuthority('mascontrol:speed')")
    public Result<FollowTestSpeedVO> measure(@RequestBody MeasureRequestEntity request) {
        List<String> servers = request.getServers();
        List<String> vps = request.getVps();
        // 批量调用服务进行测速
        followTestSpeedService.measure(servers, vps);

        return Result.ok();
    }


    @GetMapping("listTestSpeed")
    @Operation(summary = "测速记录列表")
    @PreAuthorize("hasAuthority('mascontrol:speed')")
    public Result<PageResult<FollowTestSpeedVO>> page(@ParameterObject @Valid FollowTestSpeedQuery query) {
        PageResult<FollowTestSpeedVO> page = followTestSpeedService.page(query);

        return Result.ok(page);
    }

    @PostMapping("remeasure")
    @Operation(summary = "重新测速")
    @PreAuthorize("hasAuthority('mascontrol:speed')")
    public Result<FollowTestDetailVO> remeasure(@RequestParam Long id, @RequestBody MeasureRequestEntity request) {
        List<String> servers = request.getServers();
        List<String> vps = request.getVps();
        // 批量调用服务进行测速
        followTestSpeedService.remeasure(id,servers, vps);

        return Result.ok();
    }

    @GetMapping("listTestDetail")
    @Operation(summary = "测速详情")
    @PreAuthorize("hasAuthority('mascontrol:speed')")
    public List<List<Object>> listTestDetail(@ParameterObject @Valid FollowTestDetailQuery query) {
        return followTestDetailService.page(query);
    }

    @GetMapping("listServerAndVps")
    @Operation(summary = "查询服务器和vps清单")
    @PreAuthorize("hasAuthority('mascontrol:speed')")
    public Result<List<FollowTestDetailVO>> listServerAndVps() {
        List<FollowTestDetailVO> list = followTestDetailService.listServerAndVps();

        return Result.ok(list);
    }

    @GetMapping("listServer")
    @Operation(summary = "查询服务器清单")
    @PreAuthorize("hasAuthority('mascontrol:speed')")
    public Result<List<FollowBrokeServerVO>> listServer() {
        List<FollowBrokeServerVO> list = followBrokeServerService.listByServer();

        return Result.ok(list);
    }

    @GetMapping("listVps")
    @Operation(summary = "查询vps清单")
    @PreAuthorize("hasAuthority('mascontrol:speed')")
    public Result<List<FollowVpsVO>> listVps() {
        List<FollowVpsVO> list = followVpsService.listByVps();

        return Result.ok(list);
    }
}
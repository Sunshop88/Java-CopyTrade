package net.maku.mascontrol.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import net.maku.followcom.query.FollowTraderLogQuery;
import net.maku.followcom.service.*;
import net.maku.followcom.vo.FollowTraderLogVO;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.common.utils.Result;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.*;

/**
 * 交易日志列表
 */
@RestController
@RequestMapping("/mascontrol/traderLog")
@Tag(name="交易日志")
@AllArgsConstructor
public class FollowTraderLogController {

    private final FollowTraderLogService followTraderLogService;
    @GetMapping("page")
    @Operation(summary = "分页")
    public Result<PageResult<FollowTraderLogVO>> page(@ParameterObject @Valid FollowTraderLogQuery query){
        PageResult<FollowTraderLogVO> page = followTraderLogService.page(query);
        return Result.ok(page);
    }
}
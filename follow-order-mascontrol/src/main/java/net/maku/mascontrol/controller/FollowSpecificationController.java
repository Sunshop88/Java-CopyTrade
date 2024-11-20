package net.maku.mascontrol.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import net.maku.followcom.query.FollowSysmbolSpecificationQuery;
import net.maku.followcom.service.FollowSysmbolSpecificationService;
import net.maku.followcom.vo.FollowSysmbolSpecificationVO;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.common.utils.Result;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Author:  zsd
 * Date:  2024/11/20/周三 15:15
 */
@RestController
@RequestMapping("/mascontrol/specification")
@Tag(name = "品种规格")
@AllArgsConstructor
public class FollowSpecificationController {
    private final FollowSysmbolSpecificationService followSysmbolSpecificationService;

    @GetMapping("page")
    @Operation(summary = "分页")
    public Result<PageResult<FollowSysmbolSpecificationVO>> page(@ParameterObject @Valid FollowSysmbolSpecificationQuery query) {
        PageResult<FollowSysmbolSpecificationVO> page = followSysmbolSpecificationService.page(query);
        return Result.ok(page);
    }

}

package net.maku.subcontrol.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
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

    @DeleteMapping("shutdown")
    @Operation(summary = "关闭所有服务")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<?> shutdown() {
        System.exit(0);
        return Result.ok();
    }
}

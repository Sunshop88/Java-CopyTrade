package net.maku.mascontrol.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import net.maku.followcom.query.FollowTraderUserQuery;
import net.maku.followcom.service.FollowTraderUserService;
import net.maku.followcom.vo.FollowTraderUserVO;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.common.utils.Result;
import net.maku.framework.operatelog.annotations.OperateLog;
import net.maku.framework.operatelog.enums.OperateTypeEnum;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * 账号初始表
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@RestController
@RequestMapping("/mascontrol/user")
@Tag(name="账号初始表")
@AllArgsConstructor
public class FollowTraderUserController {
    private final FollowTraderUserService followTraderUserService;

    @GetMapping("page")
    @Operation(summary = "分页")
    @PreAuthorize("hasAuthority('mascontrol:traderUser')")
    public Result<PageResult<FollowTraderUserVO>> page(@ParameterObject @Valid FollowTraderUserQuery query){
        PageResult<FollowTraderUserVO> page = followTraderUserService.page(query);

        return Result.ok(page);
    }


    @GetMapping("{id}")
    @Operation(summary = "信息")
    @PreAuthorize("hasAuthority('mascontrol:traderUser')")
    public Result<FollowTraderUserVO> get(@PathVariable("id") Long id){
        FollowTraderUserVO data = followTraderUserService.get(id);

        return Result.ok(data);
    }

    @PostMapping
    @Operation(summary = "保存")
    @OperateLog(type = OperateTypeEnum.INSERT)
    @PreAuthorize("hasAuthority('mascontrol:traderUser')")
    public Result<String> save(@RequestBody FollowTraderUserVO vo){
        followTraderUserService.save(vo);

        return Result.ok();
    }

    @PutMapping
    @Operation(summary = "修改")
    @OperateLog(type = OperateTypeEnum.UPDATE)
    @PreAuthorize("hasAuthority('mascontrol:traderUser')")
    public Result<String> update(@RequestBody @Valid FollowTraderUserVO vo){
        followTraderUserService.update(vo);

        return Result.ok();
    }

    @DeleteMapping
    @Operation(summary = "删除")
    @OperateLog(type = OperateTypeEnum.DELETE)
    @PreAuthorize("hasAuthority('mascontrol:traderUser')")
    public Result<String> delete(@RequestBody List<Long> idList){
        followTraderUserService.delete(idList);

        return Result.ok();
    }

    @PostMapping("import")
    @Operation(summary = "导入")
    @OperateLog(type = OperateTypeEnum.IMPORT)
    @PreAuthorize("hasAuthority('mascontrol:traderUser')")
    public Result<String> importExcel(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Result.error("请选择需要上传的文件");
        }
        followTraderUserService.importByExcel(file);

        return Result.ok();
    }

    @GetMapping("export")
    @Operation(summary = "导出")
    @OperateLog(type = OperateTypeEnum.EXPORT)
    @PreAuthorize("hasAuthority('mascontrol:traderUser')")
    public void export() {
        followTraderUserService.export();
    }

    @GetMapping("download")
    @Operation(summary = "下载模板")
    @OperateLog(type = OperateTypeEnum.EXPORT)
    @PreAuthorize("hasAuthority('mascontrol:traderUser')")
    public ResponseEntity<byte[]> generateCsv() {
        try {
            // 使用 ByteArrayOutputStream 来生成 CSV 数据
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            followTraderUserService.generateCsv(outputStream);

            // 设置响应头
            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Disposition", "attachment; filename=export.csv");
            headers.add("Content-Type", "text/csv");

            return new ResponseEntity<>(outputStream.toByteArray(), headers, HttpStatus.OK);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("导出CSV时出错".getBytes());
        }
    }
}
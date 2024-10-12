package net.maku.mascontrol.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import net.maku.framework.common.utils.ExcelUtils;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.common.utils.Result;
import net.maku.framework.operatelog.annotations.OperateLog;
import net.maku.framework.operatelog.enums.OperateTypeEnum;
import net.maku.mascontrol.service.FollowVarietyService;
import net.maku.mascontrol.query.FollowVarietyQuery;
import net.maku.mascontrol.vo.FollowVarietyExcelVO;
import net.maku.mascontrol.vo.FollowVarietySymbolVO;
import net.maku.mascontrol.vo.FollowVarietyVO;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 品种匹配
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@RestController
@RequestMapping("/mascontrol/variety")
@Tag(name="品种匹配")
@AllArgsConstructor
public class FollowVarietyController {
    private final FollowVarietyService followVarietyService;

    @GetMapping("page")
    @Operation(summary = "分页")
    @PreAuthorize("hasAuthority('mascontrol:variety')")
    public Result<PageResult<FollowVarietyVO>> page(@ParameterObject @Valid FollowVarietyQuery query){
        PageResult<FollowVarietyVO> page = followVarietyService.page(query);

        return Result.ok(page);
    }


    @GetMapping("{id}")
    @Operation(summary = "信息")
    @PreAuthorize("hasAuthority('mascontrol:variety')")
    public Result<FollowVarietyVO> get(@PathVariable("id") Long id){
        FollowVarietyVO data = followVarietyService.get(id);

        return Result.ok(data);
    }

    @PostMapping
    @Operation(summary = "保存")
    @OperateLog(type = OperateTypeEnum.INSERT)
    @PreAuthorize("hasAuthority('mascontrol:variety')")
    public Result<String> save(@RequestBody FollowVarietyVO vo){
        followVarietyService.save(vo);

        return Result.ok();
    }

    @PutMapping
    @Operation(summary = "修改")
    @OperateLog(type = OperateTypeEnum.UPDATE)
    @PreAuthorize("hasAuthority('mascontrol:variety')")
    public Result<String> update(@RequestBody @Valid FollowVarietyVO vo){
        followVarietyService.update(vo);

        return Result.ok();
    }

    @DeleteMapping
    @Operation(summary = "删除")
    @OperateLog(type = OperateTypeEnum.DELETE)
    @PreAuthorize("hasAuthority('mascontrol:variety')")
    public Result<String> delete(@RequestBody List<Long> idList){
        followVarietyService.delete(idList);

        return Result.ok();
    }

    @PostMapping("import")
    @Operation(summary = "导入")
    @OperateLog(type = OperateTypeEnum.IMPORT)
    @PreAuthorize("hasAuthority('mascontrol:variety')")
    public Result<String> importExcel(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            return Result.error("请选择需要上传的文件");
        }

        try {
            // 检查文件类型
            if (!isExcelOrCsv(file.getOriginalFilename())) {
                return Result.error("仅支持 Excel 和 CSV 文件");
            }

            // 导入文件
            followVarietyService.importByExcel(file);

            return Result.ok("文件导入成功");
        } catch (Exception e) {
            return Result.error("文件导入失败：" + e.getMessage());
        }
    }

    /**
     * 检查文件是否为 Excel 或 CSV 格式
     */
    private boolean isExcelOrCsv(String filename) {
        if (filename == null || filename.isEmpty()) {
            return false;
        }

        String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        return extension.equals("xlsx") || extension.equals("xls") || extension.equals("csv");
    }



    @GetMapping("export")
    @Operation(summary = "数据导出")
    @OperateLog(type = OperateTypeEnum.EXPORT)
    @PreAuthorize("hasAuthority('mascontrol:variety')")
    public ResponseEntity<byte[]> export() {
        try {
            // 使用 ByteArrayOutputStream 来生成 CSV 数据
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            followVarietyService.exportCsv(outputStream);

            // 设置响应头
            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Disposition", "attachment; filename=export.csv");
            headers.add("Content-Type", "text/csv");

            return new ResponseEntity<>(outputStream.toByteArray(), headers, HttpStatus.OK);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("导出CSV时出错".getBytes());
        }
    }

    @GetMapping("download")
    @Operation(summary = "下载模板")
    @OperateLog(type = OperateTypeEnum.EXPORT)
    @PreAuthorize("hasAuthority('mascontrol:variety')")
    public void exportData(HttpServletResponse response) throws Exception {
        followVarietyService.download(response);
    }


    @GetMapping("listSmybol")
    @Operation(summary = "查询标准品种")
    @PreAuthorize("hasAuthority('mascontrol:variety')")
    public Result<PageResult<FollowVarietyVO>> listSmybol(@ParameterObject @Valid FollowVarietyQuery query){
        PageResult<FollowVarietyVO> list = followVarietyService.pageSmybol(query);
        return Result.ok(list);
    }

    @GetMapping("listBySymbol")
    @Operation(summary = "根据标准品种查询其他信息")
    @PreAuthorize("hasAuthority('mascontrol:variety')")
    public Result<PageResult<FollowVarietyVO>> listBySymbol(@ParameterObject @Valid FollowVarietyQuery query){
        PageResult<FollowVarietyVO> list = followVarietyService.pageSmybolList(query);
        return Result.ok(list);
    }
}
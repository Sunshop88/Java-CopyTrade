package net.maku.mascontrol.controller;

import cn.hutool.core.util.ObjectUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import net.maku.followcom.entity.FollowPlatformEntity;
import net.maku.followcom.entity.FollowVarietyEntity;
import net.maku.followcom.query.FollowVarietyQuery;
import net.maku.followcom.service.FollowPlatformService;
import net.maku.followcom.service.FollowVarietyService;
import net.maku.followcom.vo.FollowVarietyVO;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.common.utils.Result;
import net.maku.framework.operatelog.annotations.OperateLog;
import net.maku.framework.operatelog.enums.OperateTypeEnum;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

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
    private final FollowPlatformService followPlatformService;
    private final RedisCache redisCache;

    @GetMapping("pageSymbol")
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
    public Result<String> importExcel(@RequestParam("file") MultipartFile file,@RequestParam("template")Integer template,@RequestParam("templateName") String templateName) throws Exception {
//        if (file.isEmpty()) {
//            return Result.error("请选择需要上传的文件");
//        }
        try {
            // 检查文件类型
            if (!isExcelOrCsv(file.getOriginalFilename())) {
                return Result.error("仅支持 Excel 和 CSV 文件");
            }
            // 导入文件
            followVarietyService.importByExcel(file,template,templateName);
            redisCache.deleteByPattern(Constant.TRADER_VARIETY);
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

    @PostMapping("addTemplate")
    @Operation(summary = "新增模板")
    @OperateLog(type = OperateTypeEnum.IMPORT)
    @PreAuthorize("hasAuthority('mascontrol:variety')")
    public Result<String> addExcel(@RequestParam("file") MultipartFile file,@RequestParam("templateName") String templateName) throws Exception {
        if (file.isEmpty()) {
            return Result.error("请选择需要上传的文件");
        }
        try {
            // 检查文件类型
            if (!isExcelOrCsv(file.getOriginalFilename())) {
                return Result.error("仅支持 Excel 和 CSV 文件");
            }
            // 导入文件
            followVarietyService.addByExcel(file,templateName);
            return Result.ok("文件导入成功");
        } catch (Exception e) {
            return Result.error("文件导入失败：" + e.getMessage());
        }
    }

    @GetMapping("export")
    @Operation(summary = "数据导出")
    @OperateLog(type = OperateTypeEnum.EXPORT)
    @PreAuthorize("hasAuthority('mascontrol:variety')")
    public ResponseEntity<byte[]> export(@RequestParam("template")Integer template) {
        try {
            // 使用 ByteArrayOutputStream 来生成 CSV 数据
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            followVarietyService.exportCsv(outputStream,template);

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
    public ResponseEntity<byte[]> generateCsv() {
        try {
            // 使用 ByteArrayOutputStream 来生成 CSV 数据
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            followVarietyService.generateCsv(outputStream);

            // 设置响应头
            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Disposition", "attachment; filename=export.csv");
            headers.add("Content-Type", "text/csv");

            return new ResponseEntity<>(outputStream.toByteArray(), headers, HttpStatus.OK);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("导出CSV时出错".getBytes());
        }
    }



    @GetMapping("listSmybol")
    @Operation(summary = "查询标准品种，标准合约")
    @PreAuthorize("hasAuthority('mascontrol:variety')")
    public Result<PageResult<String[]>> listSmybol(@ParameterObject @Valid FollowVarietyQuery query) {
        PageResult<FollowVarietyVO> list = followVarietyService.pageSmybol(query);
        List<FollowPlatformEntity> followPlatformEntityList = followPlatformService.list();
        // 根据 template 过滤 FollowVarietyEntity
        List<FollowVarietyEntity> followVarietyEntityList = followVarietyService.list()
                .stream()
                .filter(entity -> entity.getTemplateId() != null && entity.getTemplateId().equals(query.getTemplate()))
                .collect(Collectors.toList());

        // 构建映射关系：<StdSymbol + BrokerName, BrokerSymbol列表>
        Map<String, List<String>> varietyMap = followVarietyEntityList.stream()
                .collect(Collectors.groupingBy(
                        va -> va.getStdSymbol() + "_" + va.getBrokerName(),
                        Collectors.mapping(FollowVarietyEntity::getBrokerSymbol, Collectors.toList())
                ));

        List<String[]> listString = new ArrayList<>();
        String[] header = new String[followPlatformEntityList.size() + 2]; // +2 for stdContract and stdSymbol
        header[0] = "标准合约";
        header[1] = "品种名称";
        for (int i = 0; i < followPlatformEntityList.size(); i++) {
            header[i + 2] = followPlatformEntityList.get(i).getBrokerName(); // 券商名称
        }
        listString.add(header);

        // 填充数据
        for (FollowVarietyVO o : list.getList()) {
            String[] strings = new String[followPlatformEntityList.size() + 2];
            strings[0] = o.getStdContract() != null ? o.getStdContract().toString() : ""; // 品种合约
            strings[1] = o.getStdSymbol(); // 标准品种

            for (int i = 0; i < followPlatformEntityList.size(); i++) {
                FollowPlatformEntity plat = followPlatformEntityList.get(i);
                String key = o.getStdSymbol() + "_" + plat.getBrokerName();
                List<String> collect = varietyMap.getOrDefault(key, Collections.emptyList());

                // 检查是否有数据，如果有数据但含有 "null"，则替换为空字符串
                String brokerSymbol = collect.isEmpty() ? "" : String.join("/", collect);
                strings[i + 2] = "null".equals(brokerSymbol) ? "" : brokerSymbol;
            }
            listString.add(strings);
        }

        // 构造分页结果
        PageResult<String[]> pageResult = new PageResult<>(listString, list.getTotal());
        return Result.ok(pageResult);
    }

    @GetMapping("listBySymbol")
    @Operation(summary = "根据标准品种查询其他信息")
    @PreAuthorize("hasAuthority('mascontrol:variety')")
    public Result<PageResult<FollowVarietyVO>> listBySymbol(@ParameterObject @Valid FollowVarietyQuery query){
        PageResult<FollowVarietyVO> list = followVarietyService.pageSmybolList(query);
        return Result.ok(list);
    }
}
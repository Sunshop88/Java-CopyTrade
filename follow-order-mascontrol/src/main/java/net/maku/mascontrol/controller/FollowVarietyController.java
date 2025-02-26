package net.maku.mascontrol.controller;

import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import net.maku.followcom.entity.FollowPlatformEntity;
import net.maku.followcom.entity.FollowVarietyEntity;
import net.maku.followcom.entity.FollowVpsEntity;
import net.maku.followcom.query.FollowVarietyQuery;
import net.maku.followcom.service.FollowPlatformService;
import net.maku.followcom.service.FollowVarietyService;
import net.maku.followcom.service.FollowVpsService;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.util.RestUtil;
import net.maku.followcom.vo.FollowVarietyVO;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.common.utils.Result;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.framework.operatelog.annotations.OperateLog;
import net.maku.framework.operatelog.enums.OperateTypeEnum;

import org.apache.ibatis.javassist.compiler.ast.Variable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.maku.followcom.util.RestUtil.getHeader;

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
    private static final Logger log = LoggerFactory.getLogger(FollowVarietyController.class);
    private final FollowVarietyService followVarietyService;
    private final FollowPlatformService followPlatformService;
    private final RedisCache redisCache;
    private final FollowVpsService followVpsService;
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
    public Result<String> importExcel(@RequestParam(value = "file",required = false) MultipartFile file,@RequestParam("template" )Integer template,@RequestParam(value = "templateName") String templateName, HttpServletRequest req) {
        if (ObjectUtil.isEmpty(templateName)){
            return Result.error("请输入模板名称");
        }
        List<FollowVarietyEntity> list = followVarietyService.list(new LambdaQueryWrapper<FollowVarietyEntity>()
                .eq(FollowVarietyEntity::getTemplateName, templateName)
                .ne(FollowVarietyEntity::getTemplateId, template)
        );
        if (list.size()>0 ){
            return Result.error("模板名称重复，请重新输入");
        }
        try {
            // 检查文件类型
            if (file != null && !file.isEmpty()) {
                if (!isExcelOrCsv(file.getOriginalFilename())) {
                    return Result.error("仅支持 Excel 和 CSV 文件");
                }
                // 检查文件大小
                long maxSize = 10 * 1024 * 1024; // 10MB
                if (file.getSize() > maxSize) {
                    return Result.error("上传的文件大小不能超过 10MB");
                }
                // 导入文件
                followVarietyService.importByExcel(file, template, templateName);
            }else{
                followVarietyService.updateTemplateName(template,templateName);
            }
            //修改缓存
            String authorization=req.getHeader("Authorization");
            ThreadPoolUtils.execute(()->{
                for (FollowVpsEntity o : followVpsService.list()){
                    try{
                        String url = MessageFormat.format("http://{0}:{1}{2}", o.getIpAddress(), FollowConstant.VPS_PORT, FollowConstant.VPS_UPDATE_CACHE_VARIETY_CACHE);
                        JSONObject jsonObject=new JSONObject();
                        jsonObject.put("template",template);
                        HttpHeaders header = getHeader(MediaType.APPLICATION_JSON_UTF8_VALUE);
                        header.add("Authorization", authorization);
                        JSONObject body = RestUtil.request(url, HttpMethod.GET,header, jsonObject, null, JSONObject.class).getBody();
                        log.info("修改缓存"+body.toString());
                    }catch (Exception e){
                        log.info("修改缓存失败"+e);
                    }
                }
            });
            return Result.ok("修改成功");
        } catch (Exception e) {
            return Result.error("修改失败：" + e.getMessage());
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
        log.info("名称：");
        if (ObjectUtil.isEmpty(templateName)){
            return Result.error("请输入模板名称");
        }
        //查询templateName是否重复
        List<FollowVarietyEntity> list = followVarietyService.list(new LambdaQueryWrapper<FollowVarietyEntity>().eq(FollowVarietyEntity::getTemplateName, templateName));
        if (ObjectUtil.isNotEmpty(list)){
            return Result.error("模板名称重复，请重新输入");
        }
        // 检查文件大小
        long maxSize = 10 * 1024 * 1024; // 10MB
        if (file.getSize() > maxSize) {
            return Result.error("上传的文件大小不能超过 10MB");
        }
        try {
            // 检查文件类型
            if (!isExcelOrCsv(file.getOriginalFilename())) {
                return Result.error("仅支持 Excel 和 CSV 文件");
            }
            // 导入文件
            followVarietyService.addByExcel(file,templateName);
            return Result.ok("新增成功");
        } catch (Exception e) {
            return Result.error("新增失败：" + e.getMessage());
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
    @Operation(summary = "页面展示")
    @PreAuthorize("hasAuthority('mascontrol:variety')")
    public Result<PageResult<String[]>> listSymbol(@ParameterObject @Valid FollowVarietyQuery query) {
        // 合并数据库查询
        List<FollowVarietyEntity> allVarietyEntities = followVarietyService.list();
        List<FollowVarietyEntity> filteredVarietyEntities = allVarietyEntities.stream()
                .filter(entity -> entity.getTemplateId() != null && entity.getTemplateId().equals(query.getTemplate()))
                .collect(Collectors.toList());

        // 获取唯一券商名称
        Set<String> platformBrokerNames = followPlatformService.list().stream()
                .map(FollowPlatformEntity::getBrokerName)
                .collect(Collectors.toSet());
        Set<String> varietyBrokerNames = filteredVarietyEntities.stream()
                .map(FollowVarietyEntity::getBrokerName)
                .collect(Collectors.toSet());
        List<String> uniqueBrokerNames = platformBrokerNames.stream()
                .filter(varietyBrokerNames::contains)
                .collect(Collectors.toList());

        // 构建映射关系
        Map<String, List<String>> varietyMap = filteredVarietyEntities.stream()
                .collect(Collectors.groupingBy(
                        entity -> entity.getStdSymbol() + "_" + entity.getBrokerName(),
                        Collectors.mapping(FollowVarietyEntity::getBrokerSymbol, Collectors.toList())
                ));

        // 构建表头
        String[] header = Stream.concat(
                Stream.of("标准合约","品种名称"),
                uniqueBrokerNames.stream()
        ).toArray(String[]::new);

        // 构建数据行
        List<String[]> rows = new ArrayList<>();
        rows.add(header);
        PageResult<FollowVarietyVO> pageData = followVarietyService.pageSmybol(query);
        for (FollowVarietyVO varietyVO : pageData.getList()) {
            rows.add(buildRow(varietyVO, uniqueBrokerNames, varietyMap));
        }

        // 返回分页结果
        PageResult<String[]> pageResult = new PageResult<>(rows, pageData.getTotal());
        return Result.ok(pageResult);
    }

    private String[] buildRow(FollowVarietyVO varietyVO, List<String> uniqueBrokerNames, Map<String, List<String>> varietyMap) {
        String[] row = new String[uniqueBrokerNames.size() + 2];
        row[0] = Optional.ofNullable(varietyVO.getStdContract()).map(Object::toString).orElse("");
        row[1] = varietyVO.getStdSymbol();

        for (int i = 0; i < uniqueBrokerNames.size(); i++) {
            String brokerName = uniqueBrokerNames.get(i);
            String key = varietyVO.getStdSymbol() + "_" + brokerName;
            List<String> symbols = varietyMap.getOrDefault(key, Collections.emptyList());
            String symbolStr = String.join("/", symbols);
            row[i + 2] = "null".equalsIgnoreCase(symbolStr) ? "" : symbolStr;
        }
        return row;
    }

    @GetMapping("listBySymbol")
    @Operation(summary = "根据标准品种查询其他信息")
    @PreAuthorize("hasAuthority('mascontrol:variety')")
    public Result<PageResult<FollowVarietyVO>> listBySymbol(@ParameterObject @Valid FollowVarietyQuery query){
        PageResult<FollowVarietyVO> list = followVarietyService.pageSmybolList(query);
        return Result.ok(list);
    }

    @GetMapping("templateName")
    @Operation(summary = "模板名称")
    public Result<List<FollowVarietyVO>> listTemplate() {
        List<FollowVarietyVO> list = followVarietyService.getListByTemplate();
        return Result.ok(list);
    }

    @GetMapping("listSmybolSend")
    @Operation(summary = "查询标准品种，标准合约")
    @PreAuthorize("hasAuthority('mascontrol:variety')")
    public Result<PageResult<FollowVarietyVO>> listSmybolSend(@ParameterObject @Valid FollowVarietyQuery query){
        PageResult<FollowVarietyVO> list = followVarietyService.pageSmybol(query);
        return Result.ok(list);
    }

    @GetMapping("listSymbol")
    @Operation(summary = "查询所有品种")
    @PreAuthorize("hasAuthority('mascontrol:variety')")
    public Result<List<FollowVarietyVO>> listSymbol(){
        List<FollowVarietyVO> list = followVarietyService.listSymbol();
        return Result.ok(list);
    }

    @DeleteMapping("deleteTemplate")
    @Operation(summary = "删除模板")
    @OperateLog(type = OperateTypeEnum.DELETE)
    @PreAuthorize("hasAuthority('mascontrol:variety')")
    public Result<String> deleteTemplate(@RequestBody List<Integer> idList){
        if(followVarietyService.checkTemplate(idList)) {
            boolean b = followVarietyService.deleteTemplate(idList);
            if (b) {
                return Result.ok();
            }
        }
        return Result.error("删除失败");
    }
}
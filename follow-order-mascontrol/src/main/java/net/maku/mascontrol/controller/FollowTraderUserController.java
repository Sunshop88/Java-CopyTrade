package net.maku.mascontrol.controller;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.convert.FollowTraderUserConvert;
import net.maku.followcom.convert.FollowUploadTraderUserConvert;
import net.maku.followcom.entity.FollowPlatformEntity;
import net.maku.followcom.entity.FollowUploadTraderUserEntity;
import net.maku.followcom.entity.FollowVarietyEntity;
import net.maku.followcom.enums.TraderUserEnum;
import net.maku.followcom.query.FollowTestServerQuery;
import net.maku.followcom.query.FollowTraderUserQuery;
import net.maku.followcom.query.FollowUploadTraderUserQuery;
import net.maku.followcom.service.FollowPlatformService;
import net.maku.followcom.service.FollowTestDetailService;
import net.maku.followcom.service.FollowTraderUserService;
import net.maku.followcom.service.FollowUploadTraderUserService;
import net.maku.followcom.vo.FollowTestDetailVO;
import net.maku.followcom.vo.FollowTraderUserVO;
import net.maku.followcom.vo.FollowUploadTraderUserVO;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.common.utils.Result;
import net.maku.framework.operatelog.annotations.OperateLog;
import net.maku.framework.operatelog.enums.OperateTypeEnum;
import net.maku.framework.security.user.SecurityUser;
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
import java.time.LocalDateTime;
import java.util.List;

/**
 * 账号初始表
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Slf4j
@RestController
@RequestMapping("/mascontrol/user")
@Tag(name="账号初始表")
@AllArgsConstructor
public class FollowTraderUserController {
    private final FollowTraderUserService followTraderUserService;
    private final FollowUploadTraderUserService followUploadTraderUserService;
    private final FollowPlatformService followPlatformService;
    private final FollowTestDetailService followTestDetailService;

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

    @GetMapping("uploadPage")
    @Operation(summary = "批量记录")
    @PreAuthorize("hasAuthority('mascontrol:uploadTraderUser')")
    public Result<PageResult<FollowUploadTraderUserVO>> page(@ParameterObject @Valid FollowUploadTraderUserQuery query){
        PageResult<FollowUploadTraderUserVO> page = followUploadTraderUserService.page(query);

        return Result.ok(page);
    }

    @PostMapping("import")
    @Operation(summary = "导入")
    @OperateLog(type = OperateTypeEnum.IMPORT)
    @PreAuthorize("hasAuthority('mascontrol:variety')")
    public Result<String> addExcel(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            return Result.error("请选择需要上传的文件");
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
            //设置状态
            FollowUploadTraderUserVO followUploadTraderUserVO = new FollowUploadTraderUserVO();
            followUploadTraderUserVO.setStatus(TraderUserEnum.IN_PROGRESS.getType());
            followUploadTraderUserVO.setOperator(SecurityUser.getUser().getUsername());
            followUploadTraderUserVO.setUploadTime(LocalDateTime.now());
            followUploadTraderUserService.save(followUploadTraderUserVO);

            //查询最新的记录
            FollowUploadTraderUserEntity saved = followUploadTraderUserService.getOne(new QueryWrapper<FollowUploadTraderUserEntity>().orderByDesc("id").last("limit 1"));
            Long savedId = saved.getId();
            // 导入文件
            followTraderUserService.addByExcel(file,savedId);
            return Result.ok("新增成功");
        } catch (Exception e) {
            return Result.error("新增失败：" + e.getMessage());
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

    @GetMapping("pageDetail")
    @Operation(summary = "失败详情")
    @PreAuthorize("hasAuthority('mascontrol:traderUser')")
    public Result<PageResult<FollowTraderUserVO>> pageDetail(@ParameterObject @Valid FollowTraderUserQuery query){
        PageResult<FollowTraderUserVO> page = followTraderUserService.page(query);

        return Result.ok(page);
    }

    @GetMapping("listServerOrder")
    @Operation(summary = "查询当前存在服务商列表")
    public Result<List<FollowPlatformEntity>> listServerOrder() {
        return Result.ok(followPlatformService.list(new LambdaQueryWrapper<FollowPlatformEntity>().orderByDesc(FollowPlatformEntity::getCreateTime)));
    }

    @GetMapping("listHavingServer")
    @Operation(summary = "查询节点列表")
    public Result<List<FollowTestDetailVO>> listHavingServer(@Parameter FollowTestServerQuery query) {
        List<FollowTestDetailVO> list = followTestDetailService.selectServerNode(query);

        return Result.ok(list);
    }

    @PutMapping
    @Operation(summary = "批量修改分组")
    @OperateLog(type = OperateTypeEnum.UPDATE)
    @PreAuthorize("hasAuthority('mascontrol:traderUser')")
    public Result<String> updateGroup(@RequestBody List<Long> idList,@RequestBody String group) {
        followTraderUserService.updateGroup(idList,group);

        return Result.ok("批量修改分组成功");
    }

    @PutMapping
    @Operation(summary = "批量修改密码")
    @OperateLog(type = OperateTypeEnum.UPDATE)
    @PreAuthorize("hasAuthority('mascontrol:traderUser')")
    public Result<String> updatePassword(@RequestBody List<FollowTraderUserVO> voList,String password,String confirmPassword) {
        followTraderUserService.updatePassword(voList,password,confirmPassword);

        return Result.ok("批量修改密码成功");
    }

}
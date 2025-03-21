package net.maku.mascontrol.controller;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import net.maku.followcom.entity.FollowGroupEntity;
import net.maku.followcom.entity.FollowTraderUserEntity;
import net.maku.followcom.query.FollowGroupQuery;
import net.maku.followcom.service.FollowGroupService;
import net.maku.followcom.service.FollowTraderUserService;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.util.RestUtil;
import net.maku.followcom.vo.FollowGroupVO;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.common.utils.Result;
import net.maku.framework.operatelog.annotations.OperateLog;
import net.maku.framework.operatelog.enums.OperateTypeEnum;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;

/**
 * 组别
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@RestController
@RequestMapping("/mascontrol/group")
@Tag(name="组别")
@AllArgsConstructor
public class FollowGroupController {
    private final FollowGroupService followGroupService;
    private final FollowTraderUserService followTraderUserService;

    @GetMapping("page")
    @Operation(summary = "分页")
    @PreAuthorize("hasAuthority('mascontrol:group')")
    public Result<PageResult<FollowGroupVO>> page(@ParameterObject @Valid FollowGroupQuery query){
        PageResult<FollowGroupVO> page = followGroupService.page(query);

        return Result.ok(page);
    }


    @GetMapping("{id}")
    @Operation(summary = "信息")
    @PreAuthorize("hasAuthority('mascontrol:group')")
    public Result<FollowGroupVO> get(@PathVariable("id") Long id){
        FollowGroupVO data = followGroupService.get(id);

        return Result.ok(data);
    }

    @PostMapping
    @Operation(summary = "保存")
    @OperateLog(type = OperateTypeEnum.INSERT)
    @PreAuthorize("hasAuthority('mascontrol:group')")
    public Result<String> save(@RequestBody @Valid FollowGroupVO vo){
        //确保名字唯一性
        followGroupService.list().stream()
                .filter(vo1 -> vo1.getName().equals(vo.getName()))
                .findAny()
                .ifPresent(followGroupVO -> {
                    throw new ServerException("组别名称重复");
                });
        followGroupService.save(vo);

        return Result.ok();
    }

    @PutMapping
    @Operation(summary = "修改")
    @OperateLog(type = OperateTypeEnum.UPDATE)
    @PreAuthorize("hasAuthority('mascontrol:group')")
    public Result<String> update(@RequestBody @Valid FollowGroupVO vo){
        //确保名字唯一性，排除掉自己的名字
        followGroupService.list().stream()
                .filter(vo1 -> vo1.getName().equals(vo.getName()) && !vo1.getId().equals(vo.getId()))
                .findAny()
                .ifPresent(followGroupVO -> {
                    throw new ServerException("组别名称重复");
                });
        //修改账号记录名称
        LambdaUpdateWrapper<FollowTraderUserEntity> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(FollowTraderUserEntity::getGroupId, vo.getId());
        updateWrapper.set(FollowTraderUserEntity::getGroupName, vo.getName());
        followTraderUserService.update(updateWrapper);
        followGroupService.update(vo);

        return Result.ok();
    }

    @DeleteMapping
    @Operation(summary = "删除")
    @OperateLog(type = OperateTypeEnum.DELETE)
    @PreAuthorize("hasAuthority('mascontrol:group')")
    public Result<String> delete(@RequestBody List<Long> idList){
        for (Long id : idList) {
            LambdaQueryWrapper<FollowTraderUserEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(FollowTraderUserEntity::getGroupId, id);
            if (ObjectUtil.isNotEmpty(followTraderUserService.list(wrapper))) {
                throw new ServerException("该组别下有账号，不能删除");
            }
           followGroupService.removeById(id);
        }

        return Result.ok();
    }


    @GetMapping("export")
    @Operation(summary = "导出")
    @OperateLog(type = OperateTypeEnum.EXPORT)
    @PreAuthorize("hasAuthority('mascontrol:group')")
    public void export() {
        followGroupService.export();
    }

    @GetMapping("list")
    @Operation(summary = "列表展示")
    @PreAuthorize("hasAuthority('mascontrol:group')")
    public Result<List<FollowGroupEntity>> list() {
        List<FollowGroupEntity> list = followGroupService.list();
        for (FollowGroupEntity entity : list) {
            LambdaQueryWrapper<FollowTraderUserEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(FollowTraderUserEntity::getGroupId, entity.getId());
//            wrapper.eq(FollowTraderUserEntity::getUploadStatus, 0);
            //查询
            List<FollowTraderUserEntity> listNum = followTraderUserService.list(wrapper);
            if (ObjectUtil.isNotEmpty(listNum)){
                long num = listNum.stream().count();
                entity.setNumber((int) num);
                followGroupService.updateById(entity);
            }
        }
        return Result.ok(followGroupService.list());
    }

    public static void main(String[] args) { cn.hutool.json.JSONObject jsonObject = new cn.hutool.json.JSONObject();
        jsonObject.put("traderId", 22);
        Result result = RestUtil.sendRequest(null, "127.0.0.1", HttpMethod.GET, FollowConstant.PUSH_ORDER, jsonObject, null, FollowConstant.REQUEST_PORT);

    }
}
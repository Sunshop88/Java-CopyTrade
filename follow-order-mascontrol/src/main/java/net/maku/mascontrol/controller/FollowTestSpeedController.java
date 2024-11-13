package net.maku.mascontrol.controller;

import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.VpsSpendEnum;
import net.maku.followcom.query.FollowTestDetailQuery;
import net.maku.followcom.query.FollowTestSpeedQuery;
import net.maku.followcom.service.*;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.util.RestUtil;
import net.maku.followcom.vo.*;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.common.utils.Result;
import net.maku.framework.operatelog.annotations.OperateLog;
import net.maku.framework.operatelog.enums.OperateTypeEnum;
import net.maku.framework.security.user.SecurityUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.springframework.web.client.RestTemplate;

import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * 测速记录
 */
@RestController
@RequestMapping("/mascontrol/speed")
@Tag(name = "测速记录")
@AllArgsConstructor
public class FollowTestSpeedController {
    private static final Logger log = LoggerFactory.getLogger(FollowTestSpeedController.class);
    private final FollowTestSpeedService followTestSpeedService;
    private final FollowTestDetailService followTestDetailService;
    private final FollowBrokeServerService followBrokeServerService;
    private final FollowVpsService followVpsService;
    private final FollowPlatformService followPlatformService;
    private final RedisUtil redisUtil;

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
    public Result<FollowTestSpeedVO> measure(@RequestBody MeasureRequestVO request, HttpServletRequest req) throws Exception {
        FollowTestSpeedVO overallResult = new FollowTestSpeedVO();
        overallResult.setStatus(VpsSpendEnum.IN_PROGRESS.getType());
        overallResult.setDoTime(new Date());
        overallResult.setVersion(0);
        overallResult.setDeleted(0);
        overallResult.setCreator(SecurityUser.getUserId());
        overallResult.setCreateTime(LocalDateTime.now());
        overallResult.setTestName(SecurityUser.getUser().getUsername());
        followTestSpeedService.saveTestSpeed(overallResult);
        List<String> servers = request.getServers();
        List<String> vps = request.getVps();

        extracted(req, vps, servers, overallResult);
        return Result.ok();
    }

    private void extracted(HttpServletRequest req, List<String> vps, List<String> servers, FollowTestSpeedVO overallResult) throws JsonProcessingException {
        List<FollowVpsEntity> vpsList = followVpsService.listByVpsName(vps);
        ObjectMapper objectMapper = new ObjectMapper();
        boolean allSuccess = true;

        ExecutorService executorService = Executors.newFixedThreadPool(10); // 创建固定大小的线程池
        List<Future<Boolean>> futures = new ArrayList<>(); // 存储每个任务的 Future 对象

        for (FollowVpsEntity vpsEntity : vpsList) {
            futures.add(executorService.submit(() -> {
                String url = MessageFormat.format("http://{0}:{1}{2}", vpsEntity.getIpAddress(), FollowConstant.VPS_PORT, FollowConstant.VPS_MEASURE);

                MeasureRequestVO startRequest = new MeasureRequestVO();
                startRequest.setServers(servers);
                startRequest.setVpsEntity(vpsEntity);
                startRequest.setTestId(overallResult.getId());

                // 将对象序列化为 JSON
                String jsonBody = objectMapper.writeValueAsString(startRequest);
                RestTemplate restTemplate = new RestTemplate();
                HttpHeaders headers = RestUtil.getHeaderApplicationJsonAndToken(req);
                HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
                ResponseEntity<JSONObject> response = restTemplate.exchange(url, HttpMethod.POST, entity, JSONObject.class);
                log.info("测速请求:" + response.getBody());

                if (!response.getBody().getString("msg").equals("success")) {
                    log.error("测速失败ip: " + vpsEntity.getIpAddress());
                    return false; // 返回失败状态
                }
                return true; // 返回成功状态
            }));
        }
        // 等待所有任务完成并检查结果
        for (Future<Boolean> future : futures) {
            try {
                if (!future.get()) { // 如果有任何任务返回失败
                    allSuccess = false;
                    break;
                }
            } catch (InterruptedException | ExecutionException e) {
                log.error("测速任务执行异常: " + e.getMessage());
                allSuccess = false;
                break;
            }
        }
        // 根据所有任务的执行结果更新 overallResult
        if (allSuccess) {
            overallResult.setStatus(VpsSpendEnum.SUCCESS.getType());

            List<FollowTestDetailEntity> allEntities = followTestDetailService.list(
                    new LambdaQueryWrapper<FollowTestDetailEntity>()
                            .eq(FollowTestDetailEntity::getTestId, overallResult.getId())
            );
            // 获取所有唯一的 VPS 名称
            List<String> vpsNames = allEntities.stream()
                    .map(FollowTestDetailEntity::getVpsName)
                    .distinct()
                    .collect(Collectors.toList());

            vpsNames.forEach(vpsName -> {
                // 获取当前 VPS 名称下的所有服务器名称
                List<String> serverNames = allEntities.stream()
                        .filter(entity -> vpsName.equals(entity.getVpsName()))
                        .map(FollowTestDetailEntity::getServerName)
                        .distinct()
                        .collect(Collectors.toList());
                serverNames.forEach(serverName -> {
                    // 查找当前 VPS 名称和服务器名称下的最小延迟
                    FollowTestDetailEntity minLatencyEntity = allEntities.stream()
                            .filter(entity -> vpsName.equals(entity.getVpsName()) && serverName.equals(entity.getServerName()))
                            .min(Comparator.comparingLong(FollowTestDetailEntity::getSpeed))
                            .orElse(null);

                    if (ObjectUtil.isNotEmpty(minLatencyEntity)) {
                        //查询vps名称所对应的id
                        Integer vpsId = followVpsService.getOne(new LambdaQueryWrapper<FollowVpsEntity>().eq(FollowVpsEntity::getName, vpsName)).getId();
                        redisUtil.hset(Constant.VPS_NODE_SPEED + vpsId, serverName, minLatencyEntity.getServerNode(), 0);
                    }
                });
            });
        } else {
            overallResult.setStatus(VpsSpendEnum.FAILURE.getType());
            // 延迟删除操作，确保在所有测速请求完成后再进行删除
            followTestDetailService.deleteByTestId(overallResult.getId());
        }

        update(overallResult);
        executorService.shutdown(); // 关闭线程池
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
    public Result<FollowTestDetailVO> remeasure(@RequestParam Long id, @RequestBody MeasureRequestVO request, HttpServletRequest req) throws Exception {
        List<String> servers = request.getServers();
        List<String> vps = request.getVps();
        FollowTestSpeedVO overallResult = followTestSpeedService.get(id);
        overallResult.setStatus(VpsSpendEnum.IN_PROGRESS.getType());
        UpdateWrapper<FollowTestSpeedEntity> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", id).set("status", VpsSpendEnum.IN_PROGRESS.getType());
        followTestSpeedService.update(updateWrapper);

        extracted(req, vps, servers, overallResult);
        return Result.ok();
    }

    @GetMapping("listTestDetail")
    @Operation(summary = "测速详情")
    @PreAuthorize("hasAuthority('mascontrol:speed')")
    public Result<PageResult<String[]>> listSmybol(@ParameterObject @Valid FollowTestDetailQuery query) {
        PageResult<String[]> list = followTestDetailService.page(query);

        return Result.ok(list);
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
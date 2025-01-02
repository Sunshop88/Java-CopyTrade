package net.maku.mascontrol.controller;

import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.VpsSpendEnum;
import net.maku.followcom.query.FollowSpeedSettingQuery;
import net.maku.followcom.query.FollowTestDetailQuery;
import net.maku.followcom.query.FollowTestServerQuery;
import net.maku.followcom.query.FollowTestSpeedQuery;
import net.maku.followcom.service.*;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.util.RestUtil;
import net.maku.followcom.vo.*;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.common.utils.Result;
import net.maku.framework.operatelog.annotations.OperateLog;
import net.maku.framework.operatelog.enums.OperateTypeEnum;
import net.maku.framework.security.user.SecurityUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
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
    private final ObjectMapper objectMapper;
    private final FollowTraderService followTraderService;
    private final FollowSpeedSettingService followSpeedSettingService;

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
    public Result<FollowTestSpeedVO> measure(@RequestBody @Valid MeasureRequestVO request, HttpServletRequest req) throws Exception {
        if (ObjectUtil.isEmpty(request.getServers()) || ObjectUtil.isEmpty(request.getVps())) {
            return Result.error("服务器列表或vps列表为空");
        }
        FollowTestSpeedVO overallResult = new FollowTestSpeedVO();
        overallResult.setStatus(VpsSpendEnum.IN_PROGRESS.getType());
        overallResult.setDoTime(new Date());
        overallResult.setVersion(0);
        overallResult.setDeleted(0);
        overallResult.setCreator(SecurityUser.getUserId());
        overallResult.setCreateTime(LocalDateTime.now());
        overallResult.setTestName(SecurityUser.getUser().getUsername());
        followTestSpeedService.saveTestSpeed(overallResult);
        // 保存到 Redis
        String testId = overallResult.getId().toString();
        redisUtil.set("speed:"+testId + ":servers", objectMapper.writeValueAsString(request.getServers()));
        redisUtil.set("speed:"+testId + ":vps", objectMapper.writeValueAsString(request.getVps()));
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
            //平台点击测速的时候，断开连接的VPS不需要发起测速请求
            try {
                InetAddress inet = InetAddress.getByName(vpsEntity.getIpAddress());
                boolean reachable = inet.isReachable(5000);
                if (!reachable) {
                    log.warn("VPS 地址不可达: " + vpsEntity.getIpAddress() + ", 跳过该VPS");
                    continue;
                }
                try (Socket socket = new Socket(vpsEntity.getIpAddress(), Integer.parseInt(FollowConstant.VPS_PORT))) {
                    log.info("成功连接到 VPS: " + vpsEntity.getIpAddress());
                } catch (IOException e) {
                    log.warn("VPS 服务未启动: " + vpsEntity.getIpAddress() + ", 跳过该VPS");
                    continue;
                }
            } catch (IOException e) {
                log.error("请求异常: " + e.getMessage() + ", 跳过该VPS");
                continue;
            }

            futures.add(executorService.submit(() -> {
                String url = MessageFormat.format("http://{0}:{1}{2}", vpsEntity.getIpAddress(), FollowConstant.VPS_PORT, FollowConstant.VPS_MEASURE);

                MeasureRequestVO startRequest = new MeasureRequestVO();
                startRequest.setServers(servers);
                startRequest.setVpsEntity(vpsEntity);
                startRequest.setTestId(overallResult.getId());

//                // 将对象序列化为 JSON
//                String jsonBody = objectMapper.writeValueAsString(startRequest);
//                RestTemplate restTemplate = new RestTemplate();
//                HttpHeaders headers = RestUtil.getHeaderApplicationJsonAndToken(req);
//                HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
//                ResponseEntity<JSONObject> response = restTemplate.exchange(url, HttpMethod.POST, entity, JSONObject.class);
//                log.info("测速请求:" + response.getBody());

                // 手动序列化 FollowVpsEntity 中的 expiryDate 字段
                String expiryDateStr = vpsEntity.getExpiryDate().toString();
                startRequest.setExpiryDateStr(expiryDateStr);

                RestTemplate restTemplate = new RestTemplate();
                HttpHeaders headers = RestUtil.getHeaderApplicationJsonAndToken(req);
                HttpEntity<MeasureRequestVO> entity = new HttpEntity<>(startRequest, headers);
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
                        Integer vpsId = followVpsService.getOne(new LambdaQueryWrapper<FollowVpsEntity>()
                                .eq(FollowVpsEntity::getName, vpsName)
                                .eq(FollowVpsEntity::getDeleted, 0)
                                .last("LIMIT 1")).getId();
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
    public Result<FollowTestDetailVO> remeasure(@RequestParam Long id, HttpServletRequest req) throws Exception {
//        if (ObjectUtil.isEmpty(request.getServers()) || ObjectUtil.isEmpty(request.getVps())) {
//            return Result.error("服务器列表或vps列表为空");
//        }

//        List<String> servers = request.getServers();
//        List<String> vps = request.getVps();

        // 从 Redis 中获取服务器和 VPS 列表
        String serversJson = (String) redisUtil.get("speed:"+id + ":servers");
        String vpsJson = (String) redisUtil.get("speed:"+id + ":vps");

        if (ObjectUtil.isEmpty(serversJson) || ObjectUtil.isEmpty(vpsJson)) {
            return Result.error("未找到对应的服务器列表或 VPS 列表");
        }
        // 将 JSON 字符串转换为 List
        List<String> servers = objectMapper.readValue(serversJson, new TypeReference<List<String>>() {});
        List<String> vps = objectMapper.readValue(vpsJson, new TypeReference<List<String>>() {});

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
    public Result<List<FollowPlatformVO>> listServer() {
        List<FollowPlatformVO> list = followPlatformService.listByServer();

        return Result.ok(list);
    }

    @GetMapping("listVps")
    @Operation(summary = "查询vps清单")
    @PreAuthorize("hasAuthority('mascontrol:speed')")
    public Result<List<FollowVpsVO>> listVps() {
        List<FollowVpsVO> list = followVpsService.listByVps();

        return Result.ok(list);
    }

    @PostMapping("addServer")
    @Operation(summary = "添加服务器")
    @PreAuthorize("hasAuthority('mascontrol:speed')")
    public Result<String> addServer(@RequestParam String server) {
            // 根据名称查询其信息
            FollowBrokeServerEntity followBrokeServerEntity = followBrokeServerService.getByName(server);
            if (ObjectUtil.isEmpty(followBrokeServerEntity)) {
                FollowBrokeServerEntity  followBrokeServer = new FollowBrokeServerEntity();
                followBrokeServer.setServerName(server);
                followBrokeServerService.save(followBrokeServer);
                followBrokeServerEntity = followBrokeServerService.getByName(server); // 重新查询以获取生成的ID
            }

            FollowTestDetailVO followTestDetail = new FollowTestDetailVO();
            followTestDetail.setServerName(server);
            followTestDetail.setServerId(followBrokeServerEntity.getId());
            followTestDetail.setPlatformType("MT4");
            followTestDetailService.save(followTestDetail);

            return Result.ok("添加成功");
    }

    @PostMapping("addServerNode")
    @Operation(summary = "添加服务器节点")
    @PreAuthorize("hasAuthority('mascontrol:speed')")
    @Transactional(rollbackFor = Exception.class)
    public Result<String> addServerNode(@RequestBody @Valid FollowTestServerVO followTestServerVO) {
        try {
        //添加到券商表
        followTestServerVO.getServerNodeList().stream().forEach(server -> {
            FollowBrokeServerEntity  followBrokeServer = new FollowBrokeServerEntity();
            followBrokeServer.setServerName(followTestServerVO.getServerName());
            String[] split = server.split(":");
            if (split.length != 2) {
                throw new ServerException("服务器节点格式不正确");
            }
            followBrokeServer.setServerNode(split[0]);
            followBrokeServer.setServerPort(split[1]);
            followBrokeServerService.save(followBrokeServer);

            FollowTestDetailVO followTestDetail = new FollowTestDetailVO();
            followTestDetail.setServerName(followTestServerVO.getServerName());
            followTestDetail.setServerId(followBrokeServer.getId());
            followTestDetail.setPlatformType(followTestServerVO.getPlatformType());
            followTestDetail.setServerNode(server);
            followTestDetailService.save(followTestDetail);
        });

        return Result.ok("添加成功");
        } catch (Exception e) {
            log.error("添加服务器节点失败", e);
            throw e; // 确保异常被抛出，触发事务回滚
        }
    }

    @GetMapping("listTestServer")
    @Operation(summary = "服务器管理列表")
    @PreAuthorize("hasAuthority('mascontrol:speed')")
    public Result<PageResult<String[]>> listTestServer(@ParameterObject FollowTestServerQuery query) {
        PageResult<String[]>list = followTestDetailService.pageServer(query);

        return Result.ok(list);
    }

    @GetMapping("listTestServerNode")
    @Operation(summary = "节点列表")
    @PreAuthorize("hasAuthority('mascontrol:speed')")
    public Result<PageResult<String[]>> listTestServerNode(@ParameterObject FollowTestServerQuery query) {
        PageResult<String[]>list = followTestDetailService.pageServerNode(query);

        return Result.ok(list);
    }


    @PutMapping("updateServerNode")
    @Operation(summary = "修改服务器节点")
    @PreAuthorize("hasAuthority('mascontrol:speed')")
    public Result<String> updateServerNode(@RequestBody List<FollowTestDetailVO> followTestServerVO,String name) {
        for (FollowTestDetailVO followTestDetailVO : followTestServerVO){
            //确保名称的唯一性,查询followTestDetailService的serverName
            if (followTestDetailService.list(new LambdaQueryWrapper<FollowTestDetailEntity>()
                    .eq(FollowTestDetailEntity::getServerName, followTestDetailVO.getServerName())
                    .ne(FollowTestDetailEntity::getId, followTestDetailVO.getId())).size() > 0) {
                return Result.error("服务器名称重复");
            }
            followTestDetailVO.setServerName(name);
            followTestDetailService.update(followTestDetailVO);
        }
        return Result.ok("修改成功");
    }

    @PostMapping("measureServer")
    @Operation(summary = "节点列表测速")
    @PreAuthorize("hasAuthority('mascontrol:speed')")
    public Result<FollowTestSpeedVO> measures(@RequestBody MeasureRequestVO request, HttpServletRequest req) throws Exception {
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
        //查询vps
        List<String> vps = followVpsService.listByVps().stream().map(FollowVpsVO::getName).collect(Collectors.toList());

        extracted(req, vps, servers, overallResult);
        return Result.ok();
    }

    @DeleteMapping("deleteServer")
    @Operation(summary = "删除服务器")
    @PreAuthorize("hasAuthority('mascontrol:speed')")
    public Result<String> deleteServer(@RequestParam String server) {
        //判断该服务器名称的账号数量是否为0
        String accountCount = followTraderService.getAccountCount(server);
        if (Integer.parseInt(accountCount) > 0) {
            return Result.error("该服务器名称的账号数量不为0，无法删除");
        }
        followTestDetailService.remove(new LambdaQueryWrapper<FollowTestDetailEntity>().eq(FollowTestDetailEntity::getServerName, server));
//        followBrokeServerService.remove(new LambdaQueryWrapper<FollowBrokeServerEntity>().eq(FollowBrokeServerEntity::getServerName, server));
        return Result.ok("删除成功");
    }

    @GetMapping("pageSetting")
    @Operation(summary = "配置开关")
    @PreAuthorize("hasAuthority('mascontrol:speed')")
    public Result<PageResult<FollowSpeedSettingVO>> page(@ParameterObject @Valid FollowSpeedSettingQuery query){
        PageResult<FollowSpeedSettingVO> page = followSpeedSettingService.page(query);

        return Result.ok(page);
    }

    @PutMapping("updateSetting")
    @Operation(summary = "修改配置")
    @OperateLog(type = OperateTypeEnum.UPDATE)
    @PreAuthorize("hasAuthority('mascontrol:speed')")
    public Result<String> update(@RequestBody @Valid FollowSpeedSettingVO vo){
        followSpeedSettingService.update(vo);

        return Result.ok();
    }
}
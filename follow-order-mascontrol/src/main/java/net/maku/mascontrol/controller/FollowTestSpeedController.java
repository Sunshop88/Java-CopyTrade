package net.maku.mascontrol.controller;

import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.CloseOrOpenEnum;
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
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.util.*;
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
//        overallResult.setDoTime(new Date());
        overallResult.setDoTime(LocalDateTime.now());
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
        FollowTestServerQuery query = new FollowTestServerQuery();
        List<FollowTestDetailVO> detailVOLists = followTestDetailService.selectServer(query);
        //将detailVOLists的数据存储在redis
        redisUtil.set(Constant.VPS_NODE_SPEED + "detail", detailVOLists);

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
                startRequest.setMeasureTime(overallResult.getDoTime());
                log.info("测试时间"+overallResult.getDoTime());

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

            /**
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
                        redisUtil.hSet(Constant.VPS_NODE_SPEED + vpsId, serverName, minLatencyEntity.getServerNode(), 0);
                    }
                });
            });
            */
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
    public Result<String> addServer(@RequestBody FollowTestServerVO vo) {
        if (ObjectUtil.isEmpty(vo.getServerName())){
            return Result.error("服务器名称不能为空");
        }
        // 根据名称查询其信息
        FollowBrokeServerEntity followBrokeServerEntity = followBrokeServerService.getByName(vo.getServerName());
        if (ObjectUtil.isEmpty(followBrokeServerEntity)) {
            FollowBrokeServerEntity followBrokeServer = new FollowBrokeServerEntity();
            followBrokeServer.setServerName(vo.getServerName());
            followBrokeServerService.save(followBrokeServer);
            followBrokeServerEntity = followBrokeServerService.getByName(vo.getServerName()); // 重新查询以获取生成的ID
        }

        FollowTestDetailEntity followTestDetailEntity = followTestDetailService.list(new LambdaQueryWrapper<FollowTestDetailEntity>()
                        .eq(FollowTestDetailEntity::getServerName, vo.getServerName())
                        .orderByDesc(FollowTestDetailEntity::getCreateTime)) // 按照创建时间降序排列
                .stream()
                .findFirst()
                .orElse(null);
        if (ObjectUtil.isNotEmpty(followTestDetailEntity)) {
            return Result.error("该服务器已存在");
        }
        FollowTestDetailVO followTestDetail = new FollowTestDetailVO();
        followTestDetail.setServerName(vo.getServerName());
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
            // 删除已存在的空节点
            String serverName = followTestServerVO.getServerName();
            if (StringUtils.isNotBlank(serverName)) {
                followTestDetailService.remove(Wrappers.<FollowTestDetailEntity>lambdaQuery(FollowTestDetailEntity.class)
                        .eq(FollowTestDetailEntity::getServerName, serverName)
                        .and(wrapper ->wrapper.isNull(FollowTestDetailEntity::getServerNode)
                                .or().eq(FollowTestDetailEntity::getServerNode, "")));
            }

            //添加到券商表
            for (String server : followTestServerVO.getServerNodeList()) {
                String[] split = server.split(":");
                if (split.length != 2) {
                    throw new ServerException("服务器节点格式不正确");
                }
                //确保服务器节点唯一
                List<FollowTestDetailEntity> existingDetails = followTestDetailService.list(
                        Wrappers.<FollowTestDetailEntity>lambdaQuery()
                                .eq(FollowTestDetailEntity::getServerName, followTestServerVO.getServerName())
                                .eq(FollowTestDetailEntity::getServerNode, server)
                );
                if (!existingDetails.isEmpty()) {
                    log.info("服务器节点已存在: {}", server);
                    continue; // 跳过当前循环
                }
                FollowBrokeServerEntity followBrokeServer = new FollowBrokeServerEntity();
                if (ObjectUtil.isEmpty(followBrokeServerService.existsByServerNodeAndServerPort(followTestServerVO.getServerName(), split[0], split[1]))) {
                    followBrokeServer = new FollowBrokeServerEntity();
                    followBrokeServer.setServerName(followTestServerVO.getServerName());
                    followBrokeServer.setServerNode(split[0]);
                    followBrokeServer.setServerPort(split[1]);
                    followBrokeServerService.save(followBrokeServer);
                } else {
                    // 查询已存在的记录
                    followBrokeServer = followBrokeServerService.existsByServerNodeAndServerPort(followTestServerVO.getServerName(), split[0], split[1]);
                }

                FollowTestDetailVO followTestDetail = new FollowTestDetailVO();
                followTestDetail.setServerName(followTestServerVO.getServerName());
                followTestDetail.setServerId(followBrokeServer.getId());
                followTestDetail.setPlatformType("MT4");
                followTestDetail.setServerNode(server);
                followTestDetailService.save(followTestDetail);
            }

            //删除券商表中的数据
            LambdaQueryWrapper<FollowBrokeServerEntity> queryWrapper = new LambdaQueryWrapper<FollowBrokeServerEntity>()
                    .eq(FollowBrokeServerEntity::getServerName, followTestServerVO.getServerName())
                    .isNull(FollowBrokeServerEntity::getServerNode)
                    .isNull(FollowBrokeServerEntity::getServerPort);
            long count = followBrokeServerService.count(queryWrapper);
            if (count > 0) {
                followBrokeServerService.remove(queryWrapper);
            }

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
        if (query.getPage() == null || query.getLimit() == null){
            return Result.error("未传页码或条数");
        }
        PageResult<String[]>list = followTestDetailService.pageServerNode(query);

        return Result.ok(list);
    }

    @GetMapping("listServerAndNode")
    @Operation(summary = "查询默认节点列表")
    @PreAuthorize("hasAuthority('mascontrol:speed')")
    public Result<List<FollowTestDetailVO>> listServerAndNode(@RequestParam(required = false) String name) {
        FollowTestServerQuery query = new FollowTestServerQuery();
        query.setServerName(name);
        List<FollowTestDetailVO> list = followTestDetailService.selectServerNode(query);

        return Result.ok(list);
    }

    @GetMapping("ttt")
    @Operation(summary = "测试")
    @PreAuthorize("hasAuthority('mascontrol:speed')")
    public Result<List<FollowTestDetailVO>> ttt() {
        return Result.ok(followTestDetailService.selectServer1(new FollowTestServerQuery()));
    }

    @PutMapping("updateServerNode")
    @Operation(summary = "修改服务器节点")
    @PreAuthorize("hasAuthority('mascontrol:speed')")
    public Result<String> updateServerNode(@RequestBody List<FollowTestDetailVO> followTestServerVO) {
        String name = followTestServerVO.get(0).getServerName();
        FollowTestServerQuery query = new FollowTestServerQuery();
        query.setServerName(name);
        List<FollowTestDetailVO> list = followTestDetailService.selectServerNode(query);
        LocalDateTime now = LocalDateTime.now();
        for (FollowTestDetailVO vo : followTestServerVO) {
            // 根据 vo 的 serverNode 和 vpsName 在 list 中找到相应的数据
            FollowTestDetailVO targetVO = list.stream()
                    .filter(item ->
                            item.getServerNode().equals(vo.getServerNode()) &&
                                    (vo.getVpsName() == null ? item.getVpsName() == null : vo.getVpsName().equals(item.getVpsName()))
                    )
                    .findFirst()
                    .orElse(null);
            if (targetVO != null) {
                // 修改 isDefaultServer 字段
//                targetVO.setIsDefaultServer(vo.getIsDefaultServer());
                Integer isDefaultServer = vo.getIsDefaultServer();
                if (isDefaultServer == null) {
                    isDefaultServer = 1;
                }
                targetVO.setIsDefaultServer(isDefaultServer);
                targetVO.setTestUpdateTime(targetVO.getTestUpdateTime());
//                System.out.println(targetVO.getUpdateTime());
                targetVO.setServerUpdateTime(now);
                // 更新数据库
                followTestDetailService.update(targetVO);
            } else {
                // 如果没有找到匹配的数据，可以选择记录日志或返回错误信息
                log.warn("未找到匹配的 serverNode: {} ", vo.getServerNode());
            }
        }
        //redis更新
        List<FollowTestDetailVO> newlist = followTestDetailService.selectServerNode(query);
        //查询IsDefaultServer为0的数据
        List<FollowTestDetailVO> defaultServerNodes = newlist.stream()
                .filter(vo -> {
                    Integer isDefaultServer = vo.getIsDefaultServer();
                    return isDefaultServer != null && isDefaultServer == 0;
                })
                .collect(Collectors.toList());
        // 将数据存储到 Redis 中
        for (FollowTestDetailVO entity : defaultServerNodes) {
            Integer vpsId = entity.getVpsId();
            String serverName = entity.getServerName();
            String serverNode = entity.getServerNode();

            redisUtil.hSet(Constant.VPS_NODE_SPEED + vpsId, serverName, serverNode);
        }

        List<FollowTestDetailVO> detailVOLists = followTestDetailService.selectServer(new FollowTestServerQuery());
        //将detailVOLists的数据存储在redis
        redisUtil.set(Constant.VPS_NODE_SPEED + "detail", detailVOLists);

        return Result.ok("修改成功");
    }

    @PutMapping("updateServerName")
    @Operation(summary = "修改服务器名称")
    @PreAuthorize("hasAuthority('mascontrol:speed')")
    public Result<String> updateServerName(@RequestBody @Valid FollowTestServerNameVO followTestServerNameVO) {
        // 确保名称的唯一性
        FollowTestServerQuery query = new FollowTestServerQuery();
        query.setServerName(followTestServerNameVO.getOldName());
        List<FollowTestDetailVO> detailVOLists = followTestDetailService.selectServer(query);
        boolean isNameExists = detailVOLists.stream()
                .anyMatch(vo -> !vo.getServerName().equals(followTestServerNameVO.getOldName()) && vo.getServerName().equals(followTestServerNameVO.getNewName()));
        if (isNameExists) {
            return Result.error("服务器名称重复");
        }
        LocalDateTime now = LocalDateTime.now();
        for (FollowTestDetailVO vo : detailVOLists) {
            vo.setServerName(followTestServerNameVO.getNewName());
            vo.setServerUpdateTime(now);
            vo.setTestUpdateTime(vo.getTestUpdateTime());
            followTestDetailService.update(vo);
        }

        query.setServerName(followTestServerNameVO.getNewName());
        List<FollowTestDetailVO> newlist = followTestDetailService.selectServer(query);
        //查询IsDefaultServer为0的数据
        List<FollowTestDetailVO> defaultServerNodes = newlist.stream()
                .filter(vo -> {
                    Integer isDefaultServer = vo.getIsDefaultServer();
                    return isDefaultServer != null && isDefaultServer == 0;
                })
                .collect(Collectors.toList());
        //更改默认节点
        // 将数据存储到 Redis 中
        for (FollowTestDetailVO entity : defaultServerNodes) {
            Integer vpsId = entity.getVpsId();
            String oldServerName = followTestServerNameVO.getOldName();
            String newServerName = entity.getServerName();

            // 获取旧键名对应的值
            String value = (String) redisUtil.hGet(Constant.VPS_NODE_SPEED + vpsId, oldServerName);

            if (value != null) {
                // 使用新键名设置相同的值
                redisUtil.hSet(Constant.VPS_NODE_SPEED + vpsId, newServerName, value);
                // 删除旧键名
                redisUtil.hDel(Constant.VPS_NODE_SPEED + vpsId, oldServerName);
            }
        }
            return Result.ok("修改成功");
        }

    @PostMapping("measureServer")
    @Operation(summary = "节点列表测速")
    @PreAuthorize("hasAuthority('mascontrol:speed')")
    public Result<FollowTestSpeedVO> measures(@RequestBody MeasureRequestVO request, HttpServletRequest req) throws Exception {
        FollowTestSpeedVO overallResult = new FollowTestSpeedVO();
        overallResult.setStatus(VpsSpendEnum.IN_PROGRESS.getType());
//        overallResult.setDoTime(new Date());
//        overallResult.setDoTime(LocalDateTime.now());
        LocalDateTime now = LocalDateTime.now();
        log.warn("current time: {}", now);  // 打印当前时间
        overallResult.setDoTime(now);
        overallResult.setVersion(0);
        overallResult.setDeleted(0);
        overallResult.setCreator(SecurityUser.getUserId());
        overallResult.setCreateTime(LocalDateTime.now());
        overallResult.setTestName(SecurityUser.getUser().getUsername());
        followTestSpeedService.saveTestSpeed(overallResult);

        List<String> servers = request.getServers();
        //查询vps
        List<String> vps = followVpsService.listByVps().stream().map(FollowVpsVO::getName).collect(Collectors.toList());

        log.warn("time:{}", overallResult.getDoTime());

        extracted(req, vps, servers, overallResult);
        return Result.ok();
    }

    private void extract(HttpServletRequest req, List<String> vps, List<String> servers, FollowTestSpeedVO overallResult) throws JsonProcessingException {
        List<FollowVpsEntity> vpsList = followVpsService.listByVpsName(vps);
        ObjectMapper objectMapper = new ObjectMapper();
        boolean allSuccess = true;

        FollowTestServerQuery query = new FollowTestServerQuery();
        List<FollowTestDetailVO> detailVOLists = followTestDetailService.selectServer(query);
        //将detailVOLists的数据存储在redis,我存所有的数据，方便后面查询
        redisUtil.set(Constant.VPS_NODE_SPEED + "detail", detailVOLists);

        for (FollowVpsEntity vpsEntity : vpsList) {
            // 平台点击测速的时候，断开连接的VPS不需要发起测速请求
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

            String url = MessageFormat.format("http://{0}:{1}{2}", vpsEntity.getIpAddress(), FollowConstant.VPS_PORT, FollowConstant.VPS_MEASURE);

            MeasureRequestVO startRequest = new MeasureRequestVO();
            startRequest.setServers(servers);
            startRequest.setVpsEntity(vpsEntity);
            startRequest.setTestId(overallResult.getId());
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
                allSuccess = false;
                break; // 如果一个任务失败，可以提前退出
            }
        }

        // 根据所有任务的执行结果更新 overallResult
        if (allSuccess) {
            overallResult.setStatus(VpsSpendEnum.SUCCESS.getType());
        } else {
            overallResult.setStatus(VpsSpendEnum.FAILURE.getType());
            // 延迟删除操作，确保在所有测速请求完成后再进行删除
            followTestDetailService.deleteByTestId(overallResult.getId());
        }
        update(overallResult);
    }


    @DeleteMapping("deleteServer")
    @Operation(summary = "删除服务器")
    @PreAuthorize("hasAuthority('mascontrol:speed')")
    public Result<String> deleteServer(@RequestBody FollowTestServerVO followTestServerVO) {
        //删掉redis中该服务器的数据
        FollowTestServerQuery query = new FollowTestServerQuery();
        query.setServerName(followTestServerVO.getServerName());
        List<FollowTestDetailVO> newlist = followTestDetailService.selectServerNode(query);

        //判断该服务器名称的账号数量是否为0
        String accountCount = followTraderService.getAccountCount(followTestServerVO.getServerName());
        if (Integer.parseInt(accountCount) > 0) {
            return Result.error("该服务器账号数量不为0，无法删除");
        }
        log.info("删除的服务器名称为: {}", followTestServerVO.getServerName());
        followTestDetailService.remove(new LambdaQueryWrapper<FollowTestDetailEntity>().eq(FollowTestDetailEntity::getServerName, followTestServerVO.getServerName()));
        String serverName = followTestServerVO.getServerName();
        // 检查是否存在指定名称的记录
        boolean exists = followPlatformService.exists(new LambdaQueryWrapper<FollowPlatformEntity>()
                .eq(FollowPlatformEntity::getServer, serverName));
        if (exists) {
            // 如果存在，则执行删除操作
            followPlatformService.remove(new LambdaQueryWrapper<FollowPlatformEntity>()
                    .eq(FollowPlatformEntity::getServer, serverName));
        }
        followBrokeServerService.remove(new LambdaQueryWrapper<FollowBrokeServerEntity>().eq(FollowBrokeServerEntity::getServerName, followTestServerVO.getServerName()));

        //查询IsDefaultServer为0的数据
        List<FollowTestDetailVO> defaultServerNodes = newlist.stream()
                .filter(vo -> {
                    Integer isDefaultServer = vo.getIsDefaultServer();
                    return isDefaultServer != null && isDefaultServer == 0;
                })
                .collect(Collectors.toList());
        for (FollowTestDetailVO entity : defaultServerNodes) {
            Integer vpsId = entity.getVpsId();
            String serverNames = entity.getServerName();
                // 删除键名
                redisUtil.hDel(Constant.VPS_NODE_SPEED + vpsId, serverNames);
        }

        return Result.ok("删除成功");
    }

    @DeleteMapping("deleteServerNode")
    @Operation(summary = "删除服务器节点")
    @PreAuthorize("hasAuthority('mascontrol:speed')")
    public Result<String> deleteServerNode(@RequestBody FollowTestServerVO vo) {
            for (String serverNode : vo.getServerNodeList()) {
                long count = followTraderService.count(new LambdaQueryWrapper<FollowTraderEntity>()
                        .eq(FollowTraderEntity::getLoginNode, serverNode)
                        .eq(FollowTraderEntity::getStatus, CloseOrOpenEnum.CLOSE.getValue()));
                if (count > 0) {
                    return Result.error("该服务器节点账号数量不为0，无法删除");
                }
                //删掉redis中该服务器的数据
                FollowTestServerQuery query = new FollowTestServerQuery();
                query.setServerName(vo.getServerName());
                List<FollowTestDetailVO> newlist = followTestDetailService.selectServer(query);   //查询IsDefaultServer为0的数据
                List<FollowTestDetailVO> defaultServerNodes = newlist.stream()
                        .filter(vos -> {
                            Integer isDefaultServer = vos.getIsDefaultServer();
                            return isDefaultServer != null && isDefaultServer == 0;
                        })
                        .collect(Collectors.toList());

                for (FollowTestDetailVO entity : defaultServerNodes) {
                    Integer vpsId = entity.getVpsId();
                    String serverName = entity.getServerName();
                    String node = (String) redisUtil.hGet(Constant.VPS_NODE_SPEED + vpsId, serverName);
                    log.info("node:" + node);
                    if (serverNode.equals(node) && node != null) {
                        // 删除键名
                        redisUtil.hDel(Constant.VPS_NODE_SPEED + vpsId, serverName);
                        // 找到速度最快的非默认服务器节点

                        FollowTestDetailVO fastestNode = newlist.stream()
                                .filter(s -> s.getServerNode() != null && !s.getServerNode().equals(serverNode))
                                .filter(s -> s.getSpeed() != null && s.getSpeed() > 0)
                                .filter(s -> s.getVpsId().equals(vpsId))
                                .min(Comparator.comparingInt(s -> {
                                    Integer speed = s.getSpeed();
                                    return speed != null ? speed : Integer.MAX_VALUE; // 防止空指针异常
                                }))
                                .orElse(null);
                        log.info("fastestNode:{}" + fastestNode);
                        if (fastestNode != null) {
                            // 修改 fastestNode 中 isDefaultServer 为 0
                            fastestNode.setIsDefaultServer(0);
                            // 更新数据库
                            followTestDetailService.update(fastestNode);
                            // 更新 Redis 中的节点为最快的节点
                            redisUtil.hSet(Constant.VPS_NODE_SPEED + vpsId, fastestNode.getServerName(), fastestNode.getServerNode());
                        }
//                } else {
//                    log.warn("未找到有效的最快节点");
//                    continue;
//                }
                    }
                    followTestDetailService.remove(new LambdaQueryWrapper<FollowTestDetailEntity>().eq(FollowTestDetailEntity::getServerNode, serverNode));
                    //切分serverNode节点
                    String[] serverNodeArray = serverNode.split(":");
                    followBrokeServerService.remove(new LambdaQueryWrapper<FollowBrokeServerEntity>().eq(FollowBrokeServerEntity::getServerNode, serverNodeArray[0]).eq(FollowBrokeServerEntity::getServerPort, serverNodeArray[1]));
                }
            }
            return Result.ok("删除成功");

    }

    @GetMapping("pageSetting")
    @Operation(summary = "配置开关")
    @PreAuthorize("hasAuthority('mascontrol:speed')")
    public Result<PageResult<FollowSpeedSettingVO>> page(@ParameterObject @Valid FollowSpeedSettingQuery query){
        if (query.getPage() == null || query.getLimit() == null){
            return Result.error("未传页码或条数");
        }
        PageResult<FollowSpeedSettingVO> page = followSpeedSettingService.page(query);

        return Result.ok(page);
    }

    @PutMapping("updateSetting")
    @Operation(summary = "修改配置")
    @OperateLog(type = OperateTypeEnum.UPDATE)
    @PreAuthorize("hasAuthority('mascontrol:speed')")
    public Result<String> updates(@RequestBody @Valid FollowSpeedSettingVO vo){
        followSpeedSettingService.update(vo);

        return Result.ok();
    }

    /**
     * 测试
     * @param vo
     * @return
     */
    @PostMapping("redis")
    @Operation(summary = "redis测试")
    public Result<String> redis(@RequestBody FollowTestDetailVO vo){
        redisUtil.hSet(Constant.VPS_NODE_SPEED + vo.getVpsId(), vo.getServerName(), vo.getServerNode());
        return Result.ok("redis测试成功");
    }
}
package net.maku.followcom.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fhs.trans.service.impl.TransService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.AllArgsConstructor;
import net.maku.followcom.convert.FollowTestSpeedConvert;
import net.maku.followcom.dao.FollowTestSpeedDao;
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.VpsSpendEnum;
import net.maku.followcom.query.FollowTestServerQuery;
import net.maku.followcom.query.FollowTestSpeedQuery;
import net.maku.followcom.service.*;
import net.maku.followcom.vo.FollowTestDetailVO;
import net.maku.followcom.vo.FollowTestSpeedExcelVO;
import net.maku.followcom.vo.FollowTestSpeedVO;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.common.utils.ExcelUtils;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.common.utils.Result;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;

import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousSocketChannel;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 测速记录
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Service
@AllArgsConstructor
public class FollowTestSpeedServiceImpl extends BaseServiceImpl<FollowTestSpeedDao, FollowTestSpeedEntity> implements FollowTestSpeedService {
    private static final Logger log = LoggerFactory.getLogger(FollowTestSpeedServiceImpl.class);
    private final TransService transService;
    private final FollowTestDetailService followTestDetailService;
    private final FollowPlatformService followPlatformService;
    private final FollowBrokeServerService followBrokeServerService;
    private final FollowVpsService followVpsService;
    private final RedisUtil redisUtil;

    @Override
    public PageResult<FollowTestSpeedVO> page(FollowTestSpeedQuery query) {
        IPage<FollowTestSpeedEntity> page = baseMapper.selectPage(getPage(query), getWrapper(query));

        return new PageResult<>(FollowTestSpeedConvert.INSTANCE.convertList(page.getRecords()), page.getTotal());
    }


    private LambdaQueryWrapper<FollowTestSpeedEntity> getWrapper(FollowTestSpeedQuery query) {
        LambdaQueryWrapper<FollowTestSpeedEntity> wrapper = Wrappers.lambdaQuery();
        wrapper.orderByDesc(FollowTestSpeedEntity::getDoTime);

        return wrapper;
    }


    @Override
    public FollowTestSpeedVO get(Long id) {
        FollowTestSpeedEntity entity = baseMapper.selectById(id);
        FollowTestSpeedVO vo = FollowTestSpeedConvert.INSTANCE.convert(entity);

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(FollowTestSpeedVO vo) {
        FollowTestSpeedEntity entity = FollowTestSpeedConvert.INSTANCE.convert(vo);

        baseMapper.insert(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FollowTestSpeedVO vo) {
        FollowTestSpeedEntity entity = FollowTestSpeedConvert.INSTANCE.convert(vo);

        updateById(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> idList) {
        removeByIds(idList);

    }


    @Override
    public void export() {
        List<FollowTestSpeedExcelVO> excelList = FollowTestSpeedConvert.INSTANCE.convertExcelList(list());
        transService.transBatch(excelList);
        ExcelUtils.excelExport(FollowTestSpeedExcelVO.class, "测速记录", null, excelList);
    }

    @Override
//    public boolean measure(List<String> servers, FollowVpsEntity vpsEntity, Integer testId, LocalDateTime measureTime) {
//        log.info("开始测速时间：{}", measureTime);
//
//        // 获取服务器列表
//        List<FollowBrokeServerEntity> serverList = followBrokeServerService.listByServerName(servers);
//
//        // 按服务器名称分组
//        Map<String, List<FollowBrokeServerEntity>> serverMap = serverList.stream()
//                .collect(Collectors.groupingBy(FollowBrokeServerEntity::getServerName));
//
//        // 创建一个固定大小的线程池
//        ExecutorService executorService = Executors.newFixedThreadPool(20); // 可根据需求调整线程池大小
////        ConcurrentLinkedQueue<FollowTestDetailEntity> entitiesToSave = new ConcurrentLinkedQueue<>();
//        List<FollowTestDetailEntity> entitiesToSave = Collections.synchronizedList(new ArrayList<>());
//        // 提交每个测速任务到线程池
//        for (Map.Entry<String, List<FollowBrokeServerEntity>> entry : serverMap.entrySet()) {
//            List<FollowBrokeServerEntity> serverNodes = entry.getValue();
//
//            for (FollowBrokeServerEntity serverNode : serverNodes) {
//                String ipAddress = serverNode.getServerNode(); // 目标 IP 地址
//                int port = Integer.parseInt(serverNode.getServerPort()); // 目标端口号
//
//                // 提交测速任务到线程池
//                executorService.submit(() -> {
//                    int retryCount = 0; // 重试次数
//                    Integer speed = null; // 初始化速度为 null
//
//                    while (retryCount < 2) {
//                        try {
//                            AsynchronousSocketChannel socketChannel = AsynchronousSocketChannel.open();
//                            long startTime = System.currentTimeMillis(); // 记录起始时间
//                            Future<Void> future = socketChannel.connect(new InetSocketAddress(ipAddress, port));
//
//                            long timeout = 5000; // 设置超时时间
//                            try {
//                                future.get(timeout, TimeUnit.MILLISECONDS);
//                            } catch (TimeoutException e) {
//                                retryCount++; // 增加重试次数
//                                if (retryCount == 2) {
//                                    log.error("超时重试3次失败，目标地址: {}:{}", ipAddress, port);
//                                    break; // 超过最大重试次数后跳出循环
//                                }
//                                continue; // 如果超时，则重试
//                            }
//
//                            long endTime = System.currentTimeMillis(); // 记录结束时间
//                            long duration = endTime - startTime; // 计算测速时长
//
//                            speed = (int) duration; // 设置速度
//                            break; // 测试成功，跳出重试循环
//                        } catch (Exception e) {
//                            log.error("测速失败，目标地址: {}:{}, 错误信息: {}", ipAddress, port, e.getMessage());
//                            break; // 出现异常时跳出重试循环
//                        }
//                    }
//
//                    // 保存测速结果
//                    FollowTestDetailEntity newEntity = new FollowTestDetailEntity();
//                    newEntity.setServerName(serverNode.getServerName());
//                    newEntity.setServerId(serverNode.getId());
//                    newEntity.setPlatformType("MT4");
//                    newEntity.setServerNode(serverNode.getServerNode() + ":" + serverNode.getServerPort());
//                    newEntity.setVpsName(vpsEntity.getName());
//                    newEntity.setVpsId(vpsEntity.getId());
//                    newEntity.setSpeed(speed);
//                    newEntity.setTestId(testId);
//                    newEntity.setTestUpdateTime(measureTime);
//
//                    // 获取最新的服务器更新时间
//                    List<FollowTestDetailVO> vo = (List<FollowTestDetailVO>) redisUtil.get(Constant.VPS_NODE_SPEED + "detail");
//                    List<FollowTestDetailVO> detailVOList = vo.stream()
//                            .filter(detail -> detail.getServerName().equals(serverNode.getServerName())
//                                    && detail.getServerNode().equals(serverNode.getServerNode() + ":" + serverNode.getServerPort()))
//                            .collect(Collectors.toList());
//
//                    // 拿时间最新的一条数据
//                    FollowTestDetailVO detailVO = detailVOList.stream()
//                            .max(Comparator.comparing(FollowTestDetailVO::getCreateTime))
//                            .orElse(null);
//
//                    if (detailVO == null) {
//                        log.warn("detailVO为空的是 : {}:{}", serverNode.getServerName(), serverNode.getServerNode() + ":" + serverNode.getServerPort());
//                        newEntity.setServerUpdateTime(null);
//                        newEntity.setIsDefaultServer(1);
//                    } else {
//                        newEntity.setServerUpdateTime(detailVO.getServerUpdateTime());
//                        newEntity.setIsDefaultServer(detailVO.getIsDefaultServer() != null ? detailVO.getIsDefaultServer() : 1);
//                    }
//                    // 将结果加入到待保存列表
//                    entitiesToSave.add(newEntity);
//                });
//            }
//        }
//
//        // 关闭线程池并等待所有任务完成
//        executorService.shutdown();
//        try {
//            if (!executorService.awaitTermination(1, TimeUnit.HOURS)) {  // 设置最大等待时间，避免无限期等待
//                executorService.shutdownNow();
//            }
//        } catch (InterruptedException e) {
//            executorService.shutdownNow();
//            Thread.currentThread().interrupt();
//        }
//
//        // 批量插入所有测速结果
//        if (!entitiesToSave.isEmpty()) {
//            followTestDetailService.saveBatch(entitiesToSave);
//        }
//
//        log.info("==========所有测速记录入库成功");
//        return true; // 返回 true 表示所有任务提交成功
//    }

    public boolean measure(List<String> servers, FollowVpsEntity vpsEntity, Integer testId, LocalDateTime measureTime) {
        log.info("开始测速时间：{}", measureTime);

        // 获取服务器列表
        List<FollowBrokeServerEntity> serverList = followBrokeServerService.listByServerName(servers);

        // 按服务器名称分组
        Map<String, List<FollowBrokeServerEntity>> serverMap = serverList.stream()
                .collect(Collectors.groupingBy(FollowBrokeServerEntity::getServerName));

        // 创建一个固定大小的线程池
        ExecutorService executorService = Executors.newFixedThreadPool(20); // 可根据需求调整线程池大小
//        ConcurrentLinkedQueue<FollowTestDetailEntity> entitiesToSave = new ConcurrentLinkedQueue<>();
        List<FollowTestDetailEntity> entitiesToSave = Collections.synchronizedList(new ArrayList<>());

        List<FollowTestDetailVO> detailVOList = followTestDetailService.selectServer(new FollowTestServerQuery());
        Map<String, FollowTestDetailVO> map = detailVOList.stream()
                .filter(detail -> detail.getIsDefaultServer() != null && detail.getIsDefaultServer() == 0)
                .collect(Collectors.toMap(
                        FollowTestDetailVO::getServerName,
                        detail -> detail,                       // 保留每个 ServerName 的第一条数据
                        (existing, replacement) -> existing));

        // 将 map 的值转回列表
        List<FollowTestDetailVO> collect = map.values().stream().collect(Collectors.toList());
        //severName默认节点
        Map<String, String> defaultServerNodeMap = new HashMap<>();
        //更新时间
        Map<String, LocalDateTime> serverUpdateTimeMap = new HashMap<>();
        if (ObjectUtil.isNotEmpty(collect)) {
            defaultServerNodeMap = collect.stream()
                    .filter(item -> item.getServerName() != null && item.getServerNode() != null)
                    .collect(Collectors.toMap(FollowTestDetailVO::getServerName, FollowTestDetailVO::getServerNode, (existing, replacement) -> existing));

            serverUpdateTimeMap = collect.stream()
                    .filter(item -> item.getServerName() != null && item.getServerUpdateTime() != null)
                    .collect(Collectors.toMap(FollowTestDetailVO::getServerName, FollowTestDetailVO::getServerUpdateTime, (existing, replacement) -> existing));
        }
        // 提交每个测速任务到线程池
        for (Map.Entry<String, List<FollowBrokeServerEntity>> entry : serverMap.entrySet()) {
            List<FollowBrokeServerEntity> serverNodes = entry.getValue();

            for (FollowBrokeServerEntity serverNode : serverNodes) {
                if (ObjectUtil.isEmpty(serverNode.getServerNode()) || ObjectUtil.isEmpty(serverNode.getServerPort())){
                    // 如果 IP 地址或端口号为 null，则跳过测速
                    break;
                }
                String ipAddress = serverNode.getServerNode(); // 目标 IP 地址
                int port = Integer.parseInt(serverNode.getServerPort()); // 目标端口号

                LocalDateTime localDateTime = serverUpdateTimeMap.get(serverNode.getServerName());
                String defaultServerNode = defaultServerNodeMap.get(serverNode.getServerName()) != null ? defaultServerNodeMap.get(serverNode.getServerName()) : "null";
                // 提交测速任务到线程池
                executorService.submit(() -> {
                    int retryCount = 0; // 重试次数
                    Integer speed = null; // 初始化速度为 null

                    while (retryCount < 1) {
                        try {
                            AsynchronousSocketChannel socketChannel = AsynchronousSocketChannel.open();
                            long startTime = System.currentTimeMillis(); // 记录起始时间
                            Future<Void> future = socketChannel.connect(new InetSocketAddress(ipAddress, port));

                            long timeout = 2000; // 设置超时时间
                            try {
                                future.get(timeout, TimeUnit.MILLISECONDS);
                            } catch (TimeoutException e) {
                                retryCount++; // 增加重试次数
                                if (retryCount == 1) {
//                                    log.error("超时重试2次失败，目标地址: {}:{}", ipAddress, port);
                                    break; // 超过最大重试次数后跳出循环
                                }
                                continue; // 如果超时，则重试
                            }

                            long endTime = System.currentTimeMillis(); // 记录结束时间
                            long duration = endTime - startTime; // 计算测速时长

                            speed = (int) duration; // 设置速度
                            break; // 测试成功，跳出重试循环
                        } catch (Exception e) {
//                            log.error("测速失败，目标地址: {}:{}, 错误信息: {}", ipAddress, port, e.getMessage());
                            break; // 出现异常时跳出重试循环
                        }
                    }

                    // 保存测速结果
                    FollowTestDetailEntity newEntity = new FollowTestDetailEntity();
                    newEntity.setServerName(serverNode.getServerName());
                    newEntity.setServerId(serverNode.getId());
                    newEntity.setPlatformType("MT4");
                    newEntity.setServerNode(serverNode.getServerNode() + ":" + serverNode.getServerPort());
                    newEntity.setVpsName(vpsEntity.getName());
                    newEntity.setVpsId(vpsEntity.getId());
                    newEntity.setSpeed(speed);
                    newEntity.setTestId(testId);
                    newEntity.setTestUpdateTime(measureTime);
                    newEntity.setIsDefaultServer(defaultServerNode.equals(serverNode.getServerNode() + ":" + serverNode.getServerPort()) ? 0 : 1);
                    newEntity.setServerUpdateTime(localDateTime != null ? localDateTime : null);
//                    newEntity.setServerUpdateTime(localDateTime != null ? LocalDateTime.parse(DateUtil.format(localDateTime, "yyyy-MM-dd HH:mm:ss")) : null);
                    // 获取最新的服务器更新时间
//                    List<FollowTestDetailVO> vo = (List<FollowTestDetailVO>) redisUtil.get(Constant.VPS_NODE_SPEED + "detail");
//                    List<FollowTestDetailVO> detailVOList = vo.stream()
//                            .filter(detail -> detail.getServerName().equals(serverNode.getServerName())
//                                    && detail.getServerNode().equals(serverNode.getServerNode() + ":" + serverNode.getServerPort()))
//                            .collect(Collectors.toList());
//
//                    // 拿时间最新的一条数据
//                    FollowTestDetailVO detailVO = detailVOList.stream()
//                            .max(Comparator.comparing(FollowTestDetailVO::getCreateTime))
//                            .orElse(null);
//
//                    if (detailVO == null) {
//                        log.warn("detailVO为空的是 : {}:{}", serverNode.getServerName(), serverNode.getServerNode() + ":" + serverNode.getServerPort());
//                        newEntity.setServerUpdateTime(null);
//                        newEntity.setIsDefaultServer(1);
//                    } else {
//                        newEntity.setServerUpdateTime(detailVO.getServerUpdateTime());
//                        newEntity.setIsDefaultServer(detailVO.getIsDefaultServer() != null ? detailVO.getIsDefaultServer() : 1);
//                    }

                    // 将结果加入到待保存列表
                    entitiesToSave.add(newEntity);
                });
            }
        }

        // 关闭线程池并等待所有任务完成
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(1, TimeUnit.HOURS)) {  // 设置最大等待时间，避免无限期等待
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // 批量插入所有测速结果
        if (!entitiesToSave.isEmpty()) {
            log.info("准备批量插入 {} 条测速记录", entitiesToSave.size());
            try {
                followTestDetailService.saveBatch(entitiesToSave);
                log.info("成功批量插入 {} 条测速记录", entitiesToSave.size());
            } catch (Exception e) {
                log.error("批量插入测速记录失败，错误信息: {}", e.getMessage(), e);
                throw e; // 或者根据业务需求进行其他处理
            }
        }
//        if (!entitiesToSave.isEmpty()) {
//            log.info("准备批量插入 {} 条测速记录", entitiesToSave.size());
//            int batchSize = 1000;  // 根据需求调整
//            int totalSize = entitiesToSave.size();
//            for (int i = 0; i < totalSize; i += batchSize) {
//                int end = Math.min(i + batchSize, totalSize);
//                List<FollowTestDetailEntity> batch = entitiesToSave.subList(i, end);
//                try {
//                    followTestDetailService.saveBatch(batch);
//                    log.info("成功批量插入 {} 条测速记录，当前进度: {}/{}", batch.size(), end, totalSize);
//                } catch (Exception e) {
//                    log.error("批量插入测速记录失败，错误信息: {}", e.getMessage(), e);
//                    throw e; // 或者根据业务需求进行其他处理
//                }
//            }
//        }

        log.info("==========所有测速记录入库成功");
        return true; // 返回 true 表示所有任务提交成功
    }



//    public Boolean measure(List<String> servers, FollowVpsEntity vpsEntity, Integer testId, LocalDateTime measureTime) {
//        log.info("开始测速时间：{}", measureTime);
//
//        // 获取服务器列表
//        List<FollowBrokeServerEntity> serverList = followBrokeServerService.listByServerName(servers);
//
//        // 按服务器名称分组
//        Map<String, List<FollowBrokeServerEntity>> serverMap = serverList.stream()
//                .collect(Collectors.groupingBy(FollowBrokeServerEntity::getServerName));
//
//        // 创建一个固定大小的线程池
//        ExecutorService executorService = Executors.newFixedThreadPool(20); // 可根据需求调整线程池大小
//        List<FollowTestDetailEntity> entitiesToSave = Collections.synchronizedList(new ArrayList<>());
//
//        // 使用 CompletableFuture 来管理异步任务
//        List<CompletableFuture<Void>> futures = new ArrayList<>();
//
//        // 提交每个测速任务到线程池
//        for (Map.Entry<String, List<FollowBrokeServerEntity>> entry : serverMap.entrySet()) {
//            List<FollowBrokeServerEntity> serverNodes = entry.getValue();
//
//            for (FollowBrokeServerEntity serverNode : serverNodes) {
//                String ipAddress = serverNode.getServerNode(); // 目标 IP 地址
//                int port = Integer.parseInt(serverNode.getServerPort()); // 目标端口号
//
//                // 提交测速任务到线程池
//                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
//                    int retryCount = 0; // 重试次数
//                    Integer speed = null; // 初始化速度为 null
//
//                    while (retryCount < 2) {
//                        try {
//                            AsynchronousSocketChannel socketChannel = AsynchronousSocketChannel.open();
//                            long startTime = System.currentTimeMillis(); // 记录起始时间
//                            Future<Void> futureConnect = socketChannel.connect(new InetSocketAddress(ipAddress, port));
//
//                            long timeout = 5000; // 设置超时时间
//                            try {
//                                futureConnect.get(timeout, TimeUnit.MILLISECONDS);
//                            } catch (TimeoutException e) {
//                                retryCount++; // 增加重试次数
//                                if (retryCount == 2) {
//                                    log.error("超时重试3次失败，目标地址: {}:{}", ipAddress, port);
//                                    break; // 超过最大重试次数后跳出循环
//                                }
//                                continue; // 如果超时，则重试
//                            }
//
//                            long endTime = System.currentTimeMillis(); // 记录结束时间
//                            long duration = endTime - startTime; // 计算测速时长
//
//                            speed = (int) duration; // 设置速度
//                            break; // 测试成功，跳出重试循环
//                        } catch (Exception e) {
//                            log.error("测速失败，目标地址: {}:{}, 错误信息: {}", ipAddress, port, e.getMessage());
//                            break; // 出现异常时跳出重试循环
//                        }
//                    }
//
//                    // 保存测速结果
//                    FollowTestDetailEntity newEntity = new FollowTestDetailEntity();
//                    newEntity.setServerName(serverNode.getServerName());
//                    newEntity.setServerId(serverNode.getId());
//                    newEntity.setPlatformType("MT4");
//                    newEntity.setServerNode(serverNode.getServerNode() + ":" + serverNode.getServerPort());
//                    newEntity.setVpsName(vpsEntity.getName());
//                    newEntity.setVpsId(vpsEntity.getId());
//                    newEntity.setSpeed(speed);
//                    newEntity.setTestId(testId);
//                    newEntity.setTestUpdateTime(measureTime);
//
//                    // 获取最新的服务器更新时间
//                    List<FollowTestDetailVO> vo = (List<FollowTestDetailVO>) redisUtil.get(Constant.VPS_NODE_SPEED + "detail");
//                    List<FollowTestDetailVO> detailVOList = vo.stream()
//                            .filter(detail -> detail.getServerName().equals(serverNode.getServerName())
//                                    && detail.getServerNode().equals(serverNode.getServerNode() + ":" + serverNode.getServerPort()))
//                            .collect(Collectors.toList());
//
//                    // 拿时间最新的一条数据
//                    FollowTestDetailVO detailVO = detailVOList.stream()
//                            .max(Comparator.comparing(FollowTestDetailVO::getCreateTime))
//                            .orElse(null);
//
//                    if (detailVO == null) {
//                        log.warn("detailVO为空的是 : {}:{}", serverNode.getServerName(), serverNode.getServerNode() + ":" + serverNode.getServerPort());
//                        newEntity.setServerUpdateTime(null);
//                        newEntity.setIsDefaultServer(1);
//                    } else {
//                        newEntity.setServerUpdateTime(detailVO.getServerUpdateTime());
//                        newEntity.setIsDefaultServer(detailVO.getIsDefaultServer() != null ? detailVO.getIsDefaultServer() : 1);
//                    }
//
//                    // 将结果加入到待保存列表
//                    entitiesToSave.add(newEntity);
//                }, executorService);
//
//                futures.add(future);
//            }
//        }
//
//        // 关闭线程池但不等待任务完成
//        executorService.shutdown();
//
//        // 创建一个 CompletableFuture 来管理所有任务的完成状态
//        CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
//
//        // 返回一个 CompletableFuture，表示测速任务的完成状态
//        return allFutures.thenApply(v -> {
//            // 批量插入所有测速结果
//            if (!entitiesToSave.isEmpty()) {
//                log.info("准备批量插入 {} 条测速记录", entitiesToSave.size());
//                followTestDetailService.saveBatch(entitiesToSave);
//            }
//
//            log.info("==========所有测速记录入库成功");
//            return true; // 返回 true 表示所有任务已完成
//        }).exceptionally(ex -> {
//            log.error("测速任务处理过程中发生异常", ex);
//            return false; // 返回 false 表示测速任务处理失败
//        });
//
//    }

    public boolean measureTask(List<String> servers, FollowVpsEntity vpsEntity, Integer testId, LocalDateTime measureTime) {
        log.info("开始测速时间：{}", measureTime);

        // 获取服务器列表
        List<FollowBrokeServerEntity> serverList = followBrokeServerService.listByServerName(servers);

        // 按服务器名称分组
        Map<String, List<FollowBrokeServerEntity>> serverMap = serverList.stream()
                .collect(Collectors.groupingBy(FollowBrokeServerEntity::getServerName));

        // 创建一个固定大小的线程池
        ExecutorService executorService = Executors.newFixedThreadPool(20); // 可根据需求调整线程池大小
//        ConcurrentLinkedQueue<FollowTestDetailEntity> entitiesToSave = new ConcurrentLinkedQueue<>();
        List<FollowTestDetailEntity> entitiesToSave = Collections.synchronizedList(new ArrayList<>());

        List<FollowTestDetailVO> detailVOList = followTestDetailService.selectServer(new FollowTestServerQuery());
        Map<String, FollowTestDetailVO> map = detailVOList.stream()
                .filter(detail -> detail.getIsDefaultServer() != null && detail.getIsDefaultServer() == 0)
                .collect(Collectors.toMap(
                        FollowTestDetailVO::getServerName,
                        detail -> detail,                       // 保留每个 ServerName 的第一条数据
                        (existing, replacement) -> existing));

        // 将 map 的值转回列表
        List<FollowTestDetailVO> collect = map.values().stream().collect(Collectors.toList());
        //severName默认节点
        Map<String, String> defaultServerNodeMap = new HashMap<>();
        //更新时间
        Map<String, LocalDateTime> serverUpdateTimeMap = new HashMap<>();
        if (ObjectUtil.isNotEmpty(collect)) {
            defaultServerNodeMap = collect.stream()
                    .filter(item -> item.getServerName() != null && item.getServerNode() != null)
                    .collect(Collectors.toMap(FollowTestDetailVO::getServerName, FollowTestDetailVO::getServerNode, (existing, replacement) -> existing));

            serverUpdateTimeMap = collect.stream()
                    .filter(item -> item.getServerName() != null && item.getServerUpdateTime() != null)
                    .collect(Collectors.toMap(FollowTestDetailVO::getServerName, FollowTestDetailVO::getServerUpdateTime, (existing, replacement) -> existing));
        }
        // 提交每个测速任务到线程池
        for (Map.Entry<String, List<FollowBrokeServerEntity>> entry : serverMap.entrySet()) {
            List<FollowBrokeServerEntity> serverNodes = entry.getValue();

            for (FollowBrokeServerEntity serverNode : serverNodes) {
                if (ObjectUtil.isEmpty(serverNode.getServerNode()) || ObjectUtil.isEmpty(serverNode.getServerPort()) ){
                    // 如果 IP 地址或端口号为 null，则跳过测速
                    break;
                }
                String ipAddress = serverNode.getServerNode(); // 目标 IP 地址
                int port = Integer.parseInt(serverNode.getServerPort()); // 目标端口号

                LocalDateTime localDateTime = serverUpdateTimeMap.get(serverNode.getServerName());
                String defaultServerNode = defaultServerNodeMap.get(serverNode.getServerName()) != null ? defaultServerNodeMap.get(serverNode.getServerName()) : "null";
                // 提交测速任务到线程池
                executorService.submit(() -> {
                    int retryCount = 0; // 重试次数
                    Integer speed = null; // 初始化速度为 null

                    while (retryCount < 2) {
                        try {
                            AsynchronousSocketChannel socketChannel = AsynchronousSocketChannel.open();
                            long startTime = System.currentTimeMillis(); // 记录起始时间
                            Future<Void> future = socketChannel.connect(new InetSocketAddress(ipAddress, port));

                            long timeout = 5000; // 设置超时时间
                            try {
                                future.get(timeout, TimeUnit.MILLISECONDS);
                            } catch (TimeoutException e) {
                                retryCount++; // 增加重试次数
                                if (retryCount == 2) {
                                    log.error("超时重试3次失败，目标地址: {}:{}", ipAddress, port);
                                    break; // 超过最大重试次数后跳出循环
                                }
                                continue; // 如果超时，则重试
                            }

                            long endTime = System.currentTimeMillis(); // 记录结束时间
                            long duration = endTime - startTime; // 计算测速时长

                            speed = (int) duration; // 设置速度
                            break; // 测试成功，跳出重试循环
                        } catch (Exception e) {
                            log.error("测速失败，目标地址: {}:{}, 错误信息: {}", ipAddress, port, e.getMessage());
                            break; // 出现异常时跳出重试循环
                        }
                    }

                    // 保存测速结果
                    FollowTestDetailEntity newEntity = new FollowTestDetailEntity();
                    newEntity.setServerName(serverNode.getServerName());
                    newEntity.setServerId(serverNode.getId());
                    newEntity.setPlatformType("MT4");
                    newEntity.setServerNode(serverNode.getServerNode() + ":" + serverNode.getServerPort());
                    newEntity.setVpsName(vpsEntity.getName());
                    newEntity.setVpsId(vpsEntity.getId());
                    newEntity.setSpeed(speed);
                    newEntity.setTestId(testId);
                    newEntity.setTestUpdateTime(measureTime);
                    newEntity.setIsDefaultServer(1);
                    newEntity.setServerUpdateTime(localDateTime != null ? localDateTime : null);


                    // 将结果加入到待保存列表
                    entitiesToSave.add(newEntity);
                });
            }
        }

        // 关闭线程池并等待所有任务完成
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(1, TimeUnit.HOURS)) {  // 设置最大等待时间，避免无限期等待
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // 批量插入所有测速结果
        if (!entitiesToSave.isEmpty()) {
            log.info("准备批量插入 {} 条测速记录", entitiesToSave.size());
            try {
                followTestDetailService.saveBatch(entitiesToSave);
                log.info("成功批量插入 {} 条测速记录", entitiesToSave.size());
            } catch (Exception e) {
                log.error("批量插入测速记录失败，错误信息: {}", e.getMessage(), e);
                throw e; // 或者根据业务需求进行其他处理
            }
        }

        log.info("==========所有测速记录入库成功");
        return true; // 返回 true 表示所有任务提交成功
    }

//    @Override
//    public boolean measureTask(List<String> servers, FollowVpsEntity vpsEntity, Integer testId, LocalDateTime measureTime) {
//        log.info("开始测速时间：{}", measureTime);
//
//        // 获取服务器列表
//        List<FollowBrokeServerEntity> serverList = followBrokeServerService.listByServerName(servers);
//
//        // 按服务器名称分组
//        Map<String, List<FollowBrokeServerEntity>> serverMap = serverList.stream()
//                .collect(Collectors.groupingBy(FollowBrokeServerEntity::getServerName));
//
//        // 创建一个固定大小的线程池
//        ExecutorService executorService = Executors.newFixedThreadPool(20); // 可根据需求调整线程池大小
////        ConcurrentLinkedQueue<FollowTestDetailEntity> entitiesToSave = new ConcurrentLinkedQueue<>();
//        List<FollowTestDetailEntity> entitiesToSave = Collections.synchronizedList(new ArrayList<>());
//
////        List<FollowTestDetailVO> detailVOList = followTestDetailService.selectServer(new FollowTestServerQuery());
////        Map<String, FollowTestDetailVO> map = detailVOList.stream()
////                .filter(detail -> detail.getIsDefaultServer() != null && detail.getIsDefaultServer() == 0)
////                .collect(Collectors.toMap(
////                        FollowTestDetailVO::getServerName,
////                        detail -> detail,                       // 保留每个 ServerName 的第一条数据
////                        (existing, replacement) -> existing));
////
////        // 将 map 的值转回列表
////        List<FollowTestDetailVO> collect = map.values().stream().collect(Collectors.toList());
////        //severName默认节点
////        Map<String, String> defaultServerNodeMap = new HashMap<>();
////        //更新时间
////        Map<String, LocalDateTime> serverUpdateTimeMap = new HashMap<>();
////        if (ObjectUtil.isNotEmpty(collect)) {
////            defaultServerNodeMap = collect.stream()
////                    .filter(item -> item.getServerName() != null && item.getServerNode() != null)
////                    .collect(Collectors.toMap(FollowTestDetailVO::getServerName, FollowTestDetailVO::getServerNode, (existing, replacement) -> existing));
////
////            serverUpdateTimeMap = collect.stream()
////                    .filter(item -> item.getServerName() != null && item.getServerUpdateTime() != null)
////                    .collect(Collectors.toMap(FollowTestDetailVO::getServerName, FollowTestDetailVO::getServerUpdateTime, (existing, replacement) -> existing));
////        }
//        // 提交每个测速任务到线程池
//        for (Map.Entry<String, List<FollowBrokeServerEntity>> entry : serverMap.entrySet()) {
//            List<FollowBrokeServerEntity> serverNodes = entry.getValue();
//
//            for (FollowBrokeServerEntity serverNode : serverNodes) {
//                String ipAddress = serverNode.getServerNode(); // 目标 IP 地址
//                int port = Integer.parseInt(serverNode.getServerPort()); // 目标端口号
//
////                LocalDateTime localDateTime = serverUpdateTimeMap.get(serverNode.getServerName());
////                String defaultServerNode = defaultServerNodeMap.get(serverNode.getServerName()) != null ? defaultServerNodeMap.get(serverNode.getServerName()) : "null";
//                // 提交测速任务到线程池
//                executorService.submit(() -> {
//                    int retryCount = 0; // 重试次数
//                    Integer speed = null; // 初始化速度为 null
//
//                    while (retryCount < 2) {
//                        try {
//                            AsynchronousSocketChannel socketChannel = AsynchronousSocketChannel.open();
//                            long startTime = System.currentTimeMillis(); // 记录起始时间
//                            Future<Void> future = socketChannel.connect(new InetSocketAddress(ipAddress, port));
//
//                            long timeout = 5000; // 设置超时时间
//                            try {
//                                future.get(timeout, TimeUnit.MILLISECONDS);
//                            } catch (TimeoutException e) {
//                                retryCount++; // 增加重试次数
//                                if (retryCount == 2) {
//                                    log.error("超时重试3次失败，目标地址: {}:{}", ipAddress, port);
//                                    break; // 超过最大重试次数后跳出循环
//                                }
//                                continue; // 如果超时，则重试
//                            }
//
//                            long endTime = System.currentTimeMillis(); // 记录结束时间
//                            long duration = endTime - startTime; // 计算测速时长
//
//                            speed = (int) duration; // 设置速度
//                            break; // 测试成功，跳出重试循环
//                        } catch (Exception e) {
//                            log.error("测速失败，目标地址: {}:{}, 错误信息: {}", ipAddress, port, e.getMessage());
//                            break; // 出现异常时跳出重试循环
//                        }
//                    }
//
//                    // 保存测速结果
//                    FollowTestDetailEntity newEntity = new FollowTestDetailEntity();
//                    newEntity.setServerName(serverNode.getServerName());
//                    newEntity.setServerId(serverNode.getId());
//                    newEntity.setPlatformType("MT4");
//                    newEntity.setServerNode(serverNode.getServerNode() + ":" + serverNode.getServerPort());
//                    newEntity.setVpsName(vpsEntity.getName());
//                    newEntity.setVpsId(vpsEntity.getId());
//                    newEntity.setSpeed(speed);
//                    newEntity.setTestId(testId);
//                    newEntity.setTestUpdateTime(measureTime);
//                    newEntity.setIsDefaultServer(1);
////                    newEntity.setIsDefaultServer(defaultServerNode.equals(serverNode.getServerNode() + ":" + serverNode.getServerPort()) ? 1 : 0);
////                    newEntity.setServerUpdateTime(localDateTime != null ? LocalDateTime.parse(DateUtil.format(localDateTime, "yyyy-MM-dd HH:mm:ss")) : null);
//
//                    // 获取最新的服务器更新时间
//                    List<FollowTestDetailVO> vo = (List<FollowTestDetailVO>) redisUtil.get(Constant.VPS_NODE_SPEED + "detail");
//                    List<FollowTestDetailVO> detailVOList = vo.stream()
//                            .filter(detail -> detail.getServerName().equals(serverNode.getServerName())
//                                    && detail.getServerNode().equals(serverNode.getServerNode() + ":" + serverNode.getServerPort()))
//                            .collect(Collectors.toList());
//
//                    // 拿时间最新的一条数据
//                    FollowTestDetailVO detailVO = detailVOList.stream()
//                            .max(Comparator.comparing(FollowTestDetailVO::getCreateTime))
//                            .orElse(null);
//
//                    if (detailVO == null) {
//                        log.warn(" deailVO为空的是 : {}:{}", serverNode.getServerName(), serverNode.getServerNode() + ":" + serverNode.getServerPort());
//                        newEntity.setServerUpdateTime(null);
//                    } else {
//                        newEntity.setServerUpdateTime(detailVO.getServerUpdateTime() != null ? detailVO.getServerUpdateTime() : null);
//                    }
//
//                    // 将结果加入到待保存列表
//                    entitiesToSave.add(newEntity);
//                });
//            }
//        }
//
//        // 关闭线程池并等待所有任务完成
//        executorService.shutdown();
//        try {
//            if (!executorService.awaitTermination(1, TimeUnit.HOURS)) {  // 设置最大等待时间，避免无限期等待
//                executorService.shutdownNow();
//            }
//        } catch (InterruptedException e) {
//            executorService.shutdownNow();
//            Thread.currentThread().interrupt();
//        }
//
//        // 批量插入所有测速结果
//        if (!entitiesToSave.isEmpty()) {
//            log.info("准备批量插入 {} 条测速记录", entitiesToSave.size());
//            followTestDetailService.saveBatch(entitiesToSave);
//        }
//
//        log.info("==========所有测速记录入库成功");
//        return true; // 返回 true 表示所有任务提交成功
//    }




//    @Override
//    public boolean measureTask(List<String> servers, FollowVpsEntity vpsEntity, Integer testId, LocalDateTime measureTime) {
//        log.info("开始测速时间：{}", measureTime);
//
//        // 获取服务器列表
//        List<FollowBrokeServerEntity> serverList = followBrokeServerService.listByServerName(servers);
//
//        // 按服务器名称分组
//        Map<String, List<FollowBrokeServerEntity>> serverMap = serverList.stream()
//                .collect(Collectors.groupingBy(FollowBrokeServerEntity::getServerName));
//
//        // 创建一个固定大小的线程池
//        ExecutorService executorService = Executors.newFixedThreadPool(20); // 可根据需求调整线程池大小
////        ConcurrentLinkedQueue<FollowTestDetailEntity> entitiesToSave = new ConcurrentLinkedQueue<>();
//        List<FollowTestDetailEntity> entitiesToSave = Collections.synchronizedList(new ArrayList<>());
//        // 提交每个测速任务到线程池
//        for (Map.Entry<String, List<FollowBrokeServerEntity>> entry : serverMap.entrySet()) {
//            List<FollowBrokeServerEntity> serverNodes = entry.getValue();
//
//            for (FollowBrokeServerEntity serverNode : serverNodes) {
//                String ipAddress = serverNode.getServerNode(); // 目标 IP 地址
//                int port = Integer.parseInt(serverNode.getServerPort()); // 目标端口号
//
//                // 提交测速任务到线程池
//                executorService.submit(() -> {
//                    int retryCount = 0; // 重试次数
//                    Integer speed = null; // 初始化速度为 null
//
//                    while (retryCount < 2) {
//                        try {
//                            AsynchronousSocketChannel socketChannel = AsynchronousSocketChannel.open();
//                            long startTime = System.currentTimeMillis(); // 记录起始时间
//                            Future<Void> future = socketChannel.connect(new InetSocketAddress(ipAddress, port));
//
//                            long timeout = 5000; // 设置超时时间
//                            try {
//                                future.get(timeout, TimeUnit.MILLISECONDS);
//                            } catch (TimeoutException e) {
//                                retryCount++; // 增加重试次数
//                                if (retryCount == 2) {
//                                    log.error("超时重试3次失败，目标地址: {}:{}", ipAddress, port);
//                                    break; // 超过最大重试次数后跳出循环
//                                }
//                                continue; // 如果超时，则重试
//                            }
//
//                            long endTime = System.currentTimeMillis(); // 记录结束时间
//                            long duration = endTime - startTime; // 计算测速时长
//
//                            speed = (int) duration; // 设置速度
//                            break; // 测试成功，跳出重试循环
//                        } catch (Exception e) {
//                            log.error("测速失败，目标地址: {}:{}, 错误信息: {}", ipAddress, port, e.getMessage());
//                            break; // 出现异常时跳出重试循环
//                        }
//                    }
//
//                    // 保存测速结果
//                    FollowTestDetailEntity newEntity = new FollowTestDetailEntity();
//                    newEntity.setServerName(serverNode.getServerName());
//                    newEntity.setServerId(serverNode.getId());
//                    newEntity.setPlatformType("MT4");
//                    newEntity.setServerNode(serverNode.getServerNode() + ":" + serverNode.getServerPort());
//                    newEntity.setVpsName(vpsEntity.getName());
//                    newEntity.setVpsId(vpsEntity.getId());
//                    newEntity.setSpeed(speed);
//                    newEntity.setTestId(testId);
//                    newEntity.setTestUpdateTime(measureTime);
//                    newEntity.setIsDefaultServer(1);
//
//                    // 获取最新的服务器更新时间
//                    List<FollowTestDetailVO> vo = (List<FollowTestDetailVO>) redisUtil.get(Constant.VPS_NODE_SPEED + "detail");
//                    List<FollowTestDetailVO> detailVOList = vo.stream()
//                            .filter(detail -> detail.getServerName().equals(serverNode.getServerName())
//                                    && detail.getServerNode().equals(serverNode.getServerNode() + ":" + serverNode.getServerPort()))
//                            .collect(Collectors.toList());
//
//                    // 拿时间最新的一条数据
//                    FollowTestDetailVO detailVO = detailVOList.stream()
//                            .max(Comparator.comparing(FollowTestDetailVO::getCreateTime))
//                            .orElse(null);
//
//                    if (detailVO == null) {
//                        log.warn(" deailVO为空的是 : {}:{}", serverNode.getServerName(), serverNode.getServerNode() + ":" + serverNode.getServerPort());
//                        newEntity.setServerUpdateTime(null);
//                    } else {
//                        newEntity.setServerUpdateTime(detailVO.getServerUpdateTime() != null ? detailVO.getServerUpdateTime() : null);
//                    }
//                    // 将结果加入到待保存列表
//                    entitiesToSave.add(newEntity);
//                });
//            }
//        }
//
//        // 关闭线程池并等待所有任务完成
//        executorService.shutdown();
//        try {
//            if (!executorService.awaitTermination(1, TimeUnit.HOURS)) {  // 设置最大等待时间，避免无限期等待
//                executorService.shutdownNow();
//            }
//        } catch (InterruptedException e) {
//            executorService.shutdownNow();
//            Thread.currentThread().interrupt();
//        }
//
//        // 批量插入所有测速结果
//        if (!entitiesToSave.isEmpty()) {
//            followTestDetailService.saveBatch(entitiesToSave);
//        }
//
//        log.info("==========所有测速记录入库成功");
//        return true; // 返回 true 表示所有任务提交成功
//    }

//    public boolean measureTask(List<String> servers, FollowVpsEntity vpsEntity, Integer testId, LocalDateTime measureTime) {
//        // 获取服务器列表
//        List<FollowBrokeServerEntity> serverList = followBrokeServerService.listByServerName(servers);
//        // 按服务器名称分组
//        Map<String, List<FollowBrokeServerEntity>> serverMap = serverList.stream()
//                .collect(Collectors.groupingBy(FollowBrokeServerEntity::getServerName));
//        // 创建一个固定大小的线程池
//        ExecutorService executorService = Executors.newFixedThreadPool(10); // 可根据需求调整线程池大小
//        // 提交每个测速任务到线程池
//        for (Map.Entry<String, List<FollowBrokeServerEntity>> entry : serverMap.entrySet()) {
//            List<FollowBrokeServerEntity> serverNodes = entry.getValue();
//
//            for (FollowBrokeServerEntity serverNode : serverNodes) {
//                String ipAddress = serverNode.getServerNode(); // 目标 IP 地址
//                int port = Integer.parseInt(serverNode.getServerPort()); // 目标端口号
//                List<FollowTestDetailVO> vo = (List<FollowTestDetailVO>) redisUtil.get(Constant.VPS_NODE_SPEED + "detail");
//                List<FollowTestDetailVO> detailVOList = vo.stream()
//                        .filter(detail -> detail.getServerName().equals(serverNode.getServerName()) && detail.getServerNode().equals(serverNode.getServerNode() + ":" + serverNode.getServerPort()))
//                        .collect(Collectors.toList());
//                // 拿时间最新的一条数据
//                FollowTestDetailVO detailVO = detailVOList.stream()
//                        .max(Comparator.comparing(FollowTestDetailVO::getCreateTime))
//                        .orElse(null);
//
//                // 提交测速任务到线程池
//                int retryCount = 0; // 重试次数
//                Integer speed = null; // 初始化速度为 null
//
//                while (retryCount < 2) {
//                    try {
//                        AsynchronousSocketChannel socketChannel = AsynchronousSocketChannel.open();
//                        long startTime = System.currentTimeMillis(); // 记录起始时间
//                        Future<Void> future = socketChannel.connect(new InetSocketAddress(ipAddress, port));
//
//                        long timeout = 5000; // 设置超时时间
//                        try {
//                            future.get(timeout, TimeUnit.MILLISECONDS);
//                        } catch (TimeoutException e) {
//                            retryCount++; // 增加重试次数
//                            if (retryCount == 2) {
//                                log.error("超时重试3次失败，目标地址: {}:{}", ipAddress, port);
//                                break; // 超过最大重试次数后跳出循环
//                            }
//                            continue; // 如果超时，则重试
//                        }
//
//                        long endTime = System.currentTimeMillis(); // 记录结束时间
//                        long duration = endTime - startTime; // 计算测速时长
//
//                        speed = (int) duration; // 设置速度
//                        break; // 测试成功，跳出重试循环
//                    } catch (Exception e) {
//                        log.error("测速失败，目标地址: {}:{}, 错误信息: {}", ipAddress, port, e.getMessage());
//                        break; // 出现异常时跳出重试循环
//                    }
//                }
//
//                // 保存测速结果，即使速度为 null
//                FollowTestDetailEntity newEntity = new FollowTestDetailEntity();
//                newEntity.setServerName(serverNode.getServerName());
//                newEntity.setServerId(serverNode.getId());
//                newEntity.setPlatformType("MT4");
//                newEntity.setServerNode(serverNode.getServerNode() + ":" + serverNode.getServerPort());
//                newEntity.setVpsName(vpsEntity.getName());
//                newEntity.setVpsId(vpsEntity.getId());
//                newEntity.setSpeed(speed); // 设置速度，可能为 null
//                newEntity.setTestId(testId);
//                newEntity.setTestUpdateTime(measureTime);
////                newEntity.setServerUpdateTime(detailVO.getServerUpdateTime() != null ? detailVO.getServerUpdateTime() : null);
//                newEntity.setIsDefaultServer(1);
//                if (detailVO == null) {
//                    log.warn(" deailVO为空的是 : {}:{}", serverNode.getServerName(), serverNode.getServerNode() + ":" + serverNode.getServerPort());
//                    newEntity.setServerUpdateTime(null);
//                } else {
//                    newEntity.setServerUpdateTime(detailVO.getServerUpdateTime() != null ? detailVO.getServerUpdateTime() : null);
//                }
//                followTestDetailService.save(newEntity);
//            }
//        }
//
//        // 关闭线程池并等待所有任务完成
//        executorService.shutdown();
//        try {
//            if (!executorService.awaitTermination(1, TimeUnit.HOURS)) {  // 设置最大等待时间，避免无限期等待
//                executorService.shutdownNow();
//            }
//        } catch (InterruptedException e) {
//            executorService.shutdownNow();
//        }
//
//        return true; // 返回 true 表示所有任务提交成功
//    }


    @Override
    public void saveTestSpeed(FollowTestSpeedVO overallResult) {
        baseMapper.saveTestSpeed(overallResult);
    }

}
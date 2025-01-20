package net.maku.followcom.service.impl;

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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
    public boolean measure(List<String> servers, FollowVpsEntity vpsEntity, Integer testId, LocalDateTime measureTime) {
        // 获取服务器列表
        List<FollowBrokeServerEntity> serverList = followBrokeServerService.listByServerName(servers);

        // 按服务器名称分组
        Map<String, List<FollowBrokeServerEntity>> serverMap = serverList.stream()
                .collect(Collectors.groupingBy(FollowBrokeServerEntity::getServerName));

        // 创建一个固定大小的线程池
        ExecutorService executorService = Executors.newFixedThreadPool(10); // 可根据需求调整线程池大小

//        FollowTestServerQuery query = new FollowTestServerQuery();
//        List<FollowTestDetailVO> detailVOLists = followTestDetailService.selectServer(query);
//        List<FollowTestDetailVO> vo = (List<FollowTestDetailVO>) redisUtil.get(Constant.VPS_NODE_SPEED + "detail");
        // 提交每个测速任务到线程池
        for (Map.Entry<String, List<FollowBrokeServerEntity>> entry : serverMap.entrySet()) {
            List<FollowBrokeServerEntity> serverNodes = entry.getValue();

            for (FollowBrokeServerEntity serverNode : serverNodes) {
                String ipAddress = serverNode.getServerNode(); // 目标 IP 地址
                int port = Integer.parseInt(serverNode.getServerPort()); // 目标端口号

                //将服务器更新时间写进来
//                FollowTestServerQuery query = new FollowTestServerQuery();
//                query.setServerName(serverNode.getServerName());
//                query.setServerNode(serverNode.getServerNode() + ":" + serverNode.getServerPort());
//                List<FollowTestDetailVO> detailVOList = followTestDetailService.selectServer(query);
                List<FollowTestDetailVO> vo = (List<FollowTestDetailVO>) redisUtil.get(Constant.VPS_NODE_SPEED + "detail");
                List<FollowTestDetailVO> detailVOList = vo.stream()
                        .filter(detail -> detail.getServerName().equals(serverNode.getServerName()) && detail.getServerNode().equals(serverNode.getServerNode() + ":" + serverNode.getServerPort()))
                        .collect(Collectors.toList());
                //拿时间最新的一条数据
                FollowTestDetailVO detailVO = detailVOList.stream()
                            .max(Comparator.comparing(FollowTestDetailVO::getCreateTime))
                            .orElse(null);
                // 提交测速任务到线程池
//                executorService.submit(() -> {
                    int retryCount = 0; // 重试次数

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

                            // 保存测速结果
                            FollowTestDetailEntity newEntity = new FollowTestDetailEntity();
                            newEntity.setServerName(serverNode.getServerName());
                            newEntity.setServerId(serverNode.getId());
                            newEntity.setPlatformType("MT4");
                            newEntity.setServerNode(serverNode.getServerNode() + ":" + serverNode.getServerPort());
                            newEntity.setVpsName(vpsEntity.getName());
                            newEntity.setVpsId(vpsEntity.getId());
                            newEntity.setSpeed((int) duration);
                            newEntity.setTestId(testId);
                            newEntity.setUpdateTime(measureTime);
                            if (detailVO == null) {
                                log.warn(" deailVO为空的是 : {}:{}", serverNode.getServerName(), serverNode.getServerNode() + ":" + serverNode.getServerPort());
                                newEntity.setServerUpdateTime(null);
                                newEntity.setIsDefaultServer(1); // 或者根据业务需求设置其他默认值
                            } else {
                                newEntity.setServerUpdateTime(detailVO.getServerUpdateTime() != null ? detailVO.getServerUpdateTime() : null);
                                newEntity.setIsDefaultServer(detailVO.getIsDefaultServer() != null ? detailVO.getIsDefaultServer() : 1);
                            }
                            followTestDetailService.save(newEntity);
                            break; // 测试成功，跳出重试循环
                        } catch (Exception e) {
                            log.error("测速失败，目标地址: {}:{}, 错误信息: {}", ipAddress, port, e.getMessage());
                            break; // 出现异常时跳出重试循环
                        }
                    }
//                });
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
        }

        return true; // 返回 true 表示所有任务提交成功
    }

    @Override
    public boolean measureTask(List<String> servers, FollowVpsEntity vpsEntity, Integer testId, LocalDateTime measureTime) {
        // 获取服务器列表
        List<FollowBrokeServerEntity> serverList = followBrokeServerService.listByServerName(servers);
        // 按服务器名称分组
        Map<String, List<FollowBrokeServerEntity>> serverMap = serverList.stream()
                .collect(Collectors.groupingBy(FollowBrokeServerEntity::getServerName));
        // 创建一个固定大小的线程池
        ExecutorService executorService = Executors.newFixedThreadPool(10); // 可根据需求调整线程池大小
        // 提交每个测速任务到线程池
        for (Map.Entry<String, List<FollowBrokeServerEntity>> entry : serverMap.entrySet()) {
            List<FollowBrokeServerEntity> serverNodes = entry.getValue();

            for (FollowBrokeServerEntity serverNode : serverNodes) {
                String ipAddress = serverNode.getServerNode(); // 目标 IP 地址
                int port = Integer.parseInt(serverNode.getServerPort()); // 目标端口号
                List<FollowTestDetailVO> vo = (List<FollowTestDetailVO>) redisUtil.get(Constant.VPS_NODE_SPEED + "detail");
                List<FollowTestDetailVO> detailVOList = vo.stream()
                        .filter(detail -> detail.getServerName().equals(serverNode.getServerName()) && detail.getServerNode().equals(serverNode.getServerNode() + ":" + serverNode.getServerPort()))
                        .collect(Collectors.toList());
                // 拿时间最新的一条数据
                FollowTestDetailVO detailVO = detailVOList.stream()
                        .max(Comparator.comparing(FollowTestDetailVO::getCreateTime))
                        .orElse(null);

                // 提交测速任务到线程池
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

                // 保存测速结果，即使速度为 null
                FollowTestDetailEntity newEntity = new FollowTestDetailEntity();
                newEntity.setServerName(serverNode.getServerName());
                newEntity.setServerId(serverNode.getId());
                newEntity.setPlatformType("MT4");
                newEntity.setServerNode(serverNode.getServerNode() + ":" + serverNode.getServerPort());
                newEntity.setVpsName(vpsEntity.getName());
                newEntity.setVpsId(vpsEntity.getId());
                newEntity.setSpeed(speed); // 设置速度，可能为 null
                newEntity.setTestId(testId);
                newEntity.setUpdateTime(measureTime);
                newEntity.setServerUpdateTime(detailVO.getServerUpdateTime() != null ? detailVO.getServerUpdateTime() : null);
                newEntity.setIsDefaultServer(1);
                followTestDetailService.save(newEntity);
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
        }

        return true; // 返回 true 表示所有任务提交成功
    }


    @Override
    public void saveTestSpeed(FollowTestSpeedVO overallResult) {
        baseMapper.saveTestSpeed(overallResult);
    }

}
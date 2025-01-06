package net.maku.followcom.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.*;
import net.maku.followcom.service.*;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.util.RestUtil;
import net.maku.followcom.vo.FollowPlatformVO;
import net.maku.followcom.vo.FollowVpsVO;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.framework.security.user.SecurityUser;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousSocketChannel;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static net.maku.followcom.util.RestUtil.getHeader;

@Slf4j
@Service
@AllArgsConstructor
public class MasControlServiceImpl implements MasControlService {
    //    private final ClientService clientService;
    private final FollowVpsService followVpsService;
    //    private final PlatformService platformService;
//    private final ServerService serverService;
    private final FollowPlatformService followPlatformService;
    private final FollowBrokeServerService followBrokeServerService;


    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean insert(FollowVpsVO vo) {
        Boolean result = followVpsService.save(vo);
        if (!result) {
            return false;
        }
//        clientService.insert(vo);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(FollowVpsVO vo) {
//        Boolean result = clientService.update(vo);
//        if (!result) {
//            return false;
//        }
        followVpsService.update(vo);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(List<Integer> idList) {
//        clientService.delete(idList);
        followVpsService.delete(idList);

        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean deletePlatform(List<Long> idList) {
//        serverService.delete(idList);
//        platformService.delete(idList);

        followPlatformService.delete(idList);
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean updatePlatform(FollowPlatformVO vo, HttpServletRequest req) {
        FollowPlatformEntity currentPlatform = followPlatformService.getById(vo.getId());
        String currentBrokerName = currentPlatform.getBrokerName();
        String newBrokerName = vo.getBrokerName();

        // 检查券商名称是否重复
        if (!currentBrokerName.equals(newBrokerName)) {
            boolean brokerNameExists = followPlatformService.count(new LambdaQueryWrapper<FollowPlatformEntity>()
                    .eq(FollowPlatformEntity::getBrokerName, newBrokerName)) > 0;
            if (brokerNameExists) {
                throw new ServerException("券商名称重复，请重新输入");
            }
        }

        Long userId = SecurityUser.getUserId();

        // 获取当前已有的服务器列表
        List<String> existingServers = followPlatformService.list(new LambdaQueryWrapper<FollowPlatformEntity>()
                        .eq(FollowPlatformEntity::getBrokerName, currentBrokerName))
                .stream()
                .map(FollowPlatformEntity::getServer)
                .collect(Collectors.toList());

        // 找出需要删除的服务器
        List<String> serversToRemove = existingServers.stream()
                .filter(server -> !vo.getPlatformList().contains(server))
                .collect(Collectors.toList());

        // 找出需要新增的服务器
        List<String> serversToAdd = vo.getPlatformList().stream()
                .filter(server -> !existingServers.contains(server))
                .collect(Collectors.toList());

        // 批量删除需要移除的服务器
        if (!serversToRemove.isEmpty()) {
            followPlatformService.remove(new LambdaQueryWrapper<FollowPlatformEntity>()
                    .eq(FollowPlatformEntity::getBrokerName, currentBrokerName)
                    .in(FollowPlatformEntity::getServer, serversToRemove));
        }

        // 批量新增需要添加的服务器
        if (!serversToAdd.isEmpty()) {
            List<FollowPlatformEntity> newPlatforms = serversToAdd.stream().map(server -> {
                FollowPlatformEntity platform = new FollowPlatformEntity();
                platform.setBrokerName(newBrokerName);
                platform.setServer(server);
                platform.setPlatformType(vo.getPlatformType());
                platform.setCreator(userId);
                platform.setLogo(vo.getLogo());
                platform.setRemark(vo.getRemark());
                return platform;
            }).collect(Collectors.toList());
            followPlatformService.saveBatch(newPlatforms);
        }

        // 更新当前券商记录
        currentPlatform.setBrokerName(newBrokerName);
        currentPlatform.setPlatformType(vo.getPlatformType());
        currentPlatform.setLogo(vo.getLogo());
        currentPlatform.setRemark(vo.getRemark());
        followPlatformService.updateById(currentPlatform);

        // 异步测速逻辑
        asyncTestServerSpeed(vo.getPlatformList());

        // 异步更新缓存
        asyncUpdateCache(vo, req);

        return true;
    }

    // 异步更新缓存逻辑
    @Async
    public void asyncUpdateCache(FollowPlatformVO vo, HttpServletRequest req) {
        String authorization = req.getHeader("Authorization");
        followVpsService.list().forEach(vps -> {
            String url = MessageFormat.format("http://{0}:{1}{2}", vps.getIpAddress(), FollowConstant.VPS_PORT, FollowConstant.VPS_UPDATE_CACHE_FOLLOW_PLAT_CACHE);
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("id", vo.getId());
            HttpHeaders headers = getHeader(MediaType.APPLICATION_JSON_UTF8_VALUE);
            headers.add("Authorization", authorization);
            try {
                JSONObject response = RestUtil.request(url, HttpMethod.GET, headers, jsonObject, null, JSONObject.class).getBody();
                log.info("更新缓存成功：" + response.toString());
            } catch (Exception e) {
                log.error("更新缓存失败：" + e.getMessage());
            }
        });
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean insertPlatform(FollowPlatformVO vo) {
        Long userId = SecurityUser.getUserId();

        // 查询是否存在相同券商的数据
        List<FollowPlatformEntity> existingPlatforms = followPlatformService.list(
                new LambdaQueryWrapper<FollowPlatformEntity>()
                        .eq(FollowPlatformEntity::getBrokerName, vo.getBrokerName())
        );

        // 如果存在相同券商，批量更新信息
        if (!existingPlatforms.isEmpty()) {
            existingPlatforms.forEach(existingPlatform -> {
                existingPlatform.setPlatformType(vo.getPlatformType());
                existingPlatform.setRemark(vo.getRemark());
                existingPlatform.setLogo(vo.getLogo());
            });
            followPlatformService.updateBatchById(existingPlatforms); // 批量更新，减少数据库交互
        }

        // 插入或更新 platformList 数据
        List<String> platformList = vo.getPlatformList();
        List<FollowPlatformEntity> newPlatforms = new ArrayList<>();
        for (String bro : platformList) {
            List<FollowPlatformEntity> existingBroPlatform = followPlatformService.list(
                    new LambdaQueryWrapper<FollowPlatformEntity>().eq(FollowPlatformEntity::getServer, bro)
            );

            // 如果不存在，则新增
            if (existingBroPlatform.isEmpty()) {
                FollowPlatformEntity platform = new FollowPlatformEntity();
                platform.setBrokerName(vo.getBrokerName());
                platform.setServer(bro);
                platform.setPlatformType(vo.getPlatformType());
                platform.setCreator(userId);
                platform.setLogo(vo.getLogo());
                platform.setRemark(vo.getRemark());
                newPlatforms.add(platform);
            }
        }

        if (!newPlatforms.isEmpty()) {
            followPlatformService.saveBatch(newPlatforms); // 批量保存
        }

        // 测速逻辑移出事务，避免锁等待
        asyncTestServerSpeed(platformList);

        return true;
    }

    // 异步测速逻辑
    @Async
    public void asyncTestServerSpeed(List<String> platformList) {
        platformList.forEach(bro -> {
            List<FollowBrokeServerEntity> serverList = followBrokeServerService.list(
                    new LambdaQueryWrapper<FollowBrokeServerEntity>().eq(FollowBrokeServerEntity::getServerName, bro)
            );

            serverList.forEach(server -> {
                String ipAddress = server.getServerNode();
                int port = Integer.parseInt(server.getServerPort());
                try (AsynchronousSocketChannel socketChannel = AsynchronousSocketChannel.open()) {
                    long startTime = System.currentTimeMillis();
                    Future<Void> future = socketChannel.connect(new InetSocketAddress(ipAddress, port));
                    try {
                        future.get(5000, TimeUnit.MILLISECONDS); // 设置超时时间
                        long endTime = System.currentTimeMillis();
                        server.setSpeed((int) (endTime - startTime));
                        log.info("连接成功，延迟：" + (endTime - startTime) + "ms");
                    } catch (TimeoutException e) {
                        log.error("连接超时，服务器：" + ipAddress + ":" + port);
                    } catch (Exception e) {
                        log.error("测速异常：" + e.getMessage());
                    }
                    followBrokeServerService.updateById(server);
                } catch (IOException e) {
                    log.error("Socket连接异常：" + e.getMessage());
                }
            });

            // 更新最快的服务器节点
            List<FollowBrokeServerEntity> sortedServers = followBrokeServerService.list(
                    new LambdaQueryWrapper<FollowBrokeServerEntity>()
                            .eq(FollowBrokeServerEntity::getServerName, bro)
                            .isNotNull(FollowBrokeServerEntity::getSpeed)
                            .orderByAsc(FollowBrokeServerEntity::getSpeed)
            );

            if (!sortedServers.isEmpty()) {
                FollowBrokeServerEntity fastestServer = sortedServers.get(0);
                followPlatformService.update(
                        Wrappers.<FollowPlatformEntity>lambdaUpdate()
                                .eq(FollowPlatformEntity::getServer, fastestServer.getServerName())
                                .set(FollowPlatformEntity::getServerNode, fastestServer.getServerNode() + ":" + fastestServer.getServerPort())
                );
            }
        });
    }

}

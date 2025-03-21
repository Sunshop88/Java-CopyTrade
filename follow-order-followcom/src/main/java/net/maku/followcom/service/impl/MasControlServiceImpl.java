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
import net.maku.followcom.vo.PlatformVO;
import net.maku.followcom.vo.ServerVO;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.framework.security.user.SecurityUser;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousSocketChannel;
import java.text.MessageFormat;
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
    private final ClientService clientService;
    private final FollowVpsService followVpsService;
    private final PlatformService platformService;
    private final ServerService serverService;
    private final FollowPlatformService followPlatformService;
    private final FollowBrokeServerService followBrokeServerService;
    private final FollowTestDetailService followTestDetailService;


    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean insert(FollowVpsVO vo) {
        Boolean result = followVpsService.save(vo);
        if (!result) {
            log.error("插入 FollowVps 失败");
            return false;
        }
//        clientService.insert(vo);
        log.info("成功插入 FollowVps 和 Client");
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(FollowVpsVO vo) {
//        Boolean result = clientService.update(vo);
//        if (!result) {
//            log.error("更新 Client 失败");
//            return false;
//        }
        followVpsService.update(vo);
        log.info("成功更新 Client 和 FollowVps");
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(List<Integer> idList) {
        try {
//            clientService.delete(idList);
            followVpsService.delete(idList);
            //删除其vps下的所有测速记录
            List<FollowTestDetailEntity> testDetails = followTestDetailService.list(
                    new LambdaQueryWrapper<FollowTestDetailEntity>()
                            .in(FollowTestDetailEntity::getVpsId, idList)
            );

            if (ObjectUtil.isNotEmpty(testDetails)) {
                List<Long> testDetailIds = testDetails.stream()
                        .map(FollowTestDetailEntity::getId)
                        .collect(Collectors.toList());
                followTestDetailService.delete(testDetailIds);
            }
            log.info("成功删除 ID 列表: {}", idList);
            return true;
        } catch (Exception e) {
            log.error("删除 ID 列表时发生错误: {}", idList, e);
            throw new RuntimeException("删除操作失败", e);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean deletePlatform(List<Long> idList) {
        try {
            // 假设 serverService 和 platformService 已经恢复使用
//            serverService.delete(idList);
//            platformService.delete(idList);
            followPlatformService.delete(idList);
            log.info("成功删除平台 ID 列表: {}", idList);
            return true;
        } catch (Exception e) {
            log.error("删除平台时发生错误: {}", idList, e);
            throw new ServerException("删除平台操作失败", e);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean updatePlatform(FollowPlatformVO vo, HttpServletRequest req) {
        FollowPlatformEntity currentPlatform = followPlatformService.getById(vo.getId());
        String currentBrokerName = currentPlatform.getBrokerName();
        String newBrokerName = vo.getBrokerName();

        // 如果新的券商名称与当前记录的券商名称不同，则检查新的券商名称是否重复
        if (!currentBrokerName.equals(newBrokerName)) {
            List<FollowPlatformEntity> existingPlatforms = followPlatformService.list(new LambdaQueryWrapper<FollowPlatformEntity>()
                    .eq(FollowPlatformEntity::getBrokerName, newBrokerName));
            if (!existingPlatforms.isEmpty()) {
                throw new ServerException("券商名称重复，请重新输入");
            }
        }
        //platformList不能为空
        if (ObjectUtil.isEmpty(vo.getPlatformList())) {
            throw new ServerException("服务列表不能为空");
        }
        //根据vo的brokerName获取所有的券商名称，并且去重
        Long userId = SecurityUser.getUserId();

        // 获取当前数据库中已有的服务器列表
        List<String> existingServers = followPlatformService.list(new LambdaQueryWrapper<FollowPlatformEntity>()
                        .eq(FollowPlatformEntity::getBrokerName, vo.getBrokerName()))
                .stream()
                .map(FollowPlatformEntity::getServer)
                .collect(Collectors.toList());
        // 找出需要删除的服务器
        List<String> serversToRemove = existingServers.stream()
                .filter(server -> !vo.getPlatformList().contains(server))
                .collect(Collectors.toList());

        CountDownLatch latch = new CountDownLatch(vo.getPlatformList().size());
        //保存服务数据
        vo.getPlatformList().forEach(bro -> {
            ThreadPoolUtils.execute(() -> {
                try {
                    List<FollowPlatformEntity> followPlatformEntityList = followPlatformService.list(new LambdaQueryWrapper<FollowPlatformEntity>().eq(FollowPlatformEntity::getServer, bro));
                    if (ObjectUtil.isEmpty(followPlatformEntityList)) {
                        FollowPlatformVO followPlatformVO = new FollowPlatformVO();
                        followPlatformVO.setBrokerName(vo.getBrokerName());
                        followPlatformVO.setServer(bro);
                        followPlatformVO.setPlatformType(vo.getPlatformType());
                        followPlatformVO.setCreator(userId.toString());
                        followPlatformVO.setLogo(vo.getLogo());
                        followPlatformVO.setRemark(vo.getRemark());
                        followPlatformService.save(followPlatformVO);
                    }
                    // 进行测速
                    List<FollowBrokeServerEntity> list = followBrokeServerService.list(new LambdaQueryWrapper<FollowBrokeServerEntity>().eq(FollowBrokeServerEntity::getServerName, bro));

                    //外部接口
//                    List<PlatformEntity> platformEntityList = platformService.list(new LambdaQueryWrapper<PlatformEntity>().eq(PlatformEntity::getName, bro));
//                    if (ObjectUtil.isEmpty(platformEntityList)) {
//                        PlatformEntity platformEntity = new PlatformEntity();
//                        platformEntity.setName(bro);
//                        platformEntity.setType(vo.getPlatformType());
//                        platformService.save(platformEntity);
//
//                        Integer platformId = platformEntity.getId();
//                        list.forEach(o -> {
//                            ServerEntity serverEntity = new ServerEntity();
//                            serverEntity.setHost(o.getServerNode());
//                            serverEntity.setPort(Integer.valueOf(o.getServerPort()));
//                            serverEntity.setPlatformId(platformId);
//                            serverService.insert(serverEntity);
//                        });
//                    }

                    list.parallelStream().forEach(o -> {
                        String ipAddress = o.getServerNode(); // 目标IP地址
                        int port = Integer.valueOf(o.getServerPort()); // 目标端口号
                        try {
                            AsynchronousSocketChannel socketChannel = AsynchronousSocketChannel.open();
                            long startTime = System.currentTimeMillis(); // 记录起始时间
                            Future<Void> future = socketChannel.connect(new InetSocketAddress(ipAddress, port));
                            // 等待连接完成
                            long timeout = 5000; // 设置超时时间
                            try {
                                future.get(timeout, TimeUnit.MILLISECONDS);
                            } catch (TimeoutException e) {
                                log.error("连接超时，服务器：" + ipAddress + ":" + port);
                                return; // 连接超时，返回
                            }
                            long endTime = System.currentTimeMillis(); // 记录结束时间
                            o.setSpeed((int) (endTime - startTime));
                            System.out.println("连接成功，延迟：" + (endTime - startTime) + "ms");
                            followBrokeServerService.updateById(o);
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            latch.countDown();
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
//            });
//                });
//                vo.getPlatformList().forEach(bro -> {
//                UpdateWrapper<PlatformEntity> platformEntity = new UpdateWrapper<>();
//                platformEntity.eq("id", vo.getId());
                List<FollowBrokeServerEntity> list = followBrokeServerService.list(new LambdaQueryWrapper<FollowBrokeServerEntity>().eq(FollowBrokeServerEntity::getServerName, bro).orderByAsc(FollowBrokeServerEntity::getSpeed));
                if (!list.isEmpty()) {
                    FollowBrokeServerEntity followBrokeServer = list.get(0);
                    followPlatformService.update(Wrappers.<FollowPlatformEntity>lambdaUpdate().eq(FollowPlatformEntity::getServer, followBrokeServer.getServerName()).set(FollowPlatformEntity::getServerNode, followBrokeServer.getServerNode() + ":" + followBrokeServer.getServerPort()));
//                    platformService.update(Wrappers.<PlatformEntity>lambdaUpdate().eq(PlatformEntity::getName, followBrokeServer.getServerName()).set(PlatformEntity::getDefaultServer, followBrokeServer.getServerNode() + ":" + followBrokeServer.getServerPort()));
                }
//                    platformEntity.set("defaultServer", followBrokeServer.getServerNode() + ":" + followBrokeServer.getServerPort());
            });
        });
//                platformEntity.set("name", bro);
//                platformEntity.set("type", vo.getPlatformType());
//                platformService.update(platformEntity);

        try {
            // 等待所有线程完成
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        followPlatformService.update(vo);
        // 删除已移除的服务器
        if (!serversToRemove.isEmpty()) {
            followPlatformService.remove(new LambdaQueryWrapper<FollowPlatformEntity>()
                    .eq(FollowPlatformEntity::getBrokerName, vo.getBrokerName())
                    .in(FollowPlatformEntity::getServer, serversToRemove));

            //外部接口
            //根据serversToRemove查询platform的id
//            List<PlatformEntity> platformEntityList = platformService.list(new LambdaQueryWrapper<PlatformEntity>().in(PlatformEntity::getName, serversToRemove));
//            platformEntityList.forEach(o -> {
//                Integer platformId = o.getId();
//                //删除server
//                serverService.remove(new LambdaQueryWrapper<ServerEntity>().eq(ServerEntity::getPlatformId, platformId));
//            });
//            platformService.remove(new LambdaQueryWrapper<PlatformEntity>().in(PlatformEntity::getName, serversToRemove));
        }
        String authorization = req.getHeader("Authorization");
        ThreadPoolUtils.getExecutor().execute(() -> {
            //更新缓存
            for (FollowVpsEntity o : followVpsService.list()) {
                String url = MessageFormat.format("http://{0}:{1}{2}", o.getIpAddress(), FollowConstant.VPS_PORT, FollowConstant.VPS_UPDATE_CACHE_FOLLOW_PLAT_CACHE);
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("id", vo.getId());
                HttpHeaders header = getHeader(MediaType.APPLICATION_JSON_UTF8_VALUE);
                header.add("Authorization", authorization);
                JSONObject body = RestUtil.request(url, HttpMethod.GET, header, jsonObject, null, JSONObject.class).getBody();
                log.info("修改缓存" + body.toString());
            }
        });
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean insertPlatform(FollowPlatformVO vo) {
        //platformList不能为空
        if (ObjectUtil.isEmpty(vo.getPlatformList())) {
            throw new ServerException("服务列表不能为空");
        }
        Long userId = SecurityUser.getUserId();
        List<FollowPlatformEntity> existingPlatforms = followPlatformService.list(
                new LambdaQueryWrapper<FollowPlatformEntity>()
                        .eq(FollowPlatformEntity::getBrokerName, vo.getBrokerName())
        );
        if (!existingPlatforms.isEmpty()) {
            // 如果存在，则更新数据库中所有相同券商的信息
            for (FollowPlatformEntity existingPlatform : existingPlatforms) {
                existingPlatform.setPlatformType(vo.getPlatformType());
                existingPlatform.setRemark(vo.getRemark());
                existingPlatform.setLogo(vo.getLogo());
                followPlatformService.updateById(existingPlatform);
            }
        }

        CountDownLatch latch = new CountDownLatch(vo.getPlatformList().size());
        //保存服务数据
        vo.getPlatformList().forEach(bro -> {
            ThreadPoolUtils.execute(() -> {
                try {
                    List<FollowPlatformEntity> followPlatformEntityList = followPlatformService.list(new LambdaQueryWrapper<FollowPlatformEntity>().eq(FollowPlatformEntity::getServer, bro));
                    if (ObjectUtil.isEmpty(followPlatformEntityList)) {
                        FollowPlatformVO followPlatformVO = new FollowPlatformVO();
                        followPlatformVO.setBrokerName(vo.getBrokerName());
                        followPlatformVO.setServer(bro);
                        followPlatformVO.setPlatformType(vo.getPlatformType());
                        followPlatformVO.setCreator(userId.toString());
                        followPlatformVO.setLogo(vo.getLogo());
                        followPlatformVO.setRemark(vo.getRemark());
                        followPlatformService.save(followPlatformVO);
                    }
                    // 进行测速
                    List<FollowBrokeServerEntity> list = followBrokeServerService.list(new LambdaQueryWrapper<FollowBrokeServerEntity>().eq(FollowBrokeServerEntity::getServerName, bro));

                    //外部接口
//                    List<PlatformEntity> platformEntityList = platformService.list(new LambdaQueryWrapper<PlatformEntity>().eq(PlatformEntity::getName, bro));
//                    if (ObjectUtil.isEmpty(platformEntityList)) {
//                        PlatformEntity platformEntity = new PlatformEntity();
//                        platformEntity.setName(bro);
//                        platformEntity.setType(vo.getPlatformType());
//                        platformService.save(platformEntity);
//
//                        Integer platformId = platformEntity.getId();
//                        list.forEach(o -> {
//                            ServerEntity serverEntity = new ServerEntity();
//                            serverEntity.setHost(o.getServerNode());
//                            serverEntity.setPort(Integer.valueOf(o.getServerPort()));
//                            log.info("保存服务" + o.getServerNode() + ":" + o.getServerPort());
//                            serverEntity.setPlatformId(platformId);
//                            serverService.insert(serverEntity);
//                        });
//                    }

                    list.parallelStream().forEach(o -> {
                        String ipAddress = o.getServerNode(); // 目标IP地址
                        int port = Integer.valueOf(o.getServerPort()); // 目标端口号
                        try {
                            AsynchronousSocketChannel socketChannel = AsynchronousSocketChannel.open();
                            long startTime = System.currentTimeMillis(); // 记录起始时间
                            Future<Void> future = socketChannel.connect(new InetSocketAddress(ipAddress, port));
                            // 等待连接完成
                            long timeout = 5000; // 设置超时时间
                            try {
                                future.get(timeout, TimeUnit.MILLISECONDS);
                            } catch (TimeoutException e) {
                                log.error("连接超时，服务器：" + ipAddress + ":" + port);
                                return; // 连接超时，返回
                            }
                            long endTime = System.currentTimeMillis(); // 记录结束时间
                            o.setSpeed((int) (endTime - startTime));
                            System.out.println("连接成功，延迟：" + (endTime - startTime) + "ms");
                            followBrokeServerService.updateById(o);
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            latch.countDown();
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
//                vo.getPlatformList().forEach(bro -> {
//                UpdateWrapper<PlatformEntity> platformEntity = new UpdateWrapper<>();
//                platformEntity.eq("id", vo.getId());

                List<FollowBrokeServerEntity> list = followBrokeServerService.list(new LambdaQueryWrapper<FollowBrokeServerEntity>().eq(FollowBrokeServerEntity::getServerName, bro).orderByAsc(FollowBrokeServerEntity::getSpeed));
                if (!list.isEmpty()) {
                    FollowBrokeServerEntity followBrokeServer = list.get(0);
                    followPlatformService.update(Wrappers.<FollowPlatformEntity>lambdaUpdate().eq(FollowPlatformEntity::getServer, followBrokeServer.getServerName()).set(FollowPlatformEntity::getServerNode, followBrokeServer.getServerNode() + ":" + followBrokeServer.getServerPort()));
                    //外部接口
//                    platformService.update(Wrappers.<PlatformEntity>lambdaUpdate().eq(PlatformEntity::getName, bro).set(PlatformEntity::getDefaultServer, followBrokeServer.getServerNode() + ":" + followBrokeServer.getServerPort()));
                }
            });
        });
        try {
            // 等待所有线程完成
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return true;
    }
}

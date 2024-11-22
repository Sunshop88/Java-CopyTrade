package net.maku.followcom.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.AllArgsConstructor;
import net.maku.followcom.entity.FollowBrokeServerEntity;
import net.maku.followcom.entity.FollowPlatformEntity;
import net.maku.followcom.entity.PlatformEntity;
import net.maku.followcom.entity.ServerEntity;
import net.maku.followcom.service.*;
import net.maku.followcom.vo.FollowPlatformVO;
import net.maku.followcom.vo.FollowVpsVO;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.framework.security.user.SecurityUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.List;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

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
    public boolean updatePlatform(FollowPlatformVO vo) {
        followPlatformService.update(vo);

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

        // 删除已移除的服务器
        if (!serversToRemove.isEmpty()) {
            followPlatformService.remove(new LambdaQueryWrapper<FollowPlatformEntity>()
                    .eq(FollowPlatformEntity::getBrokerName, vo.getBrokerName())
                    .in(FollowPlatformEntity::getServer, serversToRemove));
        }

        Long userId = SecurityUser.getUserId();
        //保存服务数据
        ThreadPoolUtils.execute(() -> {
            vo.getPlatformList().forEach(bro -> {
                List<FollowPlatformEntity> followPlatformEntityList = followPlatformService.list(new LambdaQueryWrapper<FollowPlatformEntity>().eq(FollowPlatformEntity::getServer, bro));
                if (ObjectUtil.isEmpty(followPlatformEntityList)){
                    vo.setId(null);
                    FollowPlatformVO followPlatformVO = vo;
                    followPlatformVO.setServer(bro);
                    followPlatformVO.setCreator(userId.toString());
                    followPlatformService.save(followPlatformVO);
                    //进行测速
                    List<FollowBrokeServerEntity> list = followBrokeServerService.list(new LambdaQueryWrapper<FollowBrokeServerEntity>().eq(FollowBrokeServerEntity::getServerName, bro));
                    list.parallelStream().forEach(o -> {
                        String ipAddress = o.getServerNode(); // 目标IP地址
                        int port = Integer.valueOf(o.getServerPort()); // 目标端口号
                        try {
                            AsynchronousSocketChannel socketChannel = AsynchronousSocketChannel.open();
                            long startTime = System.currentTimeMillis(); // 记录起始时间
                            Future<Void> future = socketChannel.connect(new InetSocketAddress(ipAddress, port));
                            // 等待连接完成
                            future.get();
                            long endTime = System.currentTimeMillis(); // 记录结束时间
                            o.setSpeed((int) endTime - (int) startTime);
                            System.out.println("连接成功，延迟：" + (endTime - startTime) + "ms");
                            followBrokeServerService.updateById(o);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
//                UpdateWrapper<PlatformEntity> platformEntity = new UpdateWrapper<>();
//                platformEntity.eq("id", vo.getId());
                    list.stream().map(FollowBrokeServerEntity::getServerName).distinct().forEach(o -> {
                        //找出最小延迟
                        FollowBrokeServerEntity followBrokeServer = followBrokeServerService.list(new LambdaQueryWrapper<FollowBrokeServerEntity>().eq(FollowBrokeServerEntity::getServerName, o).orderByAsc(FollowBrokeServerEntity::getSpeed)).get(0);
                        //修改所有用户连接节点
                        followPlatformService.update(Wrappers.<FollowPlatformEntity>lambdaUpdate().eq(FollowPlatformEntity::getServer, followBrokeServer.getServerName()).set(FollowPlatformEntity::getServerNode, followBrokeServer.getServerNode() + ":" + followBrokeServer.getServerPort()));

//                    platformEntity.set("defaultServer", followBrokeServer.getServerNode() + ":" + followBrokeServer.getServerPort());
                    });

//                platformEntity.set("name", bro);
//                platformEntity.set("type", vo.getPlatformType());
//                platformService.update(platformEntity);
                }
            });
        });
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean insertPlatform(FollowPlatformVO vo) {
        Long userId = SecurityUser.getUserId();
        //判断是否已存在服务名称
        vo.getPlatformList().forEach(bro -> {
            FollowPlatformVO followPlatformVO = vo;
            followPlatformVO.setServer(bro);
            followPlatformVO.setCreator(userId.toString());
            followPlatformService.save(followPlatformVO);
            //进行测速
            List<FollowBrokeServerEntity> list = followBrokeServerService.list(new LambdaQueryWrapper<FollowBrokeServerEntity>().eq(FollowBrokeServerEntity::getServerName, bro));
            list.parallelStream().forEach(o -> {
                String ipAddress = o.getServerNode(); // 目标IP地址
                int port = Integer.valueOf(o.getServerPort()); // 目标端口号
                try {
                    AsynchronousSocketChannel socketChannel = AsynchronousSocketChannel.open();
                    long startTime = System.currentTimeMillis(); // 记录起始时间
                    Future<Void> future = socketChannel.connect(new InetSocketAddress(ipAddress, port));
                    // 等待连接完成
                    future.get();
                    long endTime = System.currentTimeMillis(); // 记录结束时间
                    o.setSpeed((int) endTime - (int) startTime);
                    followBrokeServerService.updateById(o);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
//            PlatformEntity platformEntity = new PlatformEntity();
            list.stream().map(FollowBrokeServerEntity::getServerName).distinct().forEach(o -> {
                //找出最小延迟
                FollowBrokeServerEntity followBrokeServer = followBrokeServerService.list(new LambdaQueryWrapper<FollowBrokeServerEntity>().eq(FollowBrokeServerEntity::getServerName, o).orderByAsc(FollowBrokeServerEntity::getSpeed)).get(0);
                //修改所有用户连接节点
                followPlatformService.update(Wrappers.<FollowPlatformEntity>lambdaUpdate().eq(FollowPlatformEntity::getServer, followBrokeServer.getServerName()).set(FollowPlatformEntity::getServerNode, followBrokeServer.getServerNode() + ":" + followBrokeServer.getServerPort()));

//                platformEntity.setDefaultServer(followBrokeServer.getServerNode() + ":" + followBrokeServer.getServerPort());
            });


//            platformEntity.setName(bro);
//            platformEntity.setType(vo.getPlatformType());
//            platformService.insert(platformEntity);
//            platformService.save(platformEntity);

            //查询新增的platformService的id
//            for (FollowBrokeServerEntity followBrokeServerEntity : list) {
//                ServerEntity serverEntity = new ServerEntity();
//                serverEntity.setPlatformId(platformEntity.getId());
//                serverEntity.setPort(Integer.valueOf(followBrokeServerEntity.getServerPort()));
//                serverEntity.setHost(followBrokeServerEntity.getServerNode());
//                serverService.insert(serverEntity);
//            }
        });
        return true;
    }
}

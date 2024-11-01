package net.maku.mascontrol.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import net.maku.followcom.entity.FollowBrokeServerEntity;
import net.maku.followcom.entity.FollowVpsEntity;
import net.maku.followcom.enums.TraderCloseEnum;
import net.maku.followcom.enums.VpsSpendEnum;
import net.maku.followcom.service.FollowBrokeServerService;
import net.maku.followcom.service.FollowPlatformService;
import net.maku.followcom.service.FollowVpsService;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import net.maku.framework.security.user.SecurityUser;
import net.maku.mascontrol.convert.FollowTestSpeedConvert;
import net.maku.mascontrol.entity.FollowTestDetailEntity;
import net.maku.mascontrol.entity.FollowTestSpeedEntity;
import net.maku.mascontrol.query.FollowTestSpeedQuery;
import net.maku.mascontrol.service.FollowTestDetailService;
import net.maku.mascontrol.vo.FollowTestSpeedVO;
import net.maku.mascontrol.dao.FollowTestSpeedDao;
import net.maku.mascontrol.service.FollowTestSpeedService;
import com.fhs.trans.service.impl.TransService;
import net.maku.framework.common.utils.ExcelUtils;
import net.maku.mascontrol.vo.FollowTestSpeedExcelVO;
import org.springframework.beans.factory.annotation.Autowired;
import cn.hutool.core.util.ObjectUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousSocketChannel;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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
    private final TransService transService;
    @Autowired
    private FollowTestDetailService followTestDetailService;
    @Autowired
    private FollowPlatformService followPlatformService;
    @Autowired
    private FollowBrokeServerService followBrokeServerService;
    @Autowired
    private FollowVpsService followVpsService;

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
    public void remeasure(Long id, List<String> servers, List<String> vps) {
        FollowTestSpeedEntity result = baseMapper.selectById(id);
        if (ObjectUtil.isEmpty(result)) {
            throw new ServerException("测速结果不存在");
        }

        result.setStatus(VpsSpendEnum.IN_PROGRESS.getType());
        result.setDoTime(new Date());
        updateById(result);

        extracted(servers, vps, result);
    }


    @Override
    public void measure(List<String> servers, List<String> vps) {
        FollowTestSpeedVO overallResult = new FollowTestSpeedVO();
        overallResult.setStatus(VpsSpendEnum.IN_PROGRESS.getType());
        overallResult.setDoTime(new Date());
        overallResult.setVersion(0);
        overallResult.setDeleted(0);
        overallResult.setCreator(SecurityUser.getUserId());
        overallResult.setCreateTime(LocalDateTime.now());
        overallResult.setTestName(SecurityUser.getUser().getUsername());
//            save(overallResult);
        baseMapper.saveTestSpeed(overallResult);

        // 保存并获取生成的 ID
//        baseMapper.saveTestSpeed(overallResult);
        FollowTestSpeedEntity result = FollowTestSpeedConvert.INSTANCE.convert(overallResult);

        extracted(servers, vps, result);
    }

    public void extracted(List<String> servers, List<String> vps, FollowTestSpeedEntity result) {
        List<FollowBrokeServerEntity> serverList = followBrokeServerService.listByServerName(servers);
        List<FollowVpsEntity> vpsList = followVpsService.listByVpsName(vps);

        Map<String, List<FollowBrokeServerEntity>> serverMap = serverList.stream()
                .collect(Collectors.groupingBy(FollowBrokeServerEntity::getServerName));

        AtomicBoolean allSuccess = new AtomicBoolean(true); // 用于记录是否所有连接都成功
        List<FollowTestDetailEntity> savedEntities = new ArrayList<>();

        serverMap.forEach((serverName, serverNodes) -> {
            serverNodes.parallelStream().forEach(o -> {
                String ipAddress = o.getServerNode(); // 目标 IP 地址
                int port = Integer.parseInt(o.getServerPort()); // 目标端口号

                vpsList.parallelStream().forEach(vpsName -> {
                    try {
                        AsynchronousSocketChannel socketChannel = AsynchronousSocketChannel.open();
                        long startTime = System.currentTimeMillis(); // 记录起始时间
                        Future<Void> future = socketChannel.connect(new InetSocketAddress(ipAddress, port));
                        // 等待连接完成
                        long timeout = 5000; // 设置超时时间为5秒
                        try {
                            future.get(timeout, TimeUnit.MILLISECONDS);
                        } catch (TimeoutException e) {
                            // 处理超时情况
                            e.printStackTrace();
                            allSuccess.set(false);
                        }
                        long endTime = System.currentTimeMillis(); // 记录结束时间
                        long duration = endTime - startTime;

                        FollowTestDetailEntity newEntity = new FollowTestDetailEntity();
                        newEntity.setServerName(o.getServerName());
                        newEntity.setServerId(o.getId());
//                        newEntity.setPlatformType(followPlatformService.listByServerName(o.getServerName()));
                        newEntity.setPlatformType("MT4");
                        newEntity.setServerNode(o.getServerNode() + ":" + o.getServerPort());
                        newEntity.setVpsName(vpsName.getName());
                        newEntity.setVpsId(vpsName.getId());
                        newEntity.setSpeed((int) duration);
                        newEntity.setTestId(result.getId());
                        savedEntities.add(newEntity);
                    } catch (Exception e) {
                        e.printStackTrace();
                        allSuccess.set(false);// 如果有任何连接失败，设置 allSuccess 为 false
                    }
                });
            });
        });

        // 更新状态
        if (allSuccess.get()) {
            result.setStatus(VpsSpendEnum.SUCCESS.getType());
            followTestDetailService.saveBatch(savedEntities);
            //TODO 测速完成后选节点
            /**
             List<FollowTestDetailEntity> allEntities = followTestDetailService.list(
             new LambdaQueryWrapper<FollowTestDetailEntity>()
             .eq(FollowTestDetailEntity::getTestId, result.getId())
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

             //修改所有用户连接节点
             followPlatformService.update(Wrappers.<FollowPlatformEntity>lambdaUpdate().
             eq(FollowPlatformEntity::getServer,minLatencyEntity.getServerName()).
             eq(FollowPlatformEntity::getVpsName,minLatencyEntity.getVpsName()).
             set(FollowPlatformEntity::getServerNode,minLatencyEntity.getServerNode()));

             }
             });
             });
             */
        } else {
            result.setStatus(VpsSpendEnum.FAILURE.getType());
        }

        updateById(result);
    }

}
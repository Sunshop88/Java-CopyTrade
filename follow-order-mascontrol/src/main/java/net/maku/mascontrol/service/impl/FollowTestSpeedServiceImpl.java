package net.maku.mascontrol.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import net.maku.followcom.entity.FollowBrokeServerEntity;
import net.maku.followcom.entity.FollowPlatformEntity;
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
    public boolean measure(List<String> servers, FollowVpsEntity vpsEntity, Integer testId) {
        List<FollowBrokeServerEntity> serverList = followBrokeServerService.listByServerName(servers);

        Map<String, List<FollowBrokeServerEntity>> serverMap = serverList.stream()
                .collect(Collectors.groupingBy(FollowBrokeServerEntity::getServerName));

        boolean result = true;
        for (Map.Entry<String, List<FollowBrokeServerEntity>> entry : serverMap.entrySet()) {
            String serverName = entry.getKey();
            List<FollowBrokeServerEntity> serverNodes = entry.getValue();

            for (FollowBrokeServerEntity serverNode : serverNodes) {
                String ipAddress = serverNode.getServerNode(); // 目标 IP 地址
                int port = Integer.parseInt(serverNode.getServerPort()); // 目标端口号

                try {
                    AsynchronousSocketChannel socketChannel = AsynchronousSocketChannel.open();
                    long startTime = System.currentTimeMillis(); // 记录起始时间
                    Future<Void> future = socketChannel.connect(new InetSocketAddress(ipAddress, port));
                    // 等待连接完成
                    long timeout = 15000; // 设置超时时间为15秒
                    try {
                        future.get(timeout, TimeUnit.MILLISECONDS);
                    } catch (TimeoutException e) {
                        // 处理超时情况
                        e.printStackTrace();
                        result = false;
                        break; // 如果出现异常,直接跳出当前循环
                    }
                    long endTime = System.currentTimeMillis(); // 记录结束时间
                    long duration = endTime - startTime;

                    FollowTestDetailEntity newEntity = new FollowTestDetailEntity();
                    newEntity.setServerName(serverNode.getServerName());
                    newEntity.setServerId(serverNode.getId());
                    newEntity.setPlatformType("MT4");
                    newEntity.setServerNode(serverNode.getServerNode() + ":" + serverNode.getServerPort());
                    newEntity.setVpsName(vpsEntity.getName());
                    newEntity.setVpsId(vpsEntity.getId());
                    newEntity.setSpeed((int) duration);
                    newEntity.setTestId(testId);
                    followTestDetailService.save(newEntity);
                } catch (Exception e) {
                    e.printStackTrace();
                    result = false;
                    break; // 如果出现异常,直接跳出当前循环
                }
            }
        }
        return result;
}

    @Override
    public void updateTestSpend(Long id) {
        baseMapper.updateTestSpend(id);
    }

    @Override
    public void saveTestSpeed(FollowTestSpeedVO overallResult) {
        baseMapper.saveTestSpeed(overallResult);
    }

}
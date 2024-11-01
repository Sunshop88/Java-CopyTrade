package net.maku.mascontrol.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import net.maku.followcom.entity.FollowVarietyEntity;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import net.maku.mascontrol.convert.FollowTestDetailConvert;
import net.maku.mascontrol.entity.FollowTestDetailEntity;
import net.maku.mascontrol.query.FollowTestDetailQuery;
import net.maku.mascontrol.service.FollowTestSpeedService;
import net.maku.mascontrol.vo.FollowTestDetailVO;
import net.maku.mascontrol.dao.FollowTestDetailDao;
import net.maku.mascontrol.service.FollowTestDetailService;
import com.fhs.trans.service.impl.TransService;
import net.maku.framework.common.utils.ExcelUtils;
import net.maku.mascontrol.vo.FollowTestDetailExcelVO;
import net.maku.framework.common.excel.ExcelFinishCallBack;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.multipart.MultipartFile;
import cn.hutool.core.util.ObjectUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * 测速详情
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Service
@AllArgsConstructor
public class FollowTestDetailServiceImpl extends BaseServiceImpl<FollowTestDetailDao, FollowTestDetailEntity> implements FollowTestDetailService {
    private final TransService transService;



    public List<List<Object>> page(FollowTestDetailQuery query) {
        // 获取分页数据
        IPage<FollowTestDetailEntity> page = baseMapper.selectPage(getPage(query), getWrapper(query));
        List<FollowTestDetailVO> detailVOList = FollowTestDetailConvert.INSTANCE.convertList(page.getRecords());

        // 用于最终结果的列表
        List<List<Object>> result = new ArrayList<>();

        // 创建表头
        Set<String> uniqueVpsNames = new LinkedHashSet<>();
        for (FollowTestDetailVO detail : detailVOList) {
            uniqueVpsNames.add(detail.getVpsName());
        }

        // 添加表头
        List<Object> header = new ArrayList<>();
        header.add("服务器名称");
        header.add("平台类型");
        header.add("服务器节点");
        header.addAll(uniqueVpsNames);
        result.add(header);

        Map<String, Map<String, Double>> speedMap = new HashMap<>();
        for (FollowTestDetailVO detail : detailVOList) {
            String key = detail.getServerName() + "_" + detail.getPlatformType() + "_" + detail.getServerNode();
            String vpsName = detail.getVpsName();
            double speed = detail.getSpeed();

            // 初始化Map并添加速度数据
            speedMap.computeIfAbsent(key, k -> new HashMap<>()).put(vpsName, speed);
        }


        Map<String, List<Object>> uniqueEntries = new LinkedHashMap<>();
        for (FollowTestDetailVO detail : detailVOList) {
            String key = detail.getServerName() + "_" + detail.getPlatformType() + "_" + detail.getServerNode();
            if (!uniqueEntries.containsKey(key)) {
                List<Object> entry = new ArrayList<>();
                entry.add(detail.getServerName());
                entry.add(detail.getPlatformType());
                entry.add(detail.getServerNode());


                Map<String, Double> vpsSpeeds = speedMap.getOrDefault(key, Collections.emptyMap());
                for (String vpsName : uniqueVpsNames) {
                    entry.add(vpsSpeeds.getOrDefault(vpsName, 0.0));
                }

                uniqueEntries.put(key, entry);
            }
        }
        result.addAll(uniqueEntries.values());

        return result;
    }


    private LambdaQueryWrapper<FollowTestDetailEntity> getWrapper(FollowTestDetailQuery query) {
        LambdaQueryWrapper<FollowTestDetailEntity> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(FollowTestDetailEntity::getTestId, query.getTestId());
        return wrapper;
    }


    @Override
    public FollowTestDetailVO get(Long id) {
        FollowTestDetailEntity entity = baseMapper.selectById(id);
        FollowTestDetailVO vo = FollowTestDetailConvert.INSTANCE.convert(entity);

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(FollowTestDetailVO vo) {
        FollowTestDetailEntity entity = FollowTestDetailConvert.INSTANCE.convert(vo);

        baseMapper.insert(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FollowTestDetailVO vo) {
        FollowTestDetailEntity entity = FollowTestDetailConvert.INSTANCE.convert(vo);

        updateById(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> idList) {
        removeByIds(idList);

    }


    @Override
    public void export() {
        List<FollowTestDetailExcelVO> excelList = FollowTestDetailConvert.INSTANCE.convertExcelList(list());
        transService.transBatch(excelList);
        ExcelUtils.excelExport(FollowTestDetailExcelVO.class, "测速详情", null, excelList);
    }


    @Override
    public List<FollowTestDetailVO> listServerAndVps() {
        // 查询服务器和vps清单
        LambdaQueryWrapper<FollowTestDetailEntity> wrapper = Wrappers.lambdaQuery();
        wrapper.select(FollowTestDetailEntity::getServerName, FollowTestDetailEntity::getVpsName).orderByDesc(FollowTestDetailEntity::getServerName);
        List<FollowTestDetailEntity> list = baseMapper.selectList(wrapper);
        return FollowTestDetailConvert.INSTANCE.convertList(list);
    }


    @Override
    public void updates(FollowTestDetailVO convert) {
        //设置测试速度
        convert.setSpeed(convert.getSpeed());
        update(convert);
    }



}
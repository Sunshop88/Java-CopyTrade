package net.maku.followcom.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import net.maku.followcom.entity.FollowPlatformEntity;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.query.DashboardAccountQuery;
import net.maku.followcom.query.SymbolAnalysisQuery;
import net.maku.followcom.vo.*;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import net.maku.followcom.convert.FollowTraderAnalysisConvert;
import net.maku.followcom.entity.FollowTraderAnalysisEntity;
import net.maku.followcom.query.FollowTraderAnalysisQuery;
import net.maku.followcom.dao.FollowTraderAnalysisDao;
import net.maku.followcom.service.FollowTraderAnalysisService;
import com.fhs.trans.service.impl.TransService;
import net.maku.framework.common.utils.ExcelUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 账号数据分析表
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Service
@AllArgsConstructor
public class FollowTraderAnalysisServiceImpl extends BaseServiceImpl<FollowTraderAnalysisDao, FollowTraderAnalysisEntity> implements FollowTraderAnalysisService {
    private final TransService transService;

    @Override
    public PageResult<FollowTraderAnalysisVO> page(FollowTraderAnalysisQuery query) {
        IPage<FollowTraderAnalysisEntity> page = baseMapper.selectPage(getPage(query), getWrapper(query));

        return new PageResult<>(FollowTraderAnalysisConvert.INSTANCE.convertList(page.getRecords()), page.getTotal());
    }


    private LambdaQueryWrapper<FollowTraderAnalysisEntity> getWrapper(FollowTraderAnalysisQuery query){
        LambdaQueryWrapper<FollowTraderAnalysisEntity> wrapper = Wrappers.lambdaQuery();

        return wrapper;
    }


    @Override
    public FollowTraderAnalysisVO get(Long id) {
        FollowTraderAnalysisEntity entity = baseMapper.selectById(id);
        FollowTraderAnalysisVO vo = FollowTraderAnalysisConvert.INSTANCE.convert(entity);

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(FollowTraderAnalysisVO vo) {
        FollowTraderAnalysisEntity entity = FollowTraderAnalysisConvert.INSTANCE.convert(vo);

        baseMapper.insert(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FollowTraderAnalysisVO vo) {
        FollowTraderAnalysisEntity entity = FollowTraderAnalysisConvert.INSTANCE.convert(vo);

        updateById(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> idList) {
        removeByIds(idList);

    }

    @Override
    public List<SymbolAnalysisVO> getSymbolAnalysis(SymbolAnalysisQuery vo) {
        return baseMapper.getSymbolAnalysis(vo);
    }

    @Override
    public StatDataVO getStatData() {
        return baseMapper.getStatData();
    }

    @Override
    public List<SymbolChartVO> getSymbolChart() {
        return baseMapper.getSymbolChart();
    }

    @Override
    public List<DashboardAccountDataVO> getAccountDataPage( DashboardAccountQuery vo) {
        return baseMapper.getAccountDataPage(vo);
    }

    @Override
    public List<FollowPlatformEntity> searchPlatform(String brokerName) {
        return baseMapper.searchPlatform(brokerName);
    }
}
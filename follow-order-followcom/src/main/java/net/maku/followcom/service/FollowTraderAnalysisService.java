package net.maku.followcom.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.query.DashboardAccountQuery;
import net.maku.followcom.query.SymbolAnalysisQuery;
import net.maku.followcom.vo.*;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.BaseService;
import net.maku.followcom.query.FollowTraderAnalysisQuery;
import net.maku.followcom.entity.FollowTraderAnalysisEntity;
import java.util.List;

/**
 * 账号数据分析表
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
public interface FollowTraderAnalysisService extends BaseService<FollowTraderAnalysisEntity> {

    PageResult<FollowTraderAnalysisVO> page(FollowTraderAnalysisQuery query);

    FollowTraderAnalysisVO get(Long id);


    void save(FollowTraderAnalysisVO vo);

    void update(FollowTraderAnalysisVO vo);

    void delete(List<Long> idList);


    List<SymbolAnalysisVO> getSymbolAnalysis(SymbolAnalysisQuery vo);

    StatDataVO getStatData();

    List<SymbolChartVO> getSymbolChart();

    List<DashboardAccountDataVO> getAccountDataPage(DashboardAccountQuery vo);
}
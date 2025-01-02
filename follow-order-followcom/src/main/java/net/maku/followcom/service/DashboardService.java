package net.maku.followcom.service;

import net.maku.followcom.entity.FollowTraderAnalysisEntity;
import net.maku.followcom.query.DashboardAccountQuery;
import net.maku.followcom.query.FollowTraderAnalysisQuery;
import net.maku.followcom.query.SymbolAnalysisQuery;
import net.maku.followcom.vo.*;
import net.maku.framework.common.query.Query;
import net.maku.framework.common.utils.PageResult;

import java.util.List;

/**
 * Author:  zsd
 * Date:  2025/1/2/周四 10:29
 * 仪表盘
 */
public interface DashboardService {
    //仪表盘-账号数据
    PageResult<DashboardAccountDataVO> getAccountDataPage(DashboardAccountQuery vo);

    List<SymbolAnalysisVO> getSymbolAnalysis(SymbolAnalysisQuery vo);

    List<FollowTraderAnalysisEntity> getSymbolAnalysisDetails(TraderAnalysisVO vo);

    StatDataVO getStatData();

    List<RankVO> getRanking(Query query);

    List<SymbolChartVO> getSymbolChart();
}

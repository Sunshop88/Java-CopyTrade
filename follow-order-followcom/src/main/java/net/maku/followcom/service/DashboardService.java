package net.maku.followcom.service;

import net.maku.followcom.entity.FollowPlatformEntity;
import net.maku.followcom.entity.FollowTraderAnalysisEntity;
import net.maku.followcom.query.DashboardAccountQuery;
import net.maku.followcom.query.FollowTraderAnalysisQuery;
import net.maku.followcom.query.SymbolAnalysisQuery;
import net.maku.followcom.vo.*;
import net.maku.framework.common.query.Query;
import net.maku.framework.common.utils.PageResult;

import java.util.List;
import java.util.Map;

/**
 * Author:  zsd
 * Date:  2025/1/2/周四 10:29
 * 仪表盘
 */
public interface DashboardService {
    //仪表盘-账号数据
    List<DashboardAccountDataVO> getAccountDataPage(DashboardAccountQuery vo);

    List<SymbolChartVO> getSymbolAnalysis();

    List<FollowTraderAnalysisEntity> getSymbolAnalysisDetails(TraderAnalysisVO vo);
//TraderAnalysisVO vo
     Map<String,List<FollowTraderAnalysisEntity>> getSymbolAnalysisMapDetails();

    StatDataVO getStatData();

    List<RankVO> getRanking(Query query);

    List<SymbolChartVO> getSymbolChart();

    List<FollowPlatformEntity> searchPlatform(String platform);
}

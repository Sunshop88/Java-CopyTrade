package net.maku.followcom.service;

import net.maku.followcom.query.SymbolAnalysisQuery;
import net.maku.followcom.vo.StatDataVO;
import net.maku.followcom.vo.SymbolAnalysisVO;
import net.maku.followcom.vo.SymbolChartVO;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.BaseService;
import net.maku.followcom.vo.FollowTraderAnalysisVO;
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
}
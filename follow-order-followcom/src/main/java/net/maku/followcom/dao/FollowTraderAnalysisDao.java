package net.maku.followcom.dao;

import net.maku.followcom.query.SymbolAnalysisQuery;
import net.maku.followcom.vo.StatDataVO;
import net.maku.followcom.vo.SymbolAnalysisVO;
import net.maku.followcom.vo.SymbolChartVO;
import net.maku.framework.mybatis.dao.BaseDao;
import net.maku.followcom.entity.FollowTraderAnalysisEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 账号数据分析表
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Mapper
public interface FollowTraderAnalysisDao extends BaseDao<FollowTraderAnalysisEntity> {

    @Select({
            " <script>",
            "SELECT symbol,sum(position) as position,sum(lots) as lots,sum(num) as num,sum(profit) as profit, ",
            "sum(buy_num) as buy_num,sum(buy_lots) as buy_lots,sum(buy_profit) as buy_profit,sum(sell_num) as sell_num,sum(sell_lots) as sell_lots,sum(sell_profit) as sell_profit ",
            "FROM (SELECT account,symbol,MAX(position) as position,MAX(lots) as lots,MAX(num) as num,MAX(profit) as profit, ",
            "MAX(buy_num) as buy_num,MAX(buy_lots) as buy_lots,MAX(buy_profit) as buy_profit,MAX(sell_num) as sell_num,MAX(sell_lots) as sell_lots,MAX(sell_profit) as sell_profit FROM follow_trader_analysis ",
            "<where>",
            "<if test='query!=null and query.account != null and   query.account.trim() != \"\" '>",
            " AND account LIKE CONCAT('%',#{query.account},'%')",
            "</if>",
            "<if test='query!=null and query.vpsId != null  '>",
            " AND vps_id =#{query.vpsId} ",
            "</if>",
            "<if test='query!=null and query.platform != null  and   query.account.trim() != \"\"  '>",
            " AND platform =#{query.platform} ",
            "</if>",
            "<if test='query!=null and query.sourceAccount != null  and   query.sourceAccount.trim() != \"\"  '>",
            " AND source_account =#{query.sourceAccount} ",
            "</if>",
            "<if test='query!=null and query.sourcePlatform != null  and   query.sourcePlatform.trim() != \"\"  '>",
            " AND source_platform =#{query.platform} ",
            "</if>",
            "<if test='query!=null and query.type != null '>",
            " AND `type` =#{query.type} ",
            "</if>",
            "</where>",
            " GROUP BY account,symbol)  tmp GROUP BY symbol",
            "<if test='query!=null and query.order != null and   query.order.trim() != \"\"   '>",
            "ORDER BY ${query.order}   ${query.asc?'asc':'desc'}  ",
            "</if>",
            "</script>",
    })
    List<SymbolAnalysisVO> getSymbolAnalysis(@Param("query") SymbolAnalysisQuery query);

    @Select({
            " <script>",
            "SELECT sum(position) as position,sum(lots) as lots,sum(num) as num,sum(profit) as profit, ",
            "(SELECT COUNT(1) FROM follow_vps WHERE deleted=0) as vpsNum, ",
            "(SELECT COUNT(1) FROM follow_trader WHERE type=0) as sourceNum, ",
            "(SELECT COUNT(1) FROM follow_trader WHERE type=1) as followNum ",
            " FROM  follow_trader_analysis ",
            "</script>",
    })
    StatDataVO getStatData();
    @Select({
            " <script>",
            "SELECT symbol, sum(buy_num) as buy_num,sum(buy_lots) as buy_lots,sum(buy_profit) as buy_profit,sum(sell_num) as sell_num,sum(sell_lots) as sell_lots,sum(sell_profit) as sell_profit  ",
            "FROM (SELECT account,symbol, MAX(buy_num) as buy_num,MAX(buy_lots) as buy_lots,MAX(buy_profit) as buy_profit,MAX(sell_num) as sell_num,MAX(sell_lots) as sell_lots,MAX(sell_profit) as sell_profit FROM follow_trader_analysis ",
            "GROUP BY account,symbol)  tmp GROUP BY symbol",
            "</script>",
    })
    List<SymbolChartVO> getSymbolChart();
}
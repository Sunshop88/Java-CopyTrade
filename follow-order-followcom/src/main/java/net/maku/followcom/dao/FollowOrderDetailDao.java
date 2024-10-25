package net.maku.followcom.dao;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import net.maku.followcom.query.FollowOrderSpliListQuery;
import net.maku.followcom.vo.FollowOrderSlipPointVO;
import net.maku.framework.mybatis.dao.BaseDao;
import net.maku.followcom.entity.FollowOrderDetailEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 订单详情
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Mapper
public interface FollowOrderDetailDao extends BaseDao<FollowOrderDetailEntity> {
    @Select("<script>" +
            "SELECT trader_id as traderId, " +
            "account,"+
            "broker_name as brokeName ,"+
            "server as platform,"+
            "symbol,"+
            "ROUND(AVG(open_time_difference),0) AS meanOpenTimeDifference, " +
            "ROUND(AVG(close_time_difference),0) AS meanCloseTimeDifference, " +
            "ROUND(AVG(open_price - request_open_price),7) AS meanOpenPriceDifference, " +
            "ROUND(AVG(close_price - request_close_price),7) AS meanClosePriceDifference, " +
            "ROUND(AVG(open_price_slip),2) AS meanOpenPriceSlip, " +
            "ROUND(AVG(close_price_slip),2) AS meanClosePriceSlip ," +
            "count(1) AS symbolNum " +
            "FROM follow_order_detail " +
            "<where>" +
            " AND order_no is not null" +
            "<if test='query.traderIdList != null and query.traderIdList.size > 0'> AND trader_id in \n" +
            "  <foreach collection='query.traderIdList' item='item' open='(' separator=',' close=')'>\n" +
            "    #{item}\n" +
            "  </foreach>\n" +
            "</if>" +
            "<if test='query.account != null and query.account != \"\"'> AND account = #{query.account} </if>" +
            "<if test='query.platform != null and query.platform != \"\"'> AND server = #{query.platform} </if>" +
            "<if test='query.symbolList != null and query.symbolList.size > 0'> AND symbol in \n" +
            "  <foreach collection='query.symbolList' item='item' open='(' separator=',' close=')'>\n" +
            "    #{item}\n" +
            "  </foreach>\n" +
            "</if>" +
            "<if test='query.startTime != null and query.startTime != \"\"'> AND open_time &gt;= #{query.startTime} </if>" +
            "<if test='query.endTime != null and query.endTime != \"\"'> AND open_time &lt;= #{query.endTime} </if>" +
            "</where>" +
            "GROUP BY trader_id,symbol" +
            "</script>")
    Page<FollowOrderSlipPointVO> getFollowOrderDetailStats(Page<?> page, @Param("query") FollowOrderSpliListQuery query);
}
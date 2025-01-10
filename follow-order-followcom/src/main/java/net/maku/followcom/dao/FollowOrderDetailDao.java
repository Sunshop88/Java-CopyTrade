package net.maku.followcom.dao;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import net.maku.followcom.query.FollowOrderSpliListQuery;
import net.maku.followcom.vo.FollowOrderSlipPointVO;
import net.maku.framework.mybatis.dao.BaseDao;
import net.maku.followcom.entity.FollowOrderDetailEntity;
import org.apache.ibatis.annotations.Insert;
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
            "ROUND(AVG(rate_margin),2) AS rateMargin ," +
            "GROUP_CONCAT(DISTINCT magical) AS magical ," +
            "GROUP_CONCAT(DISTINCT source_user) AS sourceUser ," +
            "GROUP_CONCAT(DISTINCT ip_addr) AS ipAddr ," +
            "count(1) AS symbolNum " +
            "FROM follow_order_detail " +
            "<where>" +
            " AND order_no is not null" +
            "<if test='query.serverId != null '> and trader_id IN ( SELECT id FROM follow_trader WHERE  server_id=#{query.serverId}) </if> \n" +
            "<if test='query.traderIdList != null and query.traderIdList.size > 0'> AND trader_id in \n" +
            "  <foreach collection='query.traderIdList' item='item' open='(' separator=',' close=')'>\n" +
            "    #{item}\n" +
            "  </foreach>\n" +
            "</if>" +
            "<if test='query.account != null and query.account != \"\"'> AND account = #{query.account} </if>" +
            "<if test='query.server != null and query.server != \"\"'> AND ip_addr = #{query.server} </if>" +
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
            " ORDER BY trader_id,create_time DESC "+
            "</script>")
    Page<FollowOrderSlipPointVO> getFollowOrderDetailStats(Page<?> page, @Param("query") FollowOrderSpliListQuery query);

    @Insert({
            " <script>",
            "insert into follow_order_detail (",
            "symbol,",
            "type,",
            "order_no,",
            "send_no,",
            "trader_id,",
            "account,",
            "request_open_time,",
            "request_open_price,",
            "open_time,",
            "response_open_time,",
            "open_price,",
            "open_price_slip,",
            "open_time_difference,",
            "request_close_time,",
            "request_close_price,",
            "close_time,",
            "response_close_time,",
            "close_price,",
            "close_price_slip,",
            "close_time_difference,",
            "size,",
            "tp,",
            "sl,",
            "commission,",
            "swap,",
            "profit,",
            "remark,",
            "version,",
            "deleted,",
            "creator,",
            "create_time,",
            "updater,",
            "update_time,",
            "placed_type,",
            "broker_name,",
            "server,",
            "ip_addr,",
            "server_name,",
            "close_id,",
            "close_status,",
            "magical,",
            "source_user,",
            "rate_margin,",
            "server_host,",
            "close_ip_addr,",
            "close_server_name,",
            "close_server_host,",
            "is_external",
            ") values " ,
            " <foreach collection='list' item='OrderDetail'  separator=','>",
            " (",
            "#{OrderDetail.symbol},",
            "#{OrderDetail.type},",
            "#{OrderDetail.orderNo},",
            "#{OrderDetail.sendNo},",
            "#{OrderDetail.traderId},",
            "#{OrderDetail.account},",
            "#{OrderDetail.requestOpenTime},",
            "#{OrderDetail.requestOpenPrice},",
            "#{OrderDetail.openTime},",
            "#{OrderDetail.responseOpenTime},",
            "#{OrderDetail.openPrice},",
            "#{OrderDetail.openPriceSlip},",
            "#{OrderDetail.openTimeDifference},",
            "#{OrderDetail.requestCloseTime},",
            "#{OrderDetail.requestClosePrice},",
            "#{OrderDetail.closeTime},",
            "#{OrderDetail.responseCloseTime},",
            "#{OrderDetail.closePrice},",
            "#{OrderDetail.closePriceSlip},",
            "#{OrderDetail.closeTimeDifference},",
            "#{OrderDetail.size},",
            "#{OrderDetail.tp},",
            "#{OrderDetail.sl},",
            "#{OrderDetail.commission},",
            "#{OrderDetail.swap},",
            "#{OrderDetail.profit},",
            "#{OrderDetail.remark},",
            "#{OrderDetail.version},",
            "#{OrderDetail.deleted},",
            "#{OrderDetail.creator},",
            "#{OrderDetail.createTime},",
            "#{OrderDetail.updater},",
            "#{OrderDetail.updateTime},",
            "#{OrderDetail.placedType},",
            "#{OrderDetail.brokeName},",
            "#{OrderDetail.platform},",
            "#{OrderDetail.ipAddr},",
            "#{OrderDetail.serverName},",
            "#{OrderDetail.closeId},",
            "#{OrderDetail.closeStatus},",
            "#{OrderDetail.magical},",
            "#{OrderDetail.sourceUser},",
            "#{OrderDetail.rateMargin},",
            "#{OrderDetail.serverHost},",
            "#{OrderDetail.closeIpAddr},",
            "#{OrderDetail.closeServerName},",
            "#{OrderDetail.closeServerHost},",
            "#{OrderDetail.isExternal}",
            ")",
            "</foreach>",
            "ON DUPLICATE KEY UPDATE update_time = now() , version=version+1,close_time=values(close_time),close_price=values(close_price),tp=values(tp),",
            "sl=values(sl),swap=values(swap),profit=values(profit)",
            "</script>",
    })
    void customBatchSaveOrUpdate(@Param("list") List<FollowOrderDetailEntity> list);
}
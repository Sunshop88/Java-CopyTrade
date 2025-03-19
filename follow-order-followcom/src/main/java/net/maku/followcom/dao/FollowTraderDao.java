package net.maku.followcom.dao;

import com.baomidou.mybatisplus.core.metadata.IPage;
import net.maku.followcom.query.DashboardAccountQuery;
import net.maku.followcom.vo.DashboardAccountDataVO;
import net.maku.followcom.vo.FollowTraderCountVO;
import net.maku.followcom.vo.FollowTraderVO;
import net.maku.framework.mybatis.dao.BaseDao;
import net.maku.followcom.entity.FollowTraderEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * mt4账号
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Mapper
public interface FollowTraderDao extends BaseDao<FollowTraderEntity> {

    @Select("SELECT b.id FROM " +
            "(SELECT id,server_id,account FROM follow_trader WHERE server_id=#{newId}) a " +
            "join " +
            "(SELECT id,server_id,account FROM follow_trader WHERE server_id=#{oldId}) b " +
            "ON a.account=b.account")
    List<Long> getShare(@Param("oldId") Integer oldId,@Param("newId") Integer newId);
    //mybatis-plus实现
    @Select({
            " <script>",
            "SELECT p.broker_name as brokerName, t.platform as server,t.server_name as vpsName,t.account as account,t.type as type,t.id as traderId,master_account  as  sourceAccount FROM follow_trader  t LEFT JOIN follow_platform p on t.platform_id=p.id ",
            "LEFT JOIN follow_trader_subscribe s ON s.slave_id=t.id ",
            " <where>",
            "<if test='query!=null and query.brokerName != null and   query.brokerName.trim() != \"\" '>",
            " AND  FIND_IN_SET(p.broker_name,#{query.brokerName}) ",
            "</if>",
            "<if test='query!=null and query.server != null and   query.server.trim() != \"\" '>",
            " AND  FIND_IN_SET(p.server,#{query.server}) ",
            "</if>",
            "<if test='query!=null and query.vpsName != null and   query.vpsName.trim() != \"\" '>",
            " AND t.server_name LIKE CONCAT('%',#{query.vpsName},'%')",
            "</if>",
            "<if test='query!=null and query.account != null and   query.account.trim() != \"\" '>",
            " AND t.account LIKE CONCAT('%',#{query.account},'%')",
            "</if>",
            "<if test='query!=null and query.sourceAccount != null and   query.sourceAccount.trim() != \"\" '>",
            " AND s.master_account LIKE CONCAT('%',#{query.sourceAccount},'%')",
            "</if>",
            "</where>",
            "ORDER BY t.create_time desc",
            "</script>",
    })
    IPage<DashboardAccountDataVO> getAccountDataPage(@Param("page") IPage<FollowTraderEntity> page, @Param("query") DashboardAccountQuery query);

    List<FollowTraderCountVO> getServerAccounts();

    List<FollowTraderCountVO> getDefaultAccountCounts();

    List<FollowTraderCountVO> getServerNodeCounts();
}
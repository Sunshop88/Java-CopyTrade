package net.maku.followcom.dao;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import net.maku.followcom.entity.FollowTraderLogEntity;
import net.maku.followcom.query.FollowTraderLogQuery;
import net.maku.framework.mybatis.dao.BaseDao;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 交易日志
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Mapper
public interface FollowTraderLogDao extends BaseDao<FollowTraderLogEntity> {

    //mybatis-plus实现
    @Select({
            " <script>",
            "SELECT * FROM follow_trader_log l ",
            " <where>",
            "<if test='query!=null and query.realName != null and   query.realName.trim() != \"\" '>",
            "  and ( creator in (",
            "    SELECT id FROM sys_user WHERE real_name LIKE CONCAT('%',#{query.realName},'%')",
            "   )",
            "<if test='\"System\".contains(query.realName)' >",
            "  or creator is null ",
            " </if>",
            " ) </if>",
            "<if test='query!=null and query.vpsName != null and   query.vpsName.trim() != \"\" '>",
            " and  vps_name LIKE CONCAT('%',#{query.vpsName},'%')",
            " </if>",
            "<if test='query!=null and query.logDetail != null and   query.logDetail.trim() != \"\" '>",
            " and  log_detail LIKE CONCAT('%',#{query.logDetail},'%')",
            " </if>",
            "<if test='query!=null and query.type != null  '>",
            " and  type =#{query.type} ",
            " </if>",
            "</where>",
            "</script>",
    })
    Page<FollowTraderLogEntity> selectFollowTraderLogByPage(@Param("page") Page<FollowTraderLogEntity> page, @Param("query") FollowTraderLogQuery query);
}
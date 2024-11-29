package net.maku.followcom.dao;

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
}
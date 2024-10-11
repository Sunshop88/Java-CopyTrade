package net.maku.mascontrol.dao;

import net.maku.framework.mybatis.dao.BaseDao;
import net.maku.mascontrol.entity.FollowVarietyEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 品种匹配
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Mapper
public interface FollowVarietyDao extends BaseDao<FollowVarietyEntity> {

    //返回所有品种的列表
    @Select("select std_symbol from follow_variety ")
    List<String> queryVarieties();

    //根据品种名称来查询券商名称和券商对应的品种名称
    @Select("select broker_name,broker_symbol from follow_variety where std_symbol=#{symbol} ")
    List<FollowVarietyEntity> getlist();
}
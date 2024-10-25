package net.maku.followcom.dao;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import net.maku.followcom.vo.FollowVarietyVO;
import net.maku.framework.mybatis.dao.BaseDao;
import net.maku.followcom.entity.FollowVarietyEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
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
    List<FollowVarietyEntity> getlist(@Param("symbol") String symbol);

    IPage<FollowVarietyEntity> pageSymbolList(Page<FollowVarietyEntity> page, String stdSymbol);

    int updateCustom(FollowVarietyEntity updateEntity);

    void updateStdContractByStdSymbol(String stdSymbol, Integer stdContract);

//    void saveAll(List<FollowVarietyExcelVO> brokerDataList);

//    @Insert("INSERT INTO follow_variety_excel (std_symbol, broker_name, broker_symbol) VALUES (#{stdSymbol}, #{brokerName}, #{brokerSymbol})")
//    void save(FollowVarietyExcelVO vo);
}
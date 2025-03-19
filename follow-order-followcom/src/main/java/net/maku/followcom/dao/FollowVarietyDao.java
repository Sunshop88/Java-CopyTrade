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

    IPage<FollowVarietyEntity> pageSymbolList(Page<FollowVarietyEntity> page, String stdSymbol);

    void updateStdContractByStdSymbol(String stdSymbol, Integer stdContract);

}
package net.maku.followcom.convert;

import net.maku.followcom.entity.FollowTraderAnalysisEntity;
import net.maku.followcom.vo.FollowTraderAnalysisEntityVO;
import net.maku.followcom.vo.FollowTraderAnalysisVO;
import net.maku.followcom.vo.RankVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 账号数据分析表
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Mapper
public interface FollowTraderAnalysisConvert {
    FollowTraderAnalysisConvert INSTANCE = Mappers.getMapper(FollowTraderAnalysisConvert.class);

    FollowTraderAnalysisEntity convert(FollowTraderAnalysisVO vo);

    FollowTraderAnalysisVO convert(FollowTraderAnalysisEntity entity);

    List<FollowTraderAnalysisVO> convertList(List<FollowTraderAnalysisEntity> list);

    List<FollowTraderAnalysisEntity> convertList2(List<FollowTraderAnalysisVO> list);


    List<RankVO> convertRank(List<FollowTraderAnalysisEntity> list);

    FollowTraderAnalysisEntityVO convertVo(FollowTraderAnalysisEntity entity);
}
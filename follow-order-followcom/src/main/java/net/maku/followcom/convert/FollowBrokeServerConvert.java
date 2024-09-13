package net.maku.followcom.convert;

import net.maku.followcom.entity.FollowBrokeServerEntity;
import net.maku.followcom.vo.FollowBrokeServerVO;
import net.maku.followcom.vo.FollowBrokeServerExcelVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 导入服务器列表
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Mapper
public interface FollowBrokeServerConvert {
    FollowBrokeServerConvert INSTANCE = Mappers.getMapper(FollowBrokeServerConvert.class);

    FollowBrokeServerEntity convert(FollowBrokeServerVO vo);

    FollowBrokeServerVO convert(FollowBrokeServerEntity entity);

    List<FollowBrokeServerVO> convertList(List<FollowBrokeServerEntity> list);

    List<FollowBrokeServerEntity> convertList2(List<FollowBrokeServerVO> list);

    List<FollowBrokeServerExcelVO> convertExcelList(List<FollowBrokeServerEntity> list);

    List<FollowBrokeServerEntity> convertExcelList2(List<FollowBrokeServerExcelVO> list);
}
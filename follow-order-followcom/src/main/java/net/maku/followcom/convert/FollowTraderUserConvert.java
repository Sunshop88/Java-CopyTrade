package net.maku.followcom.convert;

import net.maku.followcom.entity.FollowTraderUserEntity;
import net.maku.followcom.vo.FollowTraderUserExcelVO;
import net.maku.followcom.vo.FollowTraderUserVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 账号初始表
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Mapper
public interface FollowTraderUserConvert {
    FollowTraderUserConvert INSTANCE = Mappers.getMapper(FollowTraderUserConvert.class);

    FollowTraderUserEntity convert(FollowTraderUserVO vo);

    FollowTraderUserVO convert(FollowTraderUserEntity entity);

    List<FollowTraderUserVO> convertList(List<FollowTraderUserEntity> list);

    List<FollowTraderUserEntity> convertList2(List<FollowTraderUserVO> list);

    List<FollowTraderUserExcelVO> convertExcelList(List<FollowTraderUserEntity> list);

    List<FollowTraderUserEntity> convertExcelList2(List<FollowTraderUserExcelVO> list);
}
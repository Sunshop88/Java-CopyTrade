package net.maku.followcom.convert;

import net.maku.followcom.entity.FollowUploadTraderUserEntity;
import net.maku.followcom.vo.FollowUploadTraderUserVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 上传账号记录表
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Mapper
public interface FollowUploadTraderUserConvert {
    FollowUploadTraderUserConvert INSTANCE = Mappers.getMapper(FollowUploadTraderUserConvert.class);

    FollowUploadTraderUserEntity convert(FollowUploadTraderUserVO vo);

    FollowUploadTraderUserVO convert(FollowUploadTraderUserEntity entity);

    List<FollowUploadTraderUserVO> convertList(List<FollowUploadTraderUserEntity> list);

    List<FollowUploadTraderUserEntity> convertList2(List<FollowUploadTraderUserVO> list);

}
package net.maku.followcom.service;

import net.maku.followcom.entity.FollowUploadTraderUserEntity;
import net.maku.followcom.query.FollowUploadTraderUserQuery;
import net.maku.followcom.vo.FollowUploadTraderUserVO;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.BaseService;
import java.util.List;

/**
 * 上传账号记录表
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
public interface FollowUploadTraderUserService extends BaseService<FollowUploadTraderUserEntity> {

    PageResult<FollowUploadTraderUserVO> page(FollowUploadTraderUserQuery query);

    FollowUploadTraderUserVO get(Long id);

    void save(FollowUploadTraderUserVO vo);

    void update(FollowUploadTraderUserVO vo);

    void delete(List<Long> idList);


}
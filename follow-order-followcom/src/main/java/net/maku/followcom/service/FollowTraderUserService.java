package net.maku.followcom.service;

import net.maku.followcom.entity.FollowTraderUserEntity;
import net.maku.followcom.query.FollowTraderUserQuery;
import net.maku.followcom.vo.FollowTraderUserVO;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.BaseService;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

/**
 * 账号初始表
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
public interface FollowTraderUserService extends BaseService<FollowTraderUserEntity> {

    PageResult<FollowTraderUserVO> page(FollowTraderUserQuery query);

    FollowTraderUserVO get(Long id);


    void save(FollowTraderUserVO vo);

    void update(FollowTraderUserVO vo);

    void delete(List<Long> idList);

    void importByExcel(MultipartFile file);

    void export();
}
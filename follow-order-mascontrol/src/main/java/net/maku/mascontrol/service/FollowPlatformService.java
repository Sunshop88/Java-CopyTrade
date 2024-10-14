package net.maku.mascontrol.service;

import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.BaseService;
import net.maku.mascontrol.entity.FollowPlatformEntity;
import net.maku.mascontrol.query.FollowPlatformQuery;
import net.maku.mascontrol.vo.FollowPlatformVO;
import online.mtapi.mt4.QuoteClient;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 平台管理
 *
 * @author 阿沐 babamu@126.com
 * @since 1.0.0 2024-09-11
 */
public interface FollowPlatformService extends BaseService<FollowPlatformEntity> {

    PageResult<FollowPlatformVO> page(FollowPlatformQuery query);

    void save(FollowPlatformVO vo);

    void update(FollowPlatformVO vo);

    void delete(List<Long> idList);

    void export();

//    List<String> getBrokeName(List<Long> idList);

    List<FollowPlatformVO> getList();


    QuoteClient tologin(String account, String password, String platform);

    List<FollowPlatformVO> listBroke();
}
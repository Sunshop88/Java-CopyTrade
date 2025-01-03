package net.maku.followcom.service;

import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.BaseService;
import net.maku.followcom.vo.FollowBrokeServerVO;
import net.maku.followcom.query.FollowBrokeServerQuery;
import net.maku.followcom.entity.FollowBrokeServerEntity;

import java.util.List;

/**
 * 导入服务器列表
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
public interface FollowBrokeServerService extends BaseService<FollowBrokeServerEntity> {

    PageResult<FollowBrokeServerVO> page(FollowBrokeServerQuery query);

    FollowBrokeServerVO get(Long id);


    void save(FollowBrokeServerVO vo);

    void update(FollowBrokeServerVO vo);

    void delete(List<Long> idList);


    void export();

    void saveList(List<FollowBrokeServerVO> list);

    List<FollowBrokeServerEntity> listByServerName(String name);

    List<FollowBrokeServerEntity> listByServerName(List<String> name);

    List<FollowBrokeServerEntity> listByServerNameGroup(String name);

    FollowBrokeServerEntity getByName(String server);

    FollowBrokeServerEntity existsByServerNodeAndServerPort(String serverName, String s, String s1);
}
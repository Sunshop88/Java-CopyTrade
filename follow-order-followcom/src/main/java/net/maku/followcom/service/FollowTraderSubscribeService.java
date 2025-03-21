package net.maku.followcom.service;

import net.maku.followcom.vo.FollowAddSalveVo;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.BaseService;
import net.maku.followcom.vo.FollowTraderSubscribeVO;
import net.maku.followcom.query.FollowTraderSubscribeQuery;
import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import java.util.List;
import java.util.Map;

/**
 * 订阅关系表
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
public interface FollowTraderSubscribeService extends BaseService<FollowTraderSubscribeEntity> {

    PageResult<FollowTraderSubscribeVO> page(FollowTraderSubscribeQuery query);

    FollowTraderSubscribeVO get(Long id);


    void save(FollowTraderSubscribeVO vo);

    void update(FollowTraderSubscribeVO vo);

    void delete(List<Long> idList);


    void export();

    List<String> initSubscriptions(Long id);

    FollowTraderSubscribeEntity subscription(Long slaveId, Long masterId);

    void addSubscription(FollowAddSalveVo vo);

    Map<String,Object> getStatus(String account, String account1);

    List<FollowTraderSubscribeEntity> getSubscribeOrder(Long id);

    void updateSubCache(Long id);

    FollowTraderSubscribeEntity getFollowSub(Long id);
}
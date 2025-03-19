package net.maku.followcom.service;

import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.vo.FollowTraderCountVO;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.BaseService;
import net.maku.followcom.entity.FollowPlatformEntity;
import net.maku.followcom.query.FollowPlatformQuery;
import net.maku.followcom.vo.FollowPlatformVO;
import online.mtapi.mt4.QuoteClient;

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

    List<FollowPlatformVO> getList();

    QuoteClient tologin(FollowTraderEntity trader);

    List<FollowPlatformVO> listBroke();

    String listByServerName(String serverName);

    List<FollowPlatformVO> listHavingServer(String name);

    List<FollowPlatformVO> listByServer();
    
    FollowPlatformEntity getPlatFormById(String id);

    FollowPlatformEntity updatePlatCache(String id);

    String getbrokerName(String serverName);

    List<FollowTraderCountVO> getBrokerNames();
}
package net.maku.followcom.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import net.maku.followcom.dto.MasToSubOrderSendDto;
import net.maku.followcom.entity.FollowOrderSendEntity;
import net.maku.followcom.entity.FollowPlatformEntity;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.query.DashboardAccountQuery;
import net.maku.followcom.query.FollowOrderSendQuery;
import net.maku.followcom.query.FollowOrderSpliListQuery;
import net.maku.followcom.query.FollowTraderQuery;
import net.maku.followcom.vo.*;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.BaseService;
import online.mtapi.mt4.QuoteClient;

import java.util.List;
import java.util.Map;

/**
 * mt4账号
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
public interface FollowTraderService extends BaseService<FollowTraderEntity> {

    PageResult<FollowTraderVO> page(FollowTraderQuery query);

    FollowTraderVO get(Long id);


    FollowTraderVO save(FollowTraderVO vo);

    void update(FollowTraderVO vo);

    void delete(List<Long> idList);


    void export();


    /**
     * 下单
     *
     * @param vo
     * @return
     */
    boolean orderSend(FollowOrderSendVO vo, QuoteClient quoteClient, FollowTraderVO followTraderVO, Integer contract,Integer flag);

    /**
     * 滑点分析列表
     */
    PageResult<FollowOrderSlipPointVO> pageSlipPoint(FollowOrderSpliListQuery query);

    /**
     * 订单详情
     *
     * @param query
     * @return
     */
    PageResult<FollowOrderDetailVO> orderSlipDetail(FollowOrderSendQuery query);

    /**
     * 平仓
     *
     * @param vo
     * @return
     */
    boolean orderClose(FollowOrderSendCloseVO vo, QuoteClient quoteClient);

    FollowOrderSendEntity orderDoing(Long traderId);

    void saveQuo(QuoteClient quoteClient, FollowTraderEntity vo);

    TraderOverviewVO traderOverview(String ip);

    Boolean stopOrder(Integer type, String traderId);

     /**
      * 筛选出共同的账号
      * */
    List<Long> getShare(Integer oldId, Integer newId);

    FollowTraderEntity getFollowById(Long masterId);

    String getAccountCount(String serverName);

    String getDefaultAccountCount(String serverName,String defaultServerNode);

    List<FollowTraderEntity> listByServerName(String name);

    IPage<DashboardAccountDataVO> getAccountDataPage(IPage<FollowTraderEntity> page, DashboardAccountQuery vo);

    List<FollowTraderCountVO> getAccountCounts();

    List<FollowTraderCountVO> getDefaultAccountCounts();

    List<FollowTraderCountVO> getServerNodeCounts();

    void getFollowRelation(FollowTraderEntity followTraderEntity, String account, String platform);

    void removeRelation(FollowTraderEntity o, String account, Integer platformId);

    List<FollowSendAccountListVO> accountPage();

    FollowMasOrderVo masOrdersend(MasToSubOrderSendDto vo, QuoteClient quoteClient, FollowTraderVO convert, Integer contract);
}
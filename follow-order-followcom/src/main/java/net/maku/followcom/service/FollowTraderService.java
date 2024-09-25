package net.maku.followcom.service;

import net.maku.followcom.entity.FollowOrderSendEntity;
import net.maku.followcom.query.FollowOrderSendQuery;
import net.maku.followcom.query.FollowOrderSpliListQuery;
import net.maku.followcom.vo.*;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.BaseService;
import net.maku.followcom.query.FollowTraderQuery;
import net.maku.followcom.entity.FollowTraderEntity;
import online.mtapi.mt4.QuoteClient;

import java.util.List;

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
     * @param vo
     * @return
     */
    boolean orderSend(FollowOrderSendVO vo,QuoteClient quoteClient,FollowTraderVO followTraderVO);

    /**
     * 滑点分析列表
     */
    PageResult<FollowOrderSlipPointVO>  pageSlipPoint(FollowOrderSpliListQuery query);

    /**
     * 订单详情
     * @param query
     * @return
     */
    PageResult<FollowOrderDetailVO> orderSlipDetail(FollowOrderSendQuery query);

    /**
     * 平仓
     * @param vo
     * @return
     */
    boolean orderClose(FollowOrderCloseVO vo,QuoteClient quoteClient);

    QuoteClient tologin(String account, String password, String platform);

    FollowOrderSendEntity orderDoing(Long traderId);

    void saveQuo(QuoteClient quoteClient, FollowTraderEntity vo);
}
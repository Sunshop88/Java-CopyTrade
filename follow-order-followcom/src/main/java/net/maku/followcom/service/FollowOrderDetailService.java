package net.maku.followcom.service;

import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.query.FollowOrderSpliListQuery;
import net.maku.followcom.vo.FollowOrderSlipPointVO;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.BaseService;
import net.maku.followcom.vo.FollowOrderDetailVO;
import net.maku.followcom.query.FollowOrderDetailQuery;
import net.maku.followcom.entity.FollowOrderDetailEntity;
import online.mtapi.mt4.QuoteClient;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单详情
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
public interface FollowOrderDetailService extends BaseService<FollowOrderDetailEntity> {

    PageResult<FollowOrderDetailVO> page(FollowOrderDetailQuery query);

    FollowOrderDetailVO get(Long id);


    void save(FollowOrderDetailVO vo);

    void update(FollowOrderDetailVO vo);

    void delete(List<Long> idList);


    void export(List<FollowOrderDetailVO> followOrderDetailVOList);

    PageResult<FollowOrderSlipPointVO> listFollowOrderSlipPoint(FollowOrderSpliListQuery query);

    void saveOrderHistory(QuoteClient quoteClient, FollowTraderEntity u, LocalDateTime localDateTime);

    void saveOrderActive(QuoteClient quoteClient, FollowTraderEntity u);
}
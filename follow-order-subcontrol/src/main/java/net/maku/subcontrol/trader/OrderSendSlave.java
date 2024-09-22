package net.maku.subcontrol.trader;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowSubscribeOrderEntity;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.enums.CopyTradeFlag;
import net.maku.followcom.service.FollowTraderService;
import net.maku.subcontrol.pojo.CachedCopierOrderInfo;
import net.maku.subcontrol.pojo.EaOrderInfo;
import net.maku.subcontrol.rule.AbstractFollowRule;
import net.maku.subcontrol.rule.FollowRule;
import net.maku.subcontrol.service.IOperationStrategy;
import online.mtapi.mt4.Exception.TimeoutException;
import online.mtapi.mt4.Op;
import online.mtapi.mt4.Order;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * MT4 跟单者处理开仓信号策略
 *
 * @author samson bruce
 * @since 2023/04/14
 */
@Slf4j
public class OrderSendSlave extends AbstractOperation implements IOperationStrategy {
    FollowTraderEntity copier;
    FollowRule followRule;
    CopierApiTrader copierApiTrader;

    FollowTraderService aotfxTraderService;

    public OrderSendSlave(CopierApiTrader copierApiTrader) {
        super(copierApiTrader.getTrader());
        this.copierApiTrader = copierApiTrader;
        this.copier = this.copierApiTrader.getTrader();
        this.followRule = new FollowRule();
    }

    /**
     * 收到开仓信号处理操作
     *
     * @param consumerRecord ConsumerRecord
     * @param retry          处理次数
     */
    @Override
    public void operate(ConsumerRecord<String, Object> consumerRecord, int retry) {
        EaOrderInfo orderInfo = (EaOrderInfo) consumerRecord.value();
        orderInfo.setSlaveReceiveOpenTime(LocalDateTime.now());
        FollowTraderSubscribeEntity leaderCopier = leaderCopierService.subscription(copier.getId(), Long.valueOf(orderInfo.getMasterId()));
        //  判断跟单者是否允许开仓:
        //  1-跟随状态类 2-风控类
        orderInfo.addSymbolPrefixSuffix(copierApiTrader.getCorrectSymbolMap(), copierApiTrader.getPrefixSuffixList());
        FollowSubscribeOrderEntity openOrderMapping = new FollowSubscribeOrderEntity();
        openOrderMapping.setMasterId(Long.valueOf(orderInfo.getMasterId()));
        openOrderMapping.setMasterTicket((int)orderInfo.getTicket());
        openOrderMapping.setMasterSymbol(orderInfo.getOriSymbol());
        openOrderMapping.setMasterLots(BigDecimal.valueOf(orderInfo.getLots()));
        openOrderMapping.setMasterType(orderInfo.getType());
        openOrderMapping.setMasterOpenTime(orderInfo.getOpenTime());
        openOrderMapping.setComment( orderInfo.getComment());
        openOrderMapping.setDetectedOpenTime(orderInfo.getDetectedOpenTime());
        openOrderMapping.setSlaveId(copier.getId());
        openOrderMapping.setSlaveReceiveTime(orderInfo.getSlaveReceiveOpenTime());
        //  依次对备选品种进行开仓尝试
        for (String symbol : orderInfo.getSymbolList()) {
            orderInfo.setSymbol(symbol);
            AbstractFollowRule.PermitInfo permitInfo = this.followRule.permit(leaderCopier, orderInfo, copierApiTrader);
            openOrderMapping.setSlaveSymbol(orderInfo.getSymbol());
            openOrderMapping.setFollowMode(leaderCopier.getFollowMode());
            openOrderMapping.setFollowParam(leaderCopier.getFollowParam());
            openOrderMapping.setDirection(leaderCopier.getFollowDirection());
            openOrderMapping.setSlaveLots(BigDecimal.valueOf(permitInfo.getLots()));
            openOrderMapping.setFlag(permitInfo.getPermit());
            openOrderMapping.setExtra("[开仓]" + permitInfo.getExtra());
            log.info("开仓1========"+ permitInfo.getExtra());
            if (permitInfo.getPermitted()) {
                if (sendOrder(orderInfo, leaderCopier, openOrderMapping)) {
                    break;
                }
            } else {
                openOrderMappingService.saveOrUpdate(openOrderMapping, Wrappers.<FollowSubscribeOrderEntity>lambdaUpdate().eq(FollowSubscribeOrderEntity::getMasterId, openOrderMapping.getMasterId()).eq(FollowSubscribeOrderEntity::getMasterTicket, openOrderMapping.getMasterTicket()).eq(FollowSubscribeOrderEntity::getSlaveId, openOrderMapping.getSlaveId()));
            }
        }
    }

    /**
     * 跟单者进行开仓
     *
     * @param orderInfo        喊单者订单信息
     * @param leaderCopier     订阅关系
     * @param openOrderMapping 开仓映射关系
     * @return true-开仓结果 true-成功 false-失败
     */
    boolean sendOrder(EaOrderInfo orderInfo, FollowTraderSubscribeEntity leaderCopier, FollowSubscribeOrderEntity openOrderMapping) {
        boolean send = Boolean.FALSE;
        //开单所需要的信息
        String symbol = orderInfo.getSymbol();
        Op op = op(orderInfo, leaderCopier);
        double lots = openOrderMapping.getSlaveLots().doubleValue();
        String comment = comment(orderInfo);
        int magic = Integer.parseInt(orderInfo.getAccount().trim());
        Order order;
        switch (op) {
            case Buy:
            case Sell:
                try {
                    log.debug("[MT4跟单者:{}-{}-{}]收到订单{},开始下单,下单品种:{},下单手数:{}", copier.getId(), copier.getAccount(), copier.getServerName(), orderInfo, symbol, lots);
                    try {
                        //  double price = openPrice4(copierApiTrader, symbol, op.getValue());
                        double price = 0.0;
                        order = copierApiTrader.orderClient.OrderSend(symbol, op, lots, price, Integer.MAX_VALUE, BigDecimal.ZERO.doubleValue(), BigDecimal.ZERO.doubleValue(), comment.toUpperCase(), magic, null);
                    } catch (TimeoutException e) {
                        order = Arrays.stream(copierApiTrader.quoteClient.GetOpenedOrders()).filter(o -> o.Comment.equalsIgnoreCase(comment)).findFirst().orElse(null);
                        if (order == null) {
                            throw new RuntimeException(e);
                        }
                    }
                    send = Boolean.TRUE;
                    if (orderInfo.getSl() > BigDecimal.ZERO.doubleValue() || orderInfo.getTp() > BigDecimal.ZERO.doubleValue()) {
                        if (leaderCopier.getTpSl().equals(CloseOrOpenEnum.OPEN.getValue())) {
                            try {
                                copierApiTrader.orderClient.OrderModify(op, order.Ticket, 0, orderInfo.getSl(), orderInfo.getTp(), null);
                            } catch (Exception ignored) {
                            }
                        } else {
                            log.info("[MT4跟单者:{}-{}-{}]不需要修改报价sl:{},tp:{}成功", copier.getId(), copier.getAccount(), copier.getServerName(), orderInfo.getSl(), orderInfo.getTp());
                        }
                    }
                    log.debug("[MT4跟单者:{}-{}-{}]收到订单{},结束下单", copier.getId(), copier.getAccount(), copier.getServerName(), orderInfo);
                    // 开仓的比例，用于后续计算跟单者平仓的时候的手数，修改部分平仓逻辑后，此处ratio目前已经没有用了,因为现在即使喊单者是部分平仓，跟单者也是全平然后跟随开仓。
                    openOrderMapping.setSlaveType(order.Type.getValue());
                    openOrderMapping.setSlaveTicket(order.Ticket);
                    openOrderMapping.setSlaveOpenTime(order.OpenTime);
                    openOrderMapping.setSlaveOpenPrice(BigDecimal.valueOf(order.OpenPrice));
                    openOrderMapping.setComment(orderInfo.getComment());
                    openOrderMapping.setSlaveComment(order.Comment);
                    openOrderMapping.setSlavePosition(BigDecimal.valueOf(order.Lots));
                    openOrderMapping.setFlag(CopyTradeFlag.OS);
                    openOrderMapping.setExtra("[开仓]即时价格成交");

                    //缓存跟单者的开仓信息
                    CachedCopierOrderInfo cachedCopierOrderInfo = new CachedCopierOrderInfo(order);
//                    cachedCopierOrderInfo.setRatio(openOrderMapping.getRatio().doubleValue());
                    redisUtil.hset(mapKey, Long.toString(orderInfo.getTicket()), cachedCopierOrderInfo, 0);
                } catch (Exception exception) {
                    if (AbstractApiTrader.availableException4.contains(exception.getMessage())) {
                        log.info(exception.getMessage());
                    } else {
                        exception.printStackTrace();
                    }
//                    openOrderMapping.setRatio(new BigDecimal(lots / orderInfo.getLots()));
                    openOrderMapping.setSlaveType(op.getValue());
                    openOrderMapping.setSlaveTicket(null);
                    openOrderMapping.setFlag(CopyTradeFlag.OF1);
                    openOrderMapping.setExtra("[开仓]即时价格开仓失败" + exception.getMessage());
                }
                openOrderMappingService.saveOrUpdate(openOrderMapping, Wrappers.<FollowSubscribeOrderEntity>lambdaUpdate().eq(FollowSubscribeOrderEntity::getMasterId, openOrderMapping.getMasterId()).eq(FollowSubscribeOrderEntity::getMasterTicket, openOrderMapping.getMasterTicket()).eq(FollowSubscribeOrderEntity::getSlaveId, openOrderMapping.getSlaveId()));
                break;
            // 挂单只是在数据库中记录(修改为挂单在数据库中数据都不记录)，而跟单者没有执行真实的挂单操作，因为不同的经纪商实际的价格会有差异，
            // 有可能出现跟单者挂单被触发，而喊单者的挂单被触发，造成跟随出现重大差异。
            case BuyLimit:
            case SellLimit:
            case BuyStop:
            case SellStop:
            case Balance:
            case Credit:
                // TODO: 2023/5/30 余额和赠金需要触发报表刷新
                // 挂单设置跟单者开单订单号为0L.
                openOrderMapping.setSlaveType(op.getValue());
                openOrderMapping.setSlaveTicket(0);
                openOrderMapping.setFlag(0);
                openOrderMapping.setExtra("[开仓]挂单");
                break;
            default:
                log.error("开仓类型{}不正确", op.getValue());
                break;
        }
        return send;
    }
}

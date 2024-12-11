package net.maku.subcontrol.rule;


import cn.hutool.core.util.ObjectUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowSysmbolSpecificationEntity;
import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.followcom.entity.FollowVarietyEntity;
import net.maku.followcom.enums.FollowRemainderEnum;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.followcom.service.FollowSysmbolSpecificationService;
import net.maku.followcom.service.FollowVarietyService;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.framework.common.cache.RedisCache;
import net.maku.subcontrol.trader.AbstractApiTrader;
import net.maku.subcontrol.trader.LeaderApiTrader;
import net.maku.subcontrol.trader.LeaderApiTradersAdmin;
import online.mtapi.mt4.Exception.ConnectException;
import online.mtapi.mt4.Exception.InvalidSymbolException;
import online.mtapi.mt4.Exception.TimeoutException;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * @author X.T. LI
 */
@Slf4j
public abstract class AbstractFollowRule {
    protected FixedLot fixedLot = new FixedLot();
    protected FixedRatio fixedRatio = new FixedRatio();
    protected FixedEuqit fixedEuqit = new FixedEuqit();

    /**
     * 链中下一元素
     */
    protected AbstractFollowRule nextRule = null;
    protected LeaderApiTradersAdmin leaderApiTradersAdmin = SpringContextUtils.getBean(LeaderApiTradersAdmin.class);
    protected  RedisCache redisCache= SpringContextUtils.getBean(RedisCache.class);
    protected  FollowSysmbolSpecificationService followSysmbolSpecificationService= SpringContextUtils.getBean(FollowSysmbolSpecificationService.class);
    protected FollowVarietyService followVarietyService= SpringContextUtils.getBean(FollowVarietyService.class);
    public void setNextRule(AbstractFollowRule nextRule) {
        this.nextRule = nextRule;
    }

    public PermitInfo rule(FollowTraderSubscribeEntity eaLeaderCopier, EaOrderInfo eaOrderInfo, AbstractApiTrader copierApiTrader) {
        PermitInfo permitInfo = permit(eaLeaderCopier, eaOrderInfo, copierApiTrader);
        if (permitInfo.getPermitted()) {
            //允许跟随
            if (nextRule != null) {
                //需要进一步判断
                return nextRule.permit(eaLeaderCopier, eaOrderInfo, copierApiTrader);
            } else {
                return permitInfo;
            }
        } else {
            return permitInfo;
        }
    }

    /**
     * 计算跟单者跟随的时候，根据当前订单跟随规则，如果是强制开仓，计算完成后不足最小手数，以最小手数开仓。
     *
     * @param masterSlave     订阅关系
     * @param eaOrderInfo     开仓订单信息
     * @param copierApiTrader 跟单者
     * @return double 跟单者开仓手数
     */
    public double lots(FollowTraderSubscribeEntity masterSlave, EaOrderInfo eaOrderInfo, AbstractApiTrader copierApiTrader) throws InvalidSymbolException, ConnectException, TimeoutException {
        double lots = 0;
        if (ObjectUtils.isEmpty(masterSlave)) {
            return 0.0;
        }
        //MT4平台
        LeaderApiTrader LeaderApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(eaOrderInfo.getMasterId().toString());
        double pr = getPr(copierApiTrader, copierApiTrader.getTrader().getId(), eaOrderInfo.getSymbol());
        switch (masterSlave.getFollowMode()) {
            case 0:
                //按固定手数
                lots = fixedLot.lots(masterSlave, eaOrderInfo, 0.0, null);
                break;
            case 1:
                //按比例跟单
                log.info("{}按比例下单参数:下单比例{}-下单手数{}-合约比例{}",copierApiTrader.getTrader().getAccount(), masterSlave.getFollowParam(),eaOrderInfo.getLots(),pr);
                lots = fixedRatio.lots(masterSlave, eaOrderInfo, 0.0, null,pr);
                break;
            case 2:
                log.info("{}按净值下单参数:下单比例{}-下单手数{}-喊单净值{}-跟单净值{}-合约比例{}",copierApiTrader.getTrader().getAccount(), masterSlave.getFollowParam(),eaOrderInfo.getLots(),LeaderApiTrader.quoteClient.Equity, copierApiTrader.quoteClient.Equity,pr);
                //按净值比例
                lots = fixedEuqit.lots(eaOrderInfo, LeaderApiTrader.quoteClient.Equity, copierApiTrader.quoteClient.Equity,masterSlave,pr);
                break;
            case 3:
                //按资金比例(余额
                lots = fixedEuqit.lots(eaOrderInfo, LeaderApiTrader.quoteClient.Balance, copierApiTrader.quoteClient.Balance);
            default:
                lots = 0.0;
                break;
        }
        BigDecimal lots2digits;
        if (masterSlave.getRemainder().equals(FollowRemainderEnum.ROUND_IT_UP.getValue())){
            lots2digits = BigDecimal.valueOf(lots).setScale(2, RoundingMode.HALF_UP);
        } else {
            lots2digits = BigDecimal.valueOf(lots).setScale(2, RoundingMode.DOWN);
        }
        return lots2digits.doubleValue();
    }

    /***
     * 获取合约比例
     * */
    public double getPr(AbstractApiTrader copierApiTrader,long traderId,String symbol){
        double pr = 1;
        // 查看品种匹配 模板
        List<FollowVarietyEntity> followVarietyEntityList = followVarietyService.getListByTemplated(copierApiTrader.getTrader().getTemplateId());
        Integer contract = followVarietyEntityList.stream().filter(o -> ObjectUtil.isNotEmpty(o.getStdSymbol()) && o.getStdSymbol().equals(symbol)).findFirst()
                .map(FollowVarietyEntity::getStdContract)
                .orElse(0);
        log.info("跟单账号标准合约大小{}", contract);
        if (contract != 0) {
            //查询合约手数比例
            Map<String, FollowSysmbolSpecificationEntity> symbolSpecification = followSysmbolSpecificationService.getByTraderId(traderId);
            FollowSysmbolSpecificationEntity followSysmbolSpecificationEntity = symbolSpecification.get(symbol);
            if (ObjectUtil.isNotEmpty(followSysmbolSpecificationEntity)) {
                log.info("对应合约值{}", followSysmbolSpecificationEntity.getContractSize());
                pr = (double) contract / followSysmbolSpecificationEntity.getContractSize();
            }
        }
        return pr;
    }

    /**
     * 判断
     *
     * @param leaderCopier    跟随关系
     * @param orderInfo       交易信号
     * @param copierApiTrader 跟单者
     * @return PermitInfo
     */
    protected abstract PermitInfo permit(FollowTraderSubscribeEntity leaderCopier, EaOrderInfo orderInfo, AbstractApiTrader copierApiTrader);


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PermitInfo {
        private Boolean permitted;
        private int permit;
        private String extra;
        private double lots;
    }
}


package net.maku.subcontrol.rule;


import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.followcom.pojo.EaOrderInfo;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * @author X.T. LI
 */
@Slf4j
public class FixedEuqit{

    public double lots(EaOrderInfo eaOrderInfo,double equityMaster, double equitySlave) {
        return BigDecimal.valueOf(equitySlave).divide(BigDecimal.valueOf(equityMaster),2, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(eaOrderInfo.getLots())).doubleValue();
    }
    //跟单/喊单净值*下单比例*合约比例*喊单者手数=跟单实际下单手数
    public double lots(EaOrderInfo eaOrderInfo, double equityMaster, double equitySlave, FollowTraderSubscribeEntity eaLeaderCopier,double pr,double leaderPr) {
        return BigDecimal.valueOf(equitySlave).divide(BigDecimal.valueOf(equityMaster),2, RoundingMode.HALF_UP).multiply(new BigDecimal(eaLeaderCopier.getFollowParam().toString())).multiply(BigDecimal.valueOf(pr)).multiply(BigDecimal.valueOf(eaOrderInfo.getLots())).divide(BigDecimal.valueOf(leaderPr),2,RoundingMode.HALF_UP).doubleValue();
    }
}

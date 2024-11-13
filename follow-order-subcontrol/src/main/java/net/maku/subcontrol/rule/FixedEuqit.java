package net.maku.subcontrol.rule;


import lombok.extern.slf4j.Slf4j;
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

}

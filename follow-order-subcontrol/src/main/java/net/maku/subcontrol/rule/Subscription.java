package net.maku.subcontrol.rule;

import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.followcom.enums.CopyTradeFlag;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.subcontrol.trader.CopierApiTrader;
import org.jetbrains.annotations.NotNull;
import org.springframework.util.ObjectUtils;

/**
 * @author samson bruce
 */
@Slf4j
public class Subscription extends AbstractFollowRule {

    @Override
    protected PermitInfo permit(FollowTraderSubscribeEntity leaderCopier, EaOrderInfo orderInfo, CopierApiTrader copier4ApiTrader) {
        return getPermitInfo(leaderCopier, orderInfo);
    }

    @NotNull
    private PermitInfo getPermitInfo(FollowTraderSubscribeEntity leaderCopier, EaOrderInfo eaOrderInfo) {
        PermitInfo permitInfo;
        if (ObjectUtils.isEmpty(leaderCopier)) {
            permitInfo = new PermitInfo(Boolean.FALSE, CopyTradeFlag.OF2, "订阅关系不存在", 0);
            return permitInfo;
        }

        if (eaOrderInfo.isArrears()) {
            permitInfo = new PermitInfo(Boolean.FALSE, CopyTradeFlag.OF6, "系统暂停开仓(喊单者账号欠费)", 0);
            return permitInfo;
        }
        permitInfo = new PermitInfo();
        permitInfo.setPermitted(Boolean.TRUE);
        return permitInfo;
    }
}

package net.maku.subcontrol.rule;


import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.followcom.enums.CopyTradeFlag;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.subcontrol.trader.AbstractApiTrader;
import net.maku.subcontrol.trader.CopierApiTrader;
import online.mtapi.mt4.Exception.ConnectException;
import online.mtapi.mt4.Exception.InvalidSymbolException;
import online.mtapi.mt4.Exception.TimeoutException;

/**
 * @author samson bruce
 */
@Slf4j
public class FollowRule {
    public Subscription subscription;
    public Comment comment;
    public Risk risk;
    public Cycle cycle;


    public FollowRule() {
        subscription = new Subscription();
        comment = new Comment();
        risk = new Risk();
        cycle = new Cycle();
        //设置责任链的先后顺序：subscription->comment->risk->cycle
        subscription.setNextRule(comment);
        comment.setNextRule(risk);
        cycle.setNextRule(cycle);
    }

    /**
     * 判断
     *
     * @param eaLeaderCopier   跟随关系
     * @param eaOrderInfo      交易信号
     * @param copier4ApiTrader 跟单者
     * @return PermitInfo
     */

    public AbstractFollowRule.PermitInfo permit(FollowTraderSubscribeEntity eaLeaderCopier, EaOrderInfo eaOrderInfo, AbstractApiTrader copier4ApiTrader) {
        AbstractFollowRule.PermitInfo permitInfo = new AbstractFollowRule.PermitInfo();
        try {
            permitInfo.setLots(subscription.lots(eaLeaderCopier, eaOrderInfo, copier4ApiTrader));
        } catch (InvalidSymbolException e) {
            permitInfo.setPermit(CopyTradeFlag.OF1);
            permitInfo.setPermitted(Boolean.FALSE);
            permitInfo.setExtra(InvalidSymbolException.class.getSimpleName());
        } catch (ConnectException e) {
            permitInfo.setPermit(-1);
            permitInfo.setPermitted(Boolean.FALSE);
            permitInfo.setExtra(ConnectException.class.getSimpleName());
        } catch (TimeoutException e) {
            permitInfo.setPermit(-1);
            permitInfo.setPermitted(Boolean.FALSE);
            permitInfo.setExtra(TimeoutException.class.getSimpleName());
        }
        return permitInfo;
    }

}

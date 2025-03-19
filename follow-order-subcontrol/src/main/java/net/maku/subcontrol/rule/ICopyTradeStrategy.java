package net.maku.subcontrol.rule;


import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.subcontrol.pojo.EaSymbolInfo;

/**
 * @author X.T. LI
 */
public interface ICopyTradeStrategy {
    /**
     * 计算跟单者的开仓手数
     *
     * @param eaLeaderCopier 跟随关系
     * @param eaOrderInfo   喊单者的订单信息
     * @param lSymbolInfo   喊单者的SymbolInfo
     * @param fSymbolInfo   跟单者的SymbolInfo
     * @param equity        跟单者当前净值
     * @param currency      跟单者的计算货币
     * @return 跟单者应该开仓的手数
     */
    double lots(FollowTraderSubscribeEntity eaLeaderCopier, EaOrderInfo eaOrderInfo, EaSymbolInfo lSymbolInfo, EaSymbolInfo fSymbolInfo, double equity, String currency);


    /**
     * fix 2022/06/30跨平台跟单，合约大小报价点位差异造成一手的实际价值大小不一致的问题，
     * 计算跨平台跟单时，不同品种合约价值不一致的情况。
     *
     * @param lSymbolInfo 喊单者的SymbolInfo
     * @param fSymbolInfo 跟单者的SymbolInfo
     * @return 比率
     */
    static double lfRatio(EaSymbolInfo lSymbolInfo, EaSymbolInfo fSymbolInfo) {
        try {
            double v = pointValue(lSymbolInfo) / pointValue(fSymbolInfo);
            if(Double.isNaN(v)){
                return 0D;
            }else{
                return v;
            }
        } catch (Exception e) {
            e.printStackTrace();
            //品种不存在会抛出异常。直接做0处理，后续代码逻辑会判断该货币品种不存在；
           return 0D;
        }
    }

    /**
     * 计算点价值
     *
     * @param symbolInfo symbolInfo
     * @return 品种一个点的价值
     */
    static double pointValue(EaSymbolInfo symbolInfo) {
        return (symbolInfo.getTradeTickValue() / (symbolInfo.getTradeTickSize() / symbolInfo.getPoint())) * Math.pow(10, symbolInfo.getDigits());
    }


    double lots(FollowTraderSubscribeEntity eaLeaderCopier, EaOrderInfo eaOrderInfo, double equity, String currency);
}

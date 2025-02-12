package net.maku.followcom.service;


import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.followcom.vo.FixTemplateVO;
import net.maku.followcom.vo.FollowTraderVO;
import online.mtapi.mt4.QuoteClient;

/**
 * Author:  zsd
 * Date:  2025/1/23/周四 9:30
 */
public interface MessagesService {

    public void send(FixTemplateVO vo);
    public void isRepairSend(EaOrderInfo orderInfo, FollowTraderEntity follow, FollowTraderVO master, QuoteClient quoteClient);
    public void isRepairClose(EaOrderInfo orderInfo, FollowTraderEntity follow, FollowTraderVO master);
}

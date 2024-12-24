package net.maku.subcontrol.service;


import jakarta.validation.Valid;
import net.maku.followcom.vo.*;

import java.util.List;

public interface FollowApiService {
    //喊单账号添加
    Boolean save(FollowTraderVO vo);

    void delete(List<Long> idList);

    Boolean addSlave(FollowAddSalveVo vo);

    Boolean updateSlave(FollowUpdateSalveVo vo);

    //喊单表主从表同时增加
    Boolean insertSource(SourceInsertVO vo);

    //喊单表主从表同时更新
    Boolean updateSource(SourceUpdateVO vo);

    //喊单表主从表同时更新
    Boolean delSource(SourceDelVo vo);

    Boolean insertFollow(FollowInsertVO vo);

    Boolean updateFollow(FollowUpdateVO vo);

    Boolean delFollow(SourceDelVo vo);

    //查询平仓订单
    OrderClosePageVO orderCloseList(OrderHistoryVO vo);

    Boolean orderSend(OrderSendVO vo);

    Boolean orderClose(OrderCloseVO vo);

    Boolean orderCloseAll( OrderCloseAllVO vo);

    Boolean changePassword( ChangePasswordVO vo);
}

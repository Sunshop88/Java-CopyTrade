package net.maku.subcontrol.service;


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
}

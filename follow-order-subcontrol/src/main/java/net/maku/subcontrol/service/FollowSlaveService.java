package net.maku.subcontrol.service;

import net.maku.followcom.entity.FollowPlatformEntity;
import net.maku.subcontrol.vo.RepairSendVO;

public interface FollowSlaveService {
    Boolean repairSend(RepairSendVO repairSendVO);
}

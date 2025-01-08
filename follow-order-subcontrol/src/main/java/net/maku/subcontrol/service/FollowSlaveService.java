package net.maku.subcontrol.service;

import net.maku.followcom.entity.FollowPlatformEntity;
import net.maku.subcontrol.vo.RepairSendVO;

import java.util.List;

public interface FollowSlaveService {
    Boolean repairSend(RepairSendVO repairSendVO);

    Boolean batchRepairSend(List<RepairSendVO> repairSendVO);
}

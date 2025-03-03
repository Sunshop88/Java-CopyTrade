package net.maku.subcontrol.service;

import jakarta.servlet.http.HttpServletRequest;
import net.maku.followcom.entity.FollowPlatformEntity;
import net.maku.followcom.vo.RepairCloseVO;
import net.maku.subcontrol.vo.RepairSendVO;

import java.util.List;

public interface FollowSlaveService {
    Boolean repairSend(RepairSendVO repairSendVO);

    Boolean batchRepairSend(List<RepairSendVO> repairSendVO, HttpServletRequest req);

    Boolean repairOrderClose(List<RepairCloseVO> repairCloseVO);
}

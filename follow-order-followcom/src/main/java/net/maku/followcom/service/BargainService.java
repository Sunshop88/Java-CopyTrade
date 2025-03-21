package net.maku.followcom.service;

import jakarta.servlet.http.HttpServletRequest;
import net.maku.followcom.dto.MasOrderSendDto;
import net.maku.followcom.dto.MasToSubOrderCloseDto;
import net.maku.framework.common.utils.Result;

import java.util.List;

/**
 * Author:  zsd
 * Date:  2025/2/25/周二 17:17
 */
public interface BargainService {
    void masOrderSend(MasOrderSendDto vo, HttpServletRequest request);

    void masOrderClose(MasToSubOrderCloseDto vo, HttpServletRequest request);

    void stopOrder(Integer type, String orderNo, String traderList);
}

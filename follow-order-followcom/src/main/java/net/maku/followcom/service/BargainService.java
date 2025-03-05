package net.maku.followcom.service;

import jakarta.servlet.http.HttpServletRequest;
import net.maku.followcom.dto.MasOrderSendDto;
import net.maku.framework.common.utils.Result;

/**
 * Author:  zsd
 * Date:  2025/2/25/周二 17:17
 */
public interface BargainService {
    void masOrderSend(MasOrderSendDto vo, HttpServletRequest request);
}

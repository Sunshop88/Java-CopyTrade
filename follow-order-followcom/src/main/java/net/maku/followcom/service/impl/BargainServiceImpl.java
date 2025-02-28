package net.maku.followcom.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import net.maku.followcom.dto.MasOrderSendDto;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.entity.FollowTraderUserEntity;
import net.maku.followcom.service.*;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.util.RestUtil;
import net.maku.framework.common.utils.Result;
import org.jacoco.agent.rt.internal_1f1cc91.core.internal.flow.IFrame;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import net.maku.followcom.dto.MasToSubOrderSendDto;

import java.util.List;

/**
 * Author:  zsd
 * Date:  2025/2/25/周二 17:18
 */
@Service
@AllArgsConstructor
public class BargainServiceImpl implements BargainService {

    private final FollowVpsService followVpsService;
    private final FollowTraderUserService followTraderUserService;
    private final FollowTraderService followTraderService;
    private final FollowOrderInstructService followOrderInstructService;

    @Override
    public void masOrderSend(MasOrderSendDto vo, HttpServletRequest request) {
        //创建父指令

        vo.getTraderList().forEach(o->{
            FollowTraderUserEntity followTraderUserEntity = followTraderUserService.getById(o);
            List<FollowTraderEntity> followTraderEntityList = followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getAccount, followTraderUserEntity.getAccount()).eq(FollowTraderEntity::getPlatformId, followTraderUserEntity.getPlatformId()));
            followTraderEntityList.forEach(fo->{
                //创建子指令

                //发送请求
                MasToSubOrderSendDto masToSubOrderSendDto = new MasToSubOrderSendDto();
                BeanUtil.copyProperties(fo,masToSubOrderSendDto);
                masToSubOrderSendDto.setTraderId(fo.getId());
                Result result = RestUtil.sendRequest(request, fo.getIpAddr(), HttpMethod.POST, FollowConstant.MASORDERSEND, masToSubOrderSendDto);
            });
        });
    }
}

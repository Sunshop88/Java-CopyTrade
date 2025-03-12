package net.maku.mascontrol.task;

import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSONObject;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.entity.FollowVpsEntity;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.enums.MessagesTypeEnum;
import net.maku.followcom.service.MessagesService;
import net.maku.followcom.vo.FixTemplateVO;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.constant.Constant;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Author:  zsd
 * Date:  2025/1/23/周四 15:20
 * 漏单通知
 */
@Slf4j
@Component
@AllArgsConstructor
@EnableScheduling
public class MissingOrdersNoticeTask {
    private final MessagesService messagesService;
    private final RedisCache redisCache;

   @Scheduled(cron = "0 0/10 * * * ?")
   // @Scheduled(cron = "* * * * * ?")
    public void notice() {
        Set<String> sendKeys = redisCache.keys(Constant.REPAIR_SEND + "*");
        Set<String> closeKeys  = redisCache.keys(Constant.REPAIR_CLOSE + "*");
        AtomicReference<Integer> num= new AtomicReference<>(0);
        sendKeys.forEach(key -> {
            Map<Object, Object> stringObjectMap = redisCache.hGetStrAll(key);
            if(stringObjectMap!=null){
                stringObjectMap.values().forEach(obj->{
                    JSONObject jsonObject = JSONObject.parseObject(obj.toString());
                    Collection<Object> values = jsonObject.values();
                    num.updateAndGet(v -> v + values.size());

                });
            }
        });
        //检查漏单信息
        closeKeys.forEach(key -> {
            Map<Object, Object> stringObjectMap = redisCache.hGetStrAll(key);
            if(stringObjectMap!=null){
                stringObjectMap.values().forEach(obj->{
                    try {
                        JSONObject jsonObject = JSONObject.parseObject(obj.toString());
                        Collection<Object> values = jsonObject.values();
                        num.updateAndGet(v -> v + values.size());
                    } catch (Exception e) {
                      log.error("定时发送信息"+e);
                    }

                });
            }
        });
       Integer i = num.get();
       if(i>0){
           FixTemplateVO vo=FixTemplateVO.builder().templateType(MessagesTypeEnum.MISSING_ORDERS_INSPECT.getCode()).num(num.get()).build();
           messagesService.send(vo);
        }


    }

    /***
     * 判断是否需要发送消息
     * 1、VPS运行状态/VPS漏单监控  任意一个关闭，则整个VPS不监控
     * 2、VPS正常运行/VPS漏单监控开启，则判断账号跟单状态
     * 2/1主账号跟单状态关闭，则此主账号下所有跟单账号漏单都不监控
     * 2/2主账号跟单状态开启，跟单账号跟单状态关闭，则此跟单账号漏单不监控
     * 2/3主账号跟单状态开启，跟单账号跟单状态开启，则正常监控
     * */
    public  Boolean isSend(FollowVpsEntity vps, FollowTraderEntity follow, FollowTraderEntity master){
        if(ObjectUtil.isNotEmpty(vps.getIsMonitorRepair()) && vps.getIsMonitorRepair().equals(CloseOrOpenEnum.CLOSE.getValue())){
            return false;
        }
        if(ObjectUtil.isNotEmpty(vps.getIsActive()) && vps.getIsActive().equals(CloseOrOpenEnum.CLOSE.getValue())){
            return false;
        }
        if(ObjectUtil.isNotEmpty(master) &&  master.getFollowStatus().equals(CloseOrOpenEnum.CLOSE.getValue())){
            return false;
        }
        if(ObjectUtil.isNotEmpty(follow) &&  follow.getFollowStatus().equals(CloseOrOpenEnum.CLOSE.getValue())){
            return false;
        }

        return true;
    }

}

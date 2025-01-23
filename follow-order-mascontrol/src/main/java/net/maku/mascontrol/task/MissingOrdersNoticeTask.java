package net.maku.mascontrol.task;

import com.alibaba.fastjson.JSONObject;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

   //@Scheduled(cron = "0 0/10 * * * ?")
    @Scheduled(cron = "* * * * * ?")
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
                    JSONObject jsonObject = JSONObject.parseObject(obj.toString());
                    Collection<Object> values = jsonObject.values();
                    num.updateAndGet(v -> v + values.size());

                });
            }
        });
        FixTemplateVO vo=FixTemplateVO.builder().templateType(MessagesTypeEnum.MISSING_ORDERS_INSPECT.getCode()).num(num.get()).build();
        messagesService.send(vo);

    }

}

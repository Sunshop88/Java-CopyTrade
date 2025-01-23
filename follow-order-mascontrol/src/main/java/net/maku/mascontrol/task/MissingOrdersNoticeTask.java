package net.maku.mascontrol.task;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.service.MessagesService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Author:  zsd
 * Date:  2025/1/23/周四 15:20
 * 漏单通知
 */
@Slf4j
@Component
@AllArgsConstructor
public class MissingOrdersNoticeTask {
    private final MessagesService messagesService;

    @Scheduled(cron = "0 0/10 * * * ?")
    public void notice() {
        System.out.println("111");
    }

}

package yygh.schedule;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import yygh.constant.MqConst;
import yygh.service.RabbitService;

@Component
@EnableScheduling
public class ScheduledTask {

    @Autowired
    private RabbitService rabbitService;

    //每天8点执行方法，就医提醒：0 0 8 * * ?
    //cron表达式，设置执行间隔，参考https://cron.qqe2.com/
    //@Scheduled(cron = "0/30 * * * * ?")
    @Scheduled(cron = "0 0 8 * * ?")
    public void taskPatient() {
        System.out.println("发起定时任务");
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_TASK,
                MqConst.ROUTING_TASK_8,"");
    }
}

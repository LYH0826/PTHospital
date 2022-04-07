package yygh.receiver;

import com.rabbitmq.client.Channel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import yygh.constant.MqConst;
import yygh.model.hosp.Schedule;
import yygh.service.RabbitService;
import yygh.service.ScheduleService;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import yygh.vo.msm.MsmVo;
import yygh.vo.order.OrderMqVo;

import java.io.IOException;

//MQ监听器
@Component
public class HospitalReceiver {

    @Autowired
    private ScheduleService scheduleService;

    @Autowired
    private RabbitService rabbitService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_ORDER, durable = "true"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_ORDER),
            key = {MqConst.ROUTING_ORDER}
    ))
    public void receiver(OrderMqVo orderMqVo, Message message, Channel channel)
            throws IOException {
        if(null != orderMqVo.getAvailableNumber()) {
            //下单成功更新预约数
            System.out.println("orderMqVoScheduleId = " + orderMqVo.getScheduleId());
            Schedule schedule = scheduleService.getById(orderMqVo.getScheduleId());
            System.out.println("schedule = " + schedule);
            schedule.setReservedNumber(orderMqVo.getReservedNumber());
            schedule.setAvailableNumber(orderMqVo.getAvailableNumber());
            scheduleService.update(schedule);
        } else {
            //取消预约更新预约数
            String hoscode = orderMqVo.getHoscode();
            String scheduleId = orderMqVo.getScheduleId();
            System.out.println("orderMqVoScheduleId = " + scheduleId);
            System.out.println("hoscode = " + hoscode);
            //Schedule schedule = scheduleService.getById(scheduleId);
            Schedule schedule = scheduleService.getScheduleByHoscodeAndHosScheduleId(hoscode,scheduleId);
            System.out.println("scheduleCancel = " + schedule);
            int availableNumber = schedule.getAvailableNumber() + 1;
            schedule.setAvailableNumber(availableNumber);
            scheduleService.update(schedule);
        }

        //发送短信
        MsmVo msmVo = orderMqVo.getMsmVo();
        if(null != msmVo) {
            rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_MSM, MqConst.ROUTING_MSM_ITEM, msmVo);
        }
    }
}

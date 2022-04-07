package yygh.service;

import com.baomidou.mybatisplus.extension.service.IService;
import yygh.model.order.OrderInfo;
import yygh.model.order.PaymentInfo;

import java.util.Map;

public interface PaymentService extends IService<PaymentInfo> {
    //保存交易记录(1:微信)
    void savePaymentInfo(OrderInfo orderInfo,Integer paymentType);

    //更新订单状态
    void paySuccess(String out_trade_no, Map<String, String> resultMap);

    //获取支付记录
    PaymentInfo getPaymentInfo(Long orderId, Integer paymentType);
}

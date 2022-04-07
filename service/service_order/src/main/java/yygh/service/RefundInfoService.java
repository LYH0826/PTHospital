package yygh.service;

import com.baomidou.mybatisplus.extension.service.IService;
import yygh.model.order.PaymentInfo;
import yygh.model.order.RefundInfo;

public interface RefundInfoService extends IService<RefundInfo> {

    //保存退款记录
    RefundInfo saveRefundInfo(PaymentInfo paymentInfo);
}

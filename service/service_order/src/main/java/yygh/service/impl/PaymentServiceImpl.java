package yygh.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import yygh.client.HospitalFeignClient;
import yygh.enums.OrderStatusEnum;
import yygh.enums.PaymentStatusEnum;
import yygh.enums.PaymentTypeEnum;
import yygh.helper.HttpRequestHelper;
import yygh.mapper.PaymentInfoMapper;
import yygh.model.order.OrderInfo;
import yygh.model.order.PaymentInfo;
import yygh.service.OrderService;
import yygh.service.PaymentService;
import yygh.vo.order.SignInfoVo;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class PaymentServiceImpl
        extends ServiceImpl<PaymentInfoMapper, PaymentInfo>
        implements PaymentService {

    @Autowired
    private OrderService orderService;

    @Autowired
    private HospitalFeignClient hospitalFeignClient;

    //保存交易记录
    @Override
    public void savePaymentInfo(OrderInfo orderInfo, Integer paymentType) {
        QueryWrapper<PaymentInfo> wrapper = new QueryWrapper<>();
        wrapper.eq("order_id", orderInfo.getId());
        wrapper.eq("payment_type", paymentType);
        Integer count = baseMapper.selectCount(wrapper);
        //count>0说明结账表中查到该条数据
        if (count > 0) {
            return;
        }
        //保存交易记录
        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setCreateTime(new Date());
        paymentInfo.setOrderId(orderInfo.getId());
        paymentInfo.setPaymentType(paymentType);
        paymentInfo.setOutTradeNo(orderInfo.getOutTradeNo());
        paymentInfo.setPaymentStatus(PaymentStatusEnum.UNPAID.getStatus());

        String subject = new DateTime(orderInfo.getReserveDate())
                .toString("yyyy-MM-dd") + "|"
                + orderInfo.getHosname() + "|"
                + orderInfo.getDepname() + "|"
                + orderInfo.getTitle();

        paymentInfo.setSubject(subject);
        paymentInfo.setTotalAmount(orderInfo.getAmount());
        baseMapper.insert(paymentInfo);
    }

    //更新订单状态
    @Override
    public void paySuccess(String out_trade_no, Map<String, String> resultMap) {
        //1 根据订单编号得到支付记录
        QueryWrapper<PaymentInfo> wrapper = new QueryWrapper<>();
        wrapper.eq("out_trade_no",out_trade_no);
        wrapper.eq("payment_type", PaymentTypeEnum.WEIXIN.getStatus());
        PaymentInfo paymentInfo = baseMapper.selectOne(wrapper);

        //2 更新支付记录信息
        //支付状态
        paymentInfo.setPaymentStatus(PaymentStatusEnum.PAID.getStatus());
        //回调时间
        paymentInfo.setCallbackTime(new Date());
        //交易编号
        paymentInfo.setTradeNo(resultMap.get("transaction_id"));
        //回调信息
        paymentInfo.setCallbackContent(resultMap.toString());
        baseMapper.updateById(paymentInfo);

        //3 根据订单号得到订单信息
        OrderInfo orderInfo = orderService.getById(paymentInfo.getOrderId());

        //4 更新订单信息
        orderInfo.setOrderStatus(OrderStatusEnum.PAID.getStatus());
        orderService.updateById(orderInfo);

        //5 调用医院接口，更新订单支付信息
        SignInfoVo signInfoVo = hospitalFeignClient.getSignInfoVo(orderInfo.getHoscode());
        Map<String,Object> reqMap = new HashMap<>();
        reqMap.put("hoscode",orderInfo.getHoscode());
        //预约记录唯一标识（医院预约记录主键）
        reqMap.put("hosRecordId",orderInfo.getHosRecordId());
        //时间戳
        reqMap.put("timestamp", HttpRequestHelper.getTimestamp());
        String sign = HttpRequestHelper.getSign(reqMap, signInfoVo.getSignKey());
        reqMap.put("sign", sign);
        JSONObject result = HttpRequestHelper.sendRequest(reqMap, signInfoVo.getApiUrl() + "/order/updatePayStatus");
    }

    //获取支付记录
    @Override
    public PaymentInfo getPaymentInfo(Long orderId, Integer paymentType) {
        QueryWrapper<PaymentInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("order_id", orderId);
        queryWrapper.eq("payment_type", paymentType);
        return baseMapper.selectOne(queryWrapper);
    }
}

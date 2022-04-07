package yygh.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import yygh.enums.RefundStatusEnum;
import yygh.mapper.RefundInfoMapper;
import yygh.model.order.PaymentInfo;
import yygh.model.order.RefundInfo;
import yygh.service.RefundInfoService;

import java.util.Date;

@Service
public class RefundInfoServiceImpl
        extends ServiceImpl<RefundInfoMapper, RefundInfo>
        implements RefundInfoService {

    @Autowired
    private RefundInfoMapper refundInfoMapper;

    //保存退款记录
    @Override
    public RefundInfo saveRefundInfo(PaymentInfo paymentInfo) {
        QueryWrapper<RefundInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("order_id", paymentInfo.getOrderId());
        queryWrapper.eq("payment_type", paymentInfo.getPaymentType());
        RefundInfo refundInfo = refundInfoMapper.selectOne(queryWrapper);
        //有相同数据
        if(null != refundInfo) return refundInfo;
        // 保存交易记录
        refundInfo = new RefundInfo();
        refundInfo.setCreateTime(new Date());
        refundInfo.setOrderId(paymentInfo.getOrderId());
        refundInfo.setPaymentType(paymentInfo.getPaymentType());
        //对外业务编号
        refundInfo.setOutTradeNo(paymentInfo.getOutTradeNo());
        //退款中
        refundInfo.setRefundStatus(RefundStatusEnum.UNREFUND.getStatus());
        refundInfo.setSubject(paymentInfo.getSubject());
        //paymentInfo.setSubject("test");
        refundInfo.setTotalAmount(paymentInfo.getTotalAmount());
        refundInfoMapper.insert(refundInfo);
        return refundInfo;
    }
}

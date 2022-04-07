package yygh.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.github.wxpay.sdk.WXPayConstants;
import com.github.wxpay.sdk.WXPayUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import yygh.enums.PaymentTypeEnum;
import yygh.enums.RefundStatusEnum;
import yygh.model.order.OrderInfo;
import yygh.model.order.PaymentInfo;
import yygh.model.order.RefundInfo;
import yygh.service.OrderService;
import yygh.service.PaymentService;
import yygh.service.RefundInfoService;
import yygh.service.WeixinService;
import yygh.utils.ConstantPropertiesUtils;
import yygh.utils.HttpClient;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class WeixinServiceImpl implements WeixinService {
    @Autowired
    private OrderService orderService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private RefundInfoService refundInfoService;

    //根据订单号下单，生成支付链接
    @Override
    public Map createNative(Long orderId) {
        try {
            //从redis获取数据
            Map payMap = (Map) redisTemplate.opsForValue().get(orderId.toString());
            if (payMap != null) {
                return payMap;
            }
            //1 根据orderId获取订单信息
            OrderInfo order = orderService.getById(orderId);
            //2 向支付记录表添加信息，以微信方式支付
            paymentService.savePaymentInfo(order, PaymentTypeEnum.WEIXIN.getStatus());
            //3 设置参数
            //把参数转换xml格式，使用商户key进行加密
            Map paramMap = new HashMap();
            paramMap.put("appid", ConstantPropertiesUtils.APPID);
            paramMap.put("mch_id", ConstantPropertiesUtils.PARTNER);
            //获取随机字符串 Nonce Str
            paramMap.put("nonce_str", WXPayUtil.generateNonceStr());
            String body = order.getReserveDate() + "就诊" + order.getDepname();
            paramMap.put("body", body);
            //订单交易号
            paramMap.put("out_trade_no", order.getOutTradeNo());
            //参考微信开发平台API文档
            //支付0.01元
            paramMap.put("total_fee", "1");
            //当前ip地址
            paramMap.put("spbill_create_ip", "127.0.0.1");
            //回调地址，暂时没用到
            paramMap.put("notify_url", "http://guli.shop/api/order/weixinPay/weixinNotify");
            //支付类型为NATIVE，即微信扫一扫支付
            paramMap.put("trade_type", "NATIVE");
            //4 调用微信生成二维码接口,httpclient调用
            HttpClient client = new HttpClient("https://api.mch.weixin.qq.com/pay/unifiedorder");
            /**
             * 生成带有 sign 的 XML 格式字符串
             *
             * @param data Map类型数据
             * @param key API密钥
             * @return 含有sign字段的XML
             */
            client.setXmlParam(WXPayUtil.generateSignedXml(paramMap, ConstantPropertiesUtils.PARTNERKEY));
            //支持https加密请求
            client.setHttps(true);
            //post提交
            client.post();
            //5 返回相关数据
            String xml = client.getContent();

            //转换map集合
            /**
             * XML格式字符串转换为Map
             *
             * @param strXML XML字符串
             * @return XML数据转换后的Map
             * @throws Exception
             */
            Map<String, String> resultMap = WXPayUtil.xmlToMap(xml);
            System.out.println("resultMap:" + resultMap);
            //6 封装返回结果集
            Map map = new HashMap<>();
            map.put("orderId", orderId);
            map.put("totalFee", order.getAmount());
            map.put("resultCode", resultMap.get("result_code"));//状态码
            map.put("codeUrl", resultMap.get("code_url")); //二维码地址

            //如果有返回结果
            if (resultMap.get("result_code") != null) {
                //设置支付超时时长为2小时
                redisTemplate.opsForValue().set(orderId.toString(), map, 120, TimeUnit.MINUTES);
            }
            return map;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    //调用微信接口实现支付状态查询
    @Override
    public Map<String, String> queryPayStatus(Long orderId) {
        try {
            //1 根据orderId获取订单信息
            OrderInfo orderInfo = orderService.getById(orderId);

            //2 封装提交参数
            Map paramMap = new HashMap();
            paramMap.put("appid", ConstantPropertiesUtils.APPID);
            paramMap.put("mch_id", ConstantPropertiesUtils.PARTNER);
            //交易订单号
            paramMap.put("out_trade_no", orderInfo.getOutTradeNo());
            //获取随机字符串 Nonce Str
            paramMap.put("nonce_str", WXPayUtil.generateNonceStr());

            //3 设置请求内容
            HttpClient client = new HttpClient("https://api.mch.weixin.qq.com/pay/orderquery");
            /**
             * 生成带有 sign 的 XML 格式字符串
             *
             * @param data Map类型数据
             * @param key API密钥
             * @return 含有sign字段的XML
             */
            client.setXmlParam(WXPayUtil.generateSignedXml(paramMap,ConstantPropertiesUtils.PARTNERKEY));
            client.setHttps(true);
            client.post();

            //4 得到微信接口返回数据
            String xml = client.getContent();
            Map<String, String> resultMap = WXPayUtil.xmlToMap(xml);
            System.out.println("支付状态resultMap:"+resultMap);
            //5 把接口数据返回
            return resultMap;
        }catch(Exception e) {
            return null;
        }
    }

    //微信退款
    @Override
    public Boolean refund(Long orderId) {
        try {
            //获取支付记录信息
            PaymentInfo paymentInfo = paymentService.getPaymentInfo(orderId, PaymentTypeEnum.WEIXIN.getStatus());
            //添加信息到退款记录表
            RefundInfo refundInfo = refundInfoService.saveRefundInfo(paymentInfo);
            //判断当前订单数据是否已经退款
            if(refundInfo.getRefundStatus().intValue() == RefundStatusEnum.REFUND.getStatus().intValue()) {
                return true;
            }
            //调用微信接口实现退款
            //封装需要参数
            Map<String,String> paramMap = new HashMap<>();
            paramMap.put("appid",ConstantPropertiesUtils.APPID); //公众账号ID
            paramMap.put("mch_id",ConstantPropertiesUtils.PARTNER); //商户编号
            paramMap.put("nonce_str",WXPayUtil.generateNonceStr());
            paramMap.put("transaction_id",paymentInfo.getTradeNo()); //微信订单号
            paramMap.put("out_trade_no",paymentInfo.getOutTradeNo()); //商户订单编号
            paramMap.put("out_refund_no","tk"+paymentInfo.getOutTradeNo()); //商户退款单号
//       paramMap.put("total_fee",paymentInfoQuery.getTotalAmount().multiply(new BigDecimal("100")).longValue()+"");
//       paramMap.put("refund_fee",paymentInfoQuery.getTotalAmount().multiply(new BigDecimal("100")).longValue()+"");
            paramMap.put("total_fee","1"); //表示支付金额为0.01元，若按正常逻辑则为上述注释的代码⬆️
            paramMap.put("refund_fee","1");//表示退还金额为0.01元，若按正常逻辑则为上述注释的代码⬆️
            String paramXml = WXPayUtil.generateSignedXml(paramMap,ConstantPropertiesUtils.PARTNERKEY);
            //设置调用接口内容
            HttpClient client = new HttpClient("https://api.mch.weixin.qq.com/secapi/pay/refund");
            client.setXmlParam(paramXml);
            client.setHttps(true);
            //设置证书信息
            client.setCert(true);
            client.setCertPassword(ConstantPropertiesUtils.PARTNER);
            client.post();

            //接收返回数据
            String xml = client.getContent();
            Map<String, String> resultMap = WXPayUtil.xmlToMap(xml);
            if (null != resultMap && WXPayConstants.SUCCESS.equalsIgnoreCase(resultMap.get("result_code"))) {
                refundInfo.setCallbackTime(new Date());
                refundInfo.setTradeNo(resultMap.get("refund_id"));
                refundInfo.setRefundStatus(RefundStatusEnum.REFUND.getStatus());
                refundInfo.setCallbackContent(JSONObject.toJSONString(resultMap));
                refundInfoService.updateById(refundInfo);
                return true;
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}

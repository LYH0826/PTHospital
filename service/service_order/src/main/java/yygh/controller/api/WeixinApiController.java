package yygh.controller.api;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import yygh.enums.PaymentTypeEnum;
import yygh.result.Result;
import yygh.service.PaymentService;
import yygh.service.WeixinService;

import java.util.Map;

@RestController
@RequestMapping("/api/order/weixin")
public class WeixinApiController {
    @Autowired
    private WeixinService weixinService;

    @Autowired
    private PaymentService paymentService;

    //下单 生成二维码
    @GetMapping("/createNative/{orderId}")
    public Result createNative(
            @ApiParam(name = "orderId", value = "订单id", required = true)
            @PathVariable("orderId") Long orderId) {
        return Result.ok(weixinService.createNative(orderId));
    }

    //查询支付状态
    @GetMapping("queryPayStatus/{orderId}")
    public Result queryPayStatus(@PathVariable Long orderId) {
        //调用微信接口实现支付状态查询
        Map<String,String> resultMap = weixinService.queryPayStatus(orderId);
        //判断
        if(resultMap == null) {
            return Result.fail().message("支付出错");
        }
        if("SUCCESS".equals(resultMap.get("trade_state"))) { //支付成功
            //更新订单状态
            String out_trade_no = resultMap.get("out_trade_no");//订单编码
            paymentService.paySuccess(out_trade_no,resultMap);
            return Result.ok().message("支付成功");
        }
        return Result.ok().message("支付中");
    }
}

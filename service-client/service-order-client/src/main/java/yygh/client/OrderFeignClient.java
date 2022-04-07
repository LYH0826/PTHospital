package yygh.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import yygh.vo.order.OrderCountQueryVo;

import java.util.Map;

@FeignClient(value = "service-order", path = "/admin/order/orderInfo")
@Repository
public interface OrderFeignClient {

    //获取订单统计数据
    @PostMapping("/inner/getCountMap")
    Map<String, Object> getCountMap(@RequestBody OrderCountQueryVo orderCountQueryVo);

}

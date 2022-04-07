package yygh.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import yygh.model.order.PaymentInfo;

@Mapper
public interface PaymentInfoMapper extends BaseMapper<PaymentInfo> {
}

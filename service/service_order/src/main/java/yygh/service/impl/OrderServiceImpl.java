package yygh.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import yygh.client.HospitalFeignClient;
import yygh.client.PatientFeignClient;
import yygh.constant.MqConst;
import yygh.enums.OrderStatusEnum;
import yygh.exception.YyghException;
import yygh.helper.HttpRequestHelper;
import yygh.mapper.OrderInfoMapper;
import yygh.model.order.OrderInfo;
import yygh.model.user.Patient;
import yygh.result.ResultCodeEnum;
import yygh.service.OrderService;
import yygh.service.RabbitService;
import yygh.vo.hosp.ScheduleOrderVo;
import yygh.vo.msm.MsmVo;
import yygh.vo.order.OrderMqVo;
import yygh.vo.order.OrderQueryVo;
import yygh.vo.order.SignInfoVo;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Service
public class OrderServiceImpl
        extends ServiceImpl<OrderInfoMapper, OrderInfo>
        implements OrderService {

    @Autowired
    private PatientFeignClient patientFeignClient;

    @Autowired
    private HospitalFeignClient hospitalFeignClient;

    @Autowired
    private RabbitService rabbitService;

    //保存订单
    @Override
    public Long saveOrder(String scheduleId, Long patientId) {
        Patient patient = patientFeignClient.getPatientOrder(patientId);
        if (null == patient){
            throw new YyghException(ResultCodeEnum.PARAM_ERROR);
        }

        //? cmn nullpointex
        ScheduleOrderVo scheduleOrderVo =
                hospitalFeignClient.getScheduleOrderVo(scheduleId);
        if (null == scheduleOrderVo){
            throw new YyghException(ResultCodeEnum.PARAM_ERROR);
        }

        //判断当前时间是否还可以预约
        if(new DateTime(scheduleOrderVo.getStartTime()).isAfterNow()
                || new DateTime(scheduleOrderVo.getEndTime()).isBeforeNow()) {
            throw new YyghException(ResultCodeEnum.TIME_NO);
        }

        //获取签名信息
        SignInfoVo signInfoVo =
                hospitalFeignClient.getSignInfoVo(scheduleOrderVo.getHoscode());

        //将scheduleOrderVo中的信息添加到订单列表
        OrderInfo orderInfo = new OrderInfo();
        BeanUtils.copyProperties(scheduleOrderVo, orderInfo);

        //向orderInfo设置其他数据
        //以时间戳设置订单交易号
        String outTradeNo =
                System.currentTimeMillis() + "" + new Random().nextInt(100);
        orderInfo.setOutTradeNo(outTradeNo);
        orderInfo.setScheduleId(scheduleOrderVo.getHosScheduleId());
        orderInfo.setUserId(patient.getUserId());
        orderInfo.setPatientId(patientId);
        orderInfo.setPatientName(patient.getName());
        orderInfo.setPatientPhone(patient.getPhone());
        //设置订单状态为预约成功，待支付
        orderInfo.setOrderStatus(OrderStatusEnum.UNPAID.getStatus());
        //加入数据库
        baseMapper.insert(orderInfo);

        //调用医院接口，实现预约挂号操作
        //设置调用医院接口需要参数，参数放到map集合
        HashMap<String, Object> paramMap = new HashMap<>();
        //相应排班信息
        paramMap.put("hoscode",orderInfo.getHoscode());
        paramMap.put("depcode",orderInfo.getDepcode());
        paramMap.put("hosScheduleId",orderInfo.getScheduleId());
        paramMap.put("reserveDate",new DateTime(orderInfo.getReserveDate()).toString("yyyy-MM-dd"));
        paramMap.put("reserveTime", orderInfo.getReserveTime());
        paramMap.put("amount",orderInfo.getAmount());
        //就诊人信息
        paramMap.put("name", patient.getName());
        paramMap.put("certificatesType",patient.getCertificatesType());
        paramMap.put("certificatesNo", patient.getCertificatesNo());
        paramMap.put("sex",patient.getSex());
        paramMap.put("birthdate", patient.getBirthdate());
        paramMap.put("phone",patient.getPhone());
        paramMap.put("isMarry", patient.getIsMarry());
        paramMap.put("provinceCode",patient.getProvinceCode());
        paramMap.put("cityCode", patient.getCityCode());
        paramMap.put("districtCode",patient.getDistrictCode());
        paramMap.put("address",patient.getAddress());
        //联系人信息
        paramMap.put("contactsName",patient.getContactsName());
        paramMap.put("contactsCertificatesType", patient.getContactsCertificatesType());
        paramMap.put("contactsCertificatesNo",patient.getContactsCertificatesNo());
        paramMap.put("contactsPhone",patient.getContactsPhone());
        //获取时间戳
        paramMap.put("timestamp", HttpRequestHelper.getTimestamp());

        String sign = HttpRequestHelper.getSign(paramMap, signInfoVo.getSignKey());
        paramMap.put("sign", sign);

        //请求医院系统接口
        JSONObject result = HttpRequestHelper
                .sendRequest(paramMap, "http://localhost:9998/order/submitOrder");

        if (result.getInteger("code") == 200){
            //从Json中取值
            JSONObject jsonObject = result.getJSONObject("data");
            //预约记录唯一标识（医院预约记录主键）
            String hosRecordId = jsonObject.getString("hosRecordId");
            //预约序号
            Integer number = jsonObject.getInteger("number");
            //取号时间
            String fetchTime = jsonObject.getString("fetchTime");
            //取号地址
            String fetchAddress = jsonObject.getString("fetchAddress");

            //更新订单
            //预约记录唯一标识（医院预约记录主键）
            orderInfo.setHosRecordId(hosRecordId);
            //预约号序
            orderInfo.setNumber(number);
            //取号时间
            orderInfo.setFetchTime(fetchTime);
            //取号地点
            orderInfo.setFetchAddress(fetchAddress);
            //存入数据库
            baseMapper.updateById(orderInfo);

            //排班可预约数
            Integer reservedNumber = jsonObject.getInteger("reservedNumber");
            //排班剩余预约数
            Integer availableNumber = jsonObject.getInteger("availableNumber");
            //发送mq消息，号源更新和短信通知
            OrderMqVo orderMqVo = new OrderMqVo();
            orderMqVo.setScheduleId(scheduleId);
            orderMqVo.setReservedNumber(reservedNumber);
            orderMqVo.setAvailableNumber(availableNumber);

            //短信提示
            MsmVo msmVo = new MsmVo();
            msmVo.setPhone(orderInfo.getPatientPhone());
            //设置短信模版Code
            msmVo.setTemplateCode("TPL_0000");
            String reserveDate =
                    new DateTime(orderInfo.getReserveDate()).toString("yyyy-MM-dd")
                            + (orderInfo.getReserveTime()==0 ? "上午": "下午");
            Map<String,Object> param = new HashMap<String,Object>(){{
                put("title", orderInfo.getHosname()+"|"+orderInfo.getDepname()+"|"+orderInfo.getTitle());
                put("amount", orderInfo.getAmount());
                put("reserveDate", reserveDate);
                put("name", orderInfo.getPatientName());
                put("quitTime", new DateTime(orderInfo.getQuitTime()).toString("yyyy-MM-dd HH:mm"));
            }};
            msmVo.setParam(param);

            orderMqVo.setMsmVo(msmVo);
            rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_ORDER, MqConst.ROUTING_ORDER, orderMqVo);

        }else {
            //抛出异常，返回失败
            throw new YyghException(
                    result.getString("message"), ResultCodeEnum.FAIL.getCode()
            );
        }
        return orderInfo.getId();
    }

    //订单列表（条件查询带分页）
    @Override
    public IPage<OrderInfo> selectPage(Page<OrderInfo> pageParam, OrderQueryVo orderQueryVo) {
        //orderQueryVo获取条件值
        String name = orderQueryVo.getKeyword(); //医院名称
        Long patientId = orderQueryVo.getPatientId(); //就诊人名称
        String orderStatus = orderQueryVo.getOrderStatus(); //订单状态
        String reserveDate = orderQueryVo.getReserveDate();//安排时间
        String createTimeBegin = orderQueryVo.getCreateTimeBegin();//查询的开始时间
        String createTimeEnd = orderQueryVo.getCreateTimeEnd();//查询的结束时间

        //对条件值进行非空判断
        QueryWrapper<OrderInfo> wrapper = new QueryWrapper<>();
        if(!StringUtils.isEmpty(name)) {
            wrapper.like("hosname",name);
        }
        if(!StringUtils.isEmpty(patientId)) {
            wrapper.eq("patient_id",patientId);
        }
        if(!StringUtils.isEmpty(orderStatus)) {
            wrapper.eq("order_status",orderStatus);
        }
        if(!StringUtils.isEmpty(reserveDate)) {
            wrapper.ge("reserve_date",reserveDate);
        }
        if(!StringUtils.isEmpty(createTimeBegin)) {
            wrapper.ge("create_time",createTimeBegin);
        }
        if(!StringUtils.isEmpty(createTimeEnd)) {
            wrapper.le("create_time",createTimeEnd);
        }
        //调用mapper的方法
        IPage<OrderInfo> pages = baseMapper.selectPage(pageParam, wrapper);
        //编号变成对应值封装
        pages.getRecords().stream().forEach(item -> {
            this.packOrderInfo(item);
        });
        return pages;
    }

    //根据订单id查询订单详情
    @Override
    public OrderInfo getOrder(String orderId) {
        OrderInfo orderInfo = baseMapper.selectById(orderId);
        this.packOrderInfo(orderInfo);
        return orderInfo;
    }

    private void packOrderInfo(OrderInfo orderInfo) {
        orderInfo.getParam().put("orderStatusString", OrderStatusEnum.getStatusNameByStatus(orderInfo.getOrderStatus()));
    }

}

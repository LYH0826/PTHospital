package yygh.service.impl;

import org.apache.http.HttpResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import yygh.service.SmsService;
import yygh.utils.HttpUtils;
import yygh.vo.msm.MsmVo;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class SmsServiceImpl implements SmsService {

    @Value("${aliyun.sms.appcode}")
    private String appcode;

    @Value("${aliyun.sms.path}")
    private String path;

    @Value("${aliyun.sms.host}")
    private String host;

    @Value("${aliyun.sms.method}")
    private String method;

    //发送短信验证码，并判断是否发送成功
    @Override
    public boolean send(String phone, String code) {

        //判断手机号是否为空
        if(StringUtils.isEmpty(phone)) {
            return false;
        }
        Map<String, String> headers = new HashMap<String, String>();
        //最后在header中的格式(中间是英文空格)为Authorization:APPCODE 83359fd73fe94948385f570e3c139105
        headers.put("Authorization", "APPCODE " + appcode);
        //根据API的要求，定义相对应的Content-Type
        headers.put("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        Map<String, String> querys = new HashMap<String, String>();
        Map<String, String> bodys = new HashMap<String, String>();
        bodys.put("content", "code:" + code);
        bodys.put("phone_number", phone);
        bodys.put("template_id", "TPL_0000");

        try {
            HttpResponse response = HttpUtils.doPost(host, path, method, headers, querys, bodys);
            System.out.println("电话号码为" + phone + "的用户短信验证码已发送，验证码为" + code);
            //获取response的body
            //System.out.println(EntityUtils.toString(response.getEntity()));
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    //待修改
    //发送短信提醒预约成功
    @Override
    public boolean sendInfo(String phone, Map<String, Object> param) {

        //判断手机号是否为空
        if(StringUtils.isEmpty(phone)) {
            return false;
        }
        Map<String, String> headers = new HashMap<String, String>();
        //最后在header中的格式(中间是英文空格)为Authorization:APPCODE 83359fd73fe94948385f570e3c139105
        headers.put("Authorization", "APPCODE " + appcode);
        //根据API的要求，定义相对应的Content-Type
        headers.put("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        Map<String, String> querys = new HashMap<String, String>();
        Map<String, String> bodys = new HashMap<String, String>();

        //强转方式需注意
        //医生职称
        //String title = (String) param.get("title");
        //医事服务费
        //Integer amount = (Integer) param.get("amount");
        //安排日期
        //Date reserveDate = (Date) param.get("reserveDate");
        //就诊人名称
        String name = (String) param.get("name");
        //退号时间
        //Date quitTime = (Date) param.get("quitTime");

        //String message = "预约成功! 就诊人" + name +"所预约的" + title
        //        + "安排日期为" + reserveDate + ", 请支付服务费" + amount
        //        + "并在预约时间前到场, 若无法到场请在" + quitTime + "前退号, 祝您身体健康";
        String message = "就诊人" + name + "预约成功!";
//预约成功!就诊人林毅恒所预约的莆田市第一医院多发性门诊啊把啊把安倍仪式安排日期为2018-03-01上午,服务费为100.00
        bodys.put("content", "code:" + message);
        bodys.put("phone_number", phone);
        bodys.put("template_id", "TPL_0000");
        try {
            HttpResponse response = HttpUtils.doPost(host, path, method, headers, querys, bodys);
            System.out.println("电话号码为" + phone + "的" + name + "短信内容已经发送成功！");
            //获取response的body
            //System.out.println(EntityUtils.toString(response.getEntity()));
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    //mq发送短信封装
    @Override
    public boolean send(MsmVo msmVo) {
        if(!StringUtils.isEmpty(msmVo.getPhone())) {
            return this.sendInfo(msmVo.getPhone(),msmVo.getParam());
        }
        return false;
    }

    //测试
    public static void main(String[] args) {
        String host = "https://dfsns.market.alicloudapi.com";
        String path = "/data/send_sms";
        String method = "POST";
        String appcode = "3546ff78ad4549b9a3dab8b453a2c9d6";
        Map<String, String> headers = new HashMap<String, String>();
        //最后在header中的格式(中间是英文空格)为Authorization:APPCODE 83359fd73fe94948385f570e3c139105
        headers.put("Authorization", "APPCODE " + appcode);
        //根据API的要求，定义相对应的Content-Type
        headers.put("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        Map<String, String> querys = new HashMap<String, String>();
        Map<String, String> bodys = new HashMap<String, String>();
        bodys.put("content", "code:" + "芜湖");
        bodys.put("phone_number", "18065665105");
        bodys.put("template_id", "TPL_0001");


        try {
            HttpResponse response = HttpUtils.doPost(host, path, method, headers, querys, bodys);
            System.out.println(response.toString());
            //获取response的body
            //System.out.println(EntityUtils.toString(response.getEntity()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

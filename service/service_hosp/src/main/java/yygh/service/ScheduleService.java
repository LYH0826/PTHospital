package yygh.service;

import org.springframework.data.domain.Page;
import yygh.model.hosp.Schedule;
import yygh.vo.hosp.ScheduleOrderVo;
import yygh.vo.hosp.ScheduleQueryVo;

import java.util.List;
import java.util.Map;

public interface ScheduleService {
    //上传排班
    void save(Map<String, Object> parameterMap);

    //多条件分页查询
    Page<Schedule> getPage(Integer pageNum,
                              Integer pageSize,
                              ScheduleQueryVo scheduleQueryVo
    );

    //删除排班
    void delete(String hoscode, String hosScheduleId);

    //根据、医院编号、科室编号、分页、查询排班规则数据
    Map<String, Object> getScheduleRulePage
    (long page, long limit, String hoscode, String depcode);

    //根据医院编号、科室编号、工作日期，查询排版详细信息
    List<Schedule>
    getScheduleDetail(String hoscode, String depcode, String workDate);

    //获取排班可预约日期数据
    Map<String, Object> getBookingScheduleRule
    (int page, int limit, String hoscode, String depcode);

    //根据id获取排班
    Schedule getById(String id);

    //根据排班id获取预约下单数据
    ScheduleOrderVo getScheduleOrderVo(String scheduleId);

    //更新排班
    void update(Schedule schedule);

    //根据hoscode,hosScheduleId，查询数据库对应数据
    Schedule getScheduleByHoscodeAndHosScheduleId(String hoscode, String scheduleId);
}

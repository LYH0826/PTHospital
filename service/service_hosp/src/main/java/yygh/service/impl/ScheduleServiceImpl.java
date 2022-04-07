package yygh.service.impl;

//import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import yygh.exception.YyghException;
import yygh.mapper.ScheduleMapper;
import yygh.model.hosp.BookingRule;
import yygh.model.hosp.Department;
import yygh.model.hosp.Hospital;
import yygh.model.hosp.Schedule;
import yygh.repository.ScheduleRepository;
import yygh.result.ResultCodeEnum;
import yygh.service.DepartmentService;
import yygh.service.HospitalService;
import yygh.service.ScheduleService;
import yygh.utils.HospUtils;
import yygh.vo.hosp.BookingScheduleRuleVo;
import yygh.vo.hosp.ScheduleOrderVo;
import yygh.vo.hosp.ScheduleQueryVo;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ScheduleServiceImpl
        extends ServiceImpl<ScheduleMapper,Schedule>
        implements ScheduleService {

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private HospitalService hospitalService;

    @Autowired
    private DepartmentService departmentService;

    //上传排班
    @Override
    public void save(Map<String, Object> parameterMap) {
        String jsonString = JSONObject.toJSONString(parameterMap);
        Schedule schedule = JSONObject.parseObject(jsonString, Schedule.class);

        Schedule targetSchedule =
                scheduleRepository.getScheduleByHoscodeAndHosScheduleId(
                        schedule.getHoscode(),
                        schedule.getHosScheduleId()
                );

        if (targetSchedule != null) {
            targetSchedule.setUpdateTime(new Date());
            targetSchedule.setIsDeleted(0);
            //排班状态（-1：停诊 0：停约 1：可约）
            targetSchedule.setStatus(1);
            scheduleRepository.save(targetSchedule);
        } else {
            schedule.setCreateTime(new Date());
            schedule.setUpdateTime(new Date());
            schedule.setIsDeleted(0);
            //排班状态（-1：停诊 0：停约 1：可约）
            schedule.setStatus(1);
            scheduleRepository.save(schedule);
        }

    }

    //多条件分页查询
    @Override
    public Page<Schedule> getPage
    (Integer pageNum, Integer pageSize, ScheduleQueryVo scheduleQueryVo) {
        //构建排序规则
        Sort sort = Sort.by(Sort.Direction.DESC, "createTime");
        //构建分页规则，其中0为第一页
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, sort);
        //构建匹配规则: 模糊查询并忽略大小写
        ExampleMatcher matching = ExampleMatcher.matching();
        matching.withStringMatcher(ExampleMatcher.StringMatcher.CONTAINING)
                .withIgnoreCase(true);

        //将条件查询类中的数据复制到实体类
        Schedule schedule = new Schedule();
        BeanUtils.copyProperties(scheduleQueryVo, schedule);
        schedule.setIsDeleted(0);
        schedule.setStatus(1);

        Example<Schedule> example = Example.of(schedule, matching);

        return scheduleRepository.findAll(example, pageable);
    }

    //删除排班
    @Override
    public void delete(String hoscode, String hosScheduleId) {
        Schedule schedule =
                scheduleRepository.getScheduleByHoscodeAndHosScheduleId
                        (hoscode, hosScheduleId);
        if (schedule != null) {
            scheduleRepository.delete(schedule);
        }

    }

    //根据、医院编号、科室编号、分页、查询排班规则数据
    @Override
    public Map<String, Object>
    getScheduleRulePage(long page, long limit, String hoscode, String depcode) {
        //1 根据医院编号 和 科室编号 查询
        Criteria criteria = Criteria.where("hoscode").is(hoscode).and("depcode").is(depcode);

        //2 根据工作日workDate期进行分组
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(criteria),//匹配条件
                Aggregation.group("workDate")//分组字段
                        .first("workDate").as("workDate")//命名
                        //3 统计号源数量
                        .count().as("docCount")
                        //求和reservedNumber(科室可预约数)字段
                        .sum("reservedNumber").as("reservedNumber")
                        //求和availableNumber(科室剩余预约数)字段
                        .sum("availableNumber").as("availableNumber"),
                //排序
                Aggregation.sort(Sort.Direction.ASC, "workDate"),
                //4 实现分页
                Aggregation.skip((page - 1) * limit),
                Aggregation.limit(limit)
        );
        //调用方法封装到List集合中，后续上传该集合
        AggregationResults<BookingScheduleRuleVo> aggResults =
                mongoTemplate.aggregate(agg, Schedule.class, BookingScheduleRuleVo.class);
        List<BookingScheduleRuleVo> bookingScheduleRuleVoList = aggResults.getMappedResults();

        //分组查询的总记录
        Aggregation totalAgg = Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.group("workDate")
        );
        AggregationResults<BookingScheduleRuleVo> totalAggResults =
                mongoTemplate.aggregate(totalAgg, Schedule.class, BookingScheduleRuleVo.class);
        //获取总记录数
        int total = totalAggResults.getMappedResults().size();

        //把日期对应星期获取
        for (BookingScheduleRuleVo bookingScheduleRuleVo : bookingScheduleRuleVoList) {
            Date workDate = bookingScheduleRuleVo.getWorkDate();
            String dayOfWeek = HospUtils.getDayOfWeek(new DateTime(workDate));
            //设置星期
            bookingScheduleRuleVo.setDayOfWeek(dayOfWeek);
        }

        //设置最终数据，进行返回
        Map<String, Object> result = new HashMap<>();
        result.put("bookingScheduleRuleList", bookingScheduleRuleVoList);
        result.put("total", total);

        //获取医院名称
        String hosName = hospitalService.getByHoscode(hoscode).getHosname();
        //其他基础数据
        Map<String, String> baseMap = new HashMap<>();
        baseMap.put("hosname", hosName);
        result.put("baseMap", baseMap);

        return result;
    }

    //获取排班信息
    @Override
    public List<Schedule>
    getScheduleDetail(String hoscode, String depcode, String workDate) {
        Date date = new DateTime(workDate).toDate();
        List<Schedule> list =
                scheduleRepository.findScheduleByHoscodeAndDepcodeAndWorkDate(
                        hoscode,
                        depcode,
                        date
                );
        list.stream().forEach(schedule -> {
            this.packageSchedule(schedule);
        });

        return list;
    }

    //获取可预约的排班数据
    @Override
    public Map<String, Object> getBookingScheduleRule(int page, int limit, String hoscode, String depcode) {
        Map<String,Object> result = new HashMap<>();
        //获取预约规则
        //根据医院编号获取预约规则
        Hospital hospital = hospitalService.getByHoscode(hoscode);
        if(hospital == null) {
            throw new YyghException(ResultCodeEnum.DATA_ERROR);
        }
        BookingRule bookingRule = hospital.getBookingRule();

        //获取可预约日期的数据（分页）
        IPage<Date> iPage = this.getListDate(page,limit,bookingRule);
        //当前可预约日期
        List<Date> dateList = iPage.getRecords();

        //获取可预约日期里面科室的剩余预约数
        Criteria criteria = Criteria.where("hoscode").is(hoscode).and("depcode").is(depcode)
                .and("workDate").in(dateList);

        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.group("workDate").first("workDate").as("workDate")
                        .count().as("docCount")
                        .sum("availableNumber").as("availableNumber")
                        .sum("reservedNumber").as("reservedNumber")
        );
        AggregationResults<BookingScheduleRuleVo> aggregateResult =
                mongoTemplate.aggregate(agg, Schedule.class, BookingScheduleRuleVo.class);
        List<BookingScheduleRuleVo> scheduleVoList = aggregateResult.getMappedResults();

        //合并数据  map集合 key日期  value预约规则和剩余数量等
        Map<Date, BookingScheduleRuleVo> scheduleVoMap = new HashMap<>();
        if(!CollectionUtils.isEmpty(scheduleVoList)) {
            scheduleVoMap = scheduleVoList.stream().
                    collect(
                            Collectors.toMap(BookingScheduleRuleVo::getWorkDate,
                                    BookingScheduleRuleVo -> BookingScheduleRuleVo));
        }

        //获取可预约排班规则
        List<BookingScheduleRuleVo> bookingScheduleRuleVoList = new ArrayList<>();
        for(int i=0,len=dateList.size();i<len;i++) {
            Date date = dateList.get(i);
            //从map集合根据key日期获取value值
            BookingScheduleRuleVo bookingScheduleRuleVo = scheduleVoMap.get(date);
            //如果当天没有排班医生
            if(bookingScheduleRuleVo == null) {
                bookingScheduleRuleVo = new BookingScheduleRuleVo();
                //就诊医生人数
                bookingScheduleRuleVo.setDocCount(0);
                //科室剩余预约数  -1表示无号
                bookingScheduleRuleVo.setAvailableNumber(-1);
            }
            bookingScheduleRuleVo.setWorkDate(date);
            bookingScheduleRuleVo.setWorkDateMd(date);
            //计算当前预约日期对应星期
            String dayOfWeek = HospUtils.getDayOfWeek(new DateTime(date));
            bookingScheduleRuleVo.setDayOfWeek(dayOfWeek);

            //最后一页最后一条记录为即将预约   状态 0：正常 1：即将放号 -1：当天已停止挂号
            if(i == len-1 && page == iPage.getPages()) {
                bookingScheduleRuleVo.setStatus(1);
            } else {
                bookingScheduleRuleVo.setStatus(0);
            }
            //当天预约如果过了停号时间， 不能预约
            if(i == 0 && page == 1) {
                DateTime stopTime = HospUtils.getDateTime(new Date(), bookingRule.getStopTime());
                if(stopTime.isBeforeNow()) {
                    //停止预约
                    bookingScheduleRuleVo.setStatus(-1);
                }
            }
            bookingScheduleRuleVoList.add(bookingScheduleRuleVo);
        }

        //可预约日期规则数据
        result.put("bookingScheduleList", bookingScheduleRuleVoList);
        result.put("total", iPage.getTotal());

        //其他基础数据
        Map<String, String> baseMap = new HashMap<>();
        //医院名称
        baseMap.put("hosname", hospitalService.getByHoscode(hoscode).getHosname());
        //科室
        Department department =departmentService.getDepartment(hoscode, depcode);
        //大科室名称
        baseMap.put("bigname", department.getBigname());
        //科室名称
        baseMap.put("depname", department.getDepname());
        //月
        baseMap.put("workDateString", new DateTime().toString("yyyy年MM月"));
        //放号时间
        baseMap.put("releaseTime", bookingRule.getReleaseTime());
        //停号时间
        baseMap.put("stopTime", bookingRule.getStopTime());
        result.put("baseMap", baseMap);
        return result;
    }

    //根据id获取排班
    @Override
    public Schedule getById(String id) {
        Schedule schedule = scheduleRepository.findById(id).get();
        this.packageSchedule(schedule);
        return schedule;
    }



    //根据排班id获取预约下单数据
    @Override
    public ScheduleOrderVo getScheduleOrderVo(String scheduleId) {
        //排班信息
        Schedule schedule = scheduleRepository.findById(scheduleId).get();
        if (null == schedule){
            throw new YyghException(ResultCodeEnum.PARAM_ERROR);
        }
        //通过医院编号获取预约规则信息
        String hoscode = schedule.getHoscode();
        Hospital hospital = hospitalService.getByHoscode(hoscode);
        if (null == hospital){
            throw new YyghException(ResultCodeEnum.DATA_ERROR);
        }
        BookingRule bookingRule = hospital.getBookingRule();
        if (null == bookingRule){
            throw new YyghException(ResultCodeEnum.PARAM_ERROR);
        }

        //设置值
        ScheduleOrderVo scheduleOrderVo = new ScheduleOrderVo();
        String depcode = schedule.getDepcode();
        scheduleOrderVo.setHoscode(hoscode);
        scheduleOrderVo.setHosname(hospitalService.getByHoscode(hoscode).getHosname());
        scheduleOrderVo.setDepcode(depcode);
        scheduleOrderVo.setDepname(departmentService.getDeptName(hoscode, depcode));
        scheduleOrderVo.setHosScheduleId(schedule.getHosScheduleId());
        scheduleOrderVo.setAvailableNumber(schedule.getAvailableNumber());
        scheduleOrderVo.setTitle(schedule.getTitle());
        scheduleOrderVo.setReserveDate(schedule.getWorkDate());
        scheduleOrderVo.setReserveTime(schedule.getWorkTime());
        scheduleOrderVo.setAmount(schedule.getAmount());

        //设置退号截止天数
        //如：就诊前一天为-1，当天为0
        int quitDay = bookingRule.getQuitDay();
        DateTime quitTime =
                HospUtils.getDateTime(new DateTime(schedule.getWorkDate()).plusDays(quitDay).toDate(), bookingRule.getQuitTime());
        scheduleOrderVo.setQuitTime(quitTime.toDate());

        //预约开始时间
        DateTime startTime =
                HospUtils.getDateTime(new Date(), bookingRule.getReleaseTime());
        scheduleOrderVo.setStartTime(startTime.toDate());

        //预约截止时间
        DateTime endTime =
                HospUtils.getDateTime(new DateTime().plusDays(bookingRule.getCycle()).toDate(), bookingRule.getStopTime());
        scheduleOrderVo.setEndTime(endTime.toDate());

        //当天停止挂号时间
        DateTime stopTime =
                HospUtils.getDateTime(new Date(), bookingRule.getStopTime());
        scheduleOrderVo.setStopTime(stopTime.toDate());
        return scheduleOrderVo;
    }

    //更新排班
    @Override
    public void update(Schedule schedule) {
        schedule.setUpdateTime(new Date());
        //在mongodb中，主键一致便是更新
        scheduleRepository.save(schedule);
    }

    //根据hoscode,hosScheduleId，查询数据库对应数据
    @Override
    public Schedule getScheduleByHoscodeAndHosScheduleId(String hoscode, String scheduleId) {
        Schedule schedule = scheduleRepository.getScheduleByHoscodeAndHosScheduleId(hoscode, scheduleId);
        this.packageSchedule(schedule);
        return schedule;
    }


//==========================================================================================
//==========================================================================================
//==========================================================================================



    //封装其他值：医院名称，科室名称，日期对应的星期
    private void packageSchedule(Schedule schedule) {
        Map<String, Object> map = schedule.getParam();
        //获取医院名称
        String hospName =
                hospitalService.getByHoscode(schedule.getHoscode()).getHosname();
        //根据医院编号、科室编号，获取科室名称
        String deptName =
                departmentService.getDeptName(
                        schedule.getHoscode(),
                        schedule.getDepcode()
                );
        //获取日期对应的星期
        String week = HospUtils.getDayOfWeek(new DateTime(schedule.getWorkDate()));

        map.put("hospName", hospName);
        map.put("deptName", deptName);
        map.put("week", week);
    }

    //获取可预约日期的分页数据
    private IPage<Date> getListDate(int page, int limit, BookingRule bookingRule) {
        //获取当天放号时间，模版为：[年-月-日 小时:分钟]
        DateTime releaseTime =
                HospUtils.getDateTime(new Date(), bookingRule.getReleaseTime());
        //获取预约周期
        Integer cycle = bookingRule.getCycle();
        //如果当天放号时间已经过去了，预约周期从下一天开始计算，周期+1
        if (releaseTime.isBeforeNow()) {
            cycle += 1;
        }

        //获取可预约所有日期，最后一天显示即将放号
        List<Date> dateList = new ArrayList<>();
        for (int i = 0; i < cycle; i++) {
            DateTime curDateTime = new DateTime().plusDays(i);
            String dateString = curDateTime.toString("yyyy-MM-dd");
            dateList.add(new DateTime(dateString).toDate());
        }

        //因为预约周期不同的，每页显示日期最多7天数据，超过7天分页
        List<Date> pageDateList = new ArrayList<>();
        int start = (page - 1) * limit;
        int end = (page - 1) * limit + limit;
        //如果可以显示数据小于7，则直接显示，无需分页
        if (end > dateList.size()) {
            end = dateList.size();
        }
        for (int i = start; i < end; i++) {
            pageDateList.add(dateList.get(i));
        }
        //如果可以显示数据大于7，进行分页
        IPage<Date> datePage =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, 7, dateList.size());
        datePage.setRecords(pageDateList);
        return datePage;
    }

}

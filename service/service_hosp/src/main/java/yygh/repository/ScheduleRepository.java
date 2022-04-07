package yygh.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import yygh.model.hosp.Schedule;

import java.util.Date;
import java.util.List;

@Repository
public interface ScheduleRepository extends MongoRepository<Schedule,String> {
    //根据hoscode,hosScheduleId，查询数据库对应数据
    //命名符合SpringData的规范
    Schedule getScheduleByHoscodeAndHosScheduleId(String hoscode, String hosScheduleId);

    //根据医院编号、科室编号、工作日期，查询排班详细信息
    List<Schedule>
    findScheduleByHoscodeAndDepcodeAndWorkDate(
            String hoscode,
            String depcode,
            Date toDate
    );

}

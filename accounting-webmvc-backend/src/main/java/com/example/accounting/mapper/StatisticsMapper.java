package com.example.accounting.mapper;

import com.example.accounting.dto.CategoryStat;
import com.example.accounting.dto.TimePeriodStat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StatisticsMapper {
    List<TimePeriodStat> weeklyStats(@Param("userId") Long userId,
                                     @Param("ledgerId") Long ledgerId,
                                     @Param("startDate") String startDate,
                                     @Param("endDate") String endDate);

    List<TimePeriodStat> monthlyStats(@Param("userId") Long userId,
                                      @Param("ledgerId") Long ledgerId,
                                      @Param("startDate") String startDate,
                                      @Param("endDate") String endDate);

    List<TimePeriodStat> yearlyStats(@Param("userId") Long userId,
                                     @Param("ledgerId") Long ledgerId,
                                     @Param("startDate") String startDate,
                                     @Param("endDate") String endDate);

    List<CategoryStat> categoryStats(@Param("userId") Long userId,
                                     @Param("ledgerId") Long ledgerId,
                                     @Param("type") Integer type,
                                     @Param("startDate") String startDate,
                                     @Param("endDate") String endDate);
}

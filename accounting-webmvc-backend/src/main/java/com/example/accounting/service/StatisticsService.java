package com.example.accounting.service;

import com.example.accounting.dto.CategoryStat;
import com.example.accounting.dto.StatisticsResponse;
import com.example.accounting.dto.TimePeriodStat;
import com.example.accounting.mapper.StatisticsMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class StatisticsService {

    @Autowired
    private StatisticsMapper statisticsMapper;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    public StatisticsResponse getWeeklyStats(Long userId, Long ledgerId) {
        String ledgerKey = ledgerId != null ? String.valueOf(ledgerId) : "all";
        String key = "statistics:weekly:" + userId + ":" + ledgerKey;
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, StatisticsResponse.class);
            } catch (Exception e) {
                // ignore
            }
        }
        StatisticsResponse response = calculateWeeklyStats(userId, ledgerId);
        cacheResponse(key, response);
        return response;
    }

    private StatisticsResponse calculateWeeklyStats(Long userId, Long ledgerId) {
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        LocalDate sunday = today.with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY));

        List<TimePeriodStat> dbList = statisticsMapper.weeklyStats(
                userId,
                ledgerId,
                monday.format(DateTimeFormatter.ISO_LOCAL_DATE),
                sunday.format(DateTimeFormatter.ISO_LOCAL_DATE)
        );

        List<TimePeriodStat> fullList = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = monday.plusDays(i);
            String period = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
            fullList.add(TimePeriodStat.builder()
                    .period(period)
                    .income(BigDecimal.ZERO)
                    .expense(BigDecimal.ZERO)
                    .build());
        }

        Map<String, TimePeriodStat> dbMap = dbList.stream()
                .collect(Collectors.toMap(TimePeriodStat::getPeriod, s -> s, (a, b) -> a));

        for (TimePeriodStat stat : fullList) {
            TimePeriodStat dbStat = dbMap.get(stat.getPeriod());
            if (dbStat != null) {
                stat.setIncome(dbStat.getIncome() != null ? dbStat.getIncome() : BigDecimal.ZERO);
                stat.setExpense(dbStat.getExpense() != null ? dbStat.getExpense() : BigDecimal.ZERO);
            }
        }

        BigDecimal totalIncome = fullList.stream()
                .map(TimePeriodStat::getIncome)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalExpense = fullList.stream()
                .map(TimePeriodStat::getExpense)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal balance = totalIncome.subtract(totalExpense);

        return StatisticsResponse.builder()
                .periodStats(fullList)
                .totalIncome(totalIncome)
                .totalExpense(totalExpense)
                .balance(balance)
                .build();
    }

    public StatisticsResponse getMonthlyStats(Long userId, Long ledgerId, int year, int month) {
        String ledgerKey = ledgerId != null ? String.valueOf(ledgerId) : "all";
        String key = "statistics:monthly:" + userId + ":" + year + ":" + month + ":" + ledgerKey;
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, StatisticsResponse.class);
            } catch (Exception e) {
                // ignore
            }
        }
        StatisticsResponse response = calculateMonthlyStats(userId, ledgerId, year, month);
        cacheResponse(key, response);
        return response;
    }

    private StatisticsResponse calculateMonthlyStats(Long userId, Long ledgerId, int year, int month) {
        LocalDate startOfMonth = LocalDate.of(year, month, 1);
        LocalDate endOfMonth = startOfMonth.with(TemporalAdjusters.lastDayOfMonth());

        List<TimePeriodStat> dbList = statisticsMapper.monthlyStats(
                userId,
                ledgerId,
                startOfMonth.format(DateTimeFormatter.ISO_LOCAL_DATE),
                endOfMonth.format(DateTimeFormatter.ISO_LOCAL_DATE)
        );

        List<TimePeriodStat> fullList = new ArrayList<>();
        for (int i = 1; i <= endOfMonth.getDayOfMonth(); i++) {
            String day = String.format("%04d-%02d-%02d", year, month, i);
            fullList.add(TimePeriodStat.builder()
                    .period(day)
                    .income(BigDecimal.ZERO)
                    .expense(BigDecimal.ZERO)
                    .build());
        }

        Map<String, TimePeriodStat> dbMap = dbList.stream()
                .collect(Collectors.toMap(TimePeriodStat::getPeriod, s -> s, (a, b) -> a));

        for (TimePeriodStat stat : fullList) {
            TimePeriodStat dbStat = dbMap.get(stat.getPeriod());
            if (dbStat != null) {
                stat.setIncome(dbStat.getIncome() != null ? dbStat.getIncome() : BigDecimal.ZERO);
                stat.setExpense(dbStat.getExpense() != null ? dbStat.getExpense() : BigDecimal.ZERO);
            }
        }

        BigDecimal totalIncome = fullList.stream()
                .map(TimePeriodStat::getIncome)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalExpense = fullList.stream()
                .map(TimePeriodStat::getExpense)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal balance = totalIncome.subtract(totalExpense);

        return StatisticsResponse.builder()
                .periodStats(fullList)
                .totalIncome(totalIncome)
                .totalExpense(totalExpense)
                .balance(balance)
                .build();
    }

    public StatisticsResponse getYearlyStats(Long userId, Long ledgerId, int year) {
        String ledgerKey = ledgerId != null ? String.valueOf(ledgerId) : "all";
        String key = "statistics:yearly:" + userId + ":" + year + ":" + ledgerKey;
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, StatisticsResponse.class);
            } catch (Exception e) {
                // ignore
            }
        }
        StatisticsResponse response = calculateYearlyStats(userId, ledgerId, year);
        cacheResponse(key, response);
        return response;
    }

    private StatisticsResponse calculateYearlyStats(Long userId, Long ledgerId, int year) {
        LocalDate startOfYear = LocalDate.of(year, 1, 1);
        LocalDate endOfYear = LocalDate.of(year, 12, 31);

        List<TimePeriodStat> dbList = statisticsMapper.yearlyStats(
                userId,
                ledgerId,
                startOfYear.format(DateTimeFormatter.ISO_LOCAL_DATE),
                endOfYear.format(DateTimeFormatter.ISO_LOCAL_DATE)
        );

        List<TimePeriodStat> fullList = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            String monthStr = String.format("%04d-%02d", year, i);
            fullList.add(TimePeriodStat.builder()
                    .period(monthStr)
                    .income(BigDecimal.ZERO)
                    .expense(BigDecimal.ZERO)
                    .build());
        }

        Map<String, TimePeriodStat> dbMap = dbList.stream()
                .collect(Collectors.toMap(TimePeriodStat::getPeriod, s -> s, (a, b) -> a));

        for (TimePeriodStat stat : fullList) {
            TimePeriodStat dbStat = dbMap.get(stat.getPeriod());
            if (dbStat != null) {
                stat.setIncome(dbStat.getIncome() != null ? dbStat.getIncome() : BigDecimal.ZERO);
                stat.setExpense(dbStat.getExpense() != null ? dbStat.getExpense() : BigDecimal.ZERO);
            }
        }

        BigDecimal totalIncome = fullList.stream()
                .map(TimePeriodStat::getIncome)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalExpense = fullList.stream()
                .map(TimePeriodStat::getExpense)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal balance = totalIncome.subtract(totalExpense);

        return StatisticsResponse.builder()
                .periodStats(fullList)
                .totalIncome(totalIncome)
                .totalExpense(totalExpense)
                .balance(balance)
                .build();
    }

    public StatisticsResponse getCategoryStats(Long userId, Long ledgerId, Integer type, LocalDate startDate, LocalDate endDate) {
        String typeKey = type != null ? String.valueOf(type) : "all";
        String startKey = startDate != null ? startDate.toString() : "all";
        String endKey = endDate != null ? endDate.toString() : "all";
        String ledgerKey = ledgerId != null ? String.valueOf(ledgerId) : "all";
        String key = "statistics:category:" + userId + ":" + typeKey + ":" + startKey + ":" + endKey + ":" + ledgerKey;

        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, StatisticsResponse.class);
            } catch (Exception e) {
                // ignore
            }
        }
        StatisticsResponse response = calculateCategoryStats(userId, ledgerId, type, startDate, endDate);
        cacheResponse(key, response);
        return response;
    }

    private StatisticsResponse calculateCategoryStats(Long userId, Long ledgerId, Integer type, LocalDate startDate, LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.of(1970, 1, 1);
        LocalDate end = endDate != null ? endDate : LocalDate.now();

        List<CategoryStat> dbList = statisticsMapper.categoryStats(
                userId,
                ledgerId,
                type,
                start.format(DateTimeFormatter.ISO_LOCAL_DATE),
                end.format(DateTimeFormatter.ISO_LOCAL_DATE)
        );

        BigDecimal totalIncome = dbList.stream()
                .filter(s -> s.getType() != null && s.getType() == 1)
                .map(CategoryStat::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalExpense = dbList.stream()
                .filter(s -> s.getType() != null && s.getType() == 2)
                .map(CategoryStat::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        for (CategoryStat stat : dbList) {
            if (stat.getAmount() == null) {
                stat.setAmount(BigDecimal.ZERO);
            }
            if (stat.getType() != null && stat.getType() == 1 && totalIncome.compareTo(BigDecimal.ZERO) > 0) {
                double percentage = stat.getAmount().multiply(BigDecimal.valueOf(100))
                        .divide(totalIncome, 2, RoundingMode.HALF_UP)
                        .doubleValue();
                stat.setPercentage(percentage);
            } else if (stat.getType() != null && stat.getType() == 2 && totalExpense.compareTo(BigDecimal.ZERO) > 0) {
                double percentage = stat.getAmount().multiply(BigDecimal.valueOf(100))
                        .divide(totalExpense, 2, RoundingMode.HALF_UP)
                        .doubleValue();
                stat.setPercentage(percentage);
            } else {
                stat.setPercentage(0.0);
            }
        }

        return StatisticsResponse.builder()
                .categoryStats(dbList)
                .totalIncome(totalIncome)
                .totalExpense(totalExpense)
                .balance(totalIncome.subtract(totalExpense))
                .build();
    }

    private void cacheResponse(String key, StatisticsResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(key, json, 5, TimeUnit.MINUTES);
        } catch (Exception e) {
            // ignore
        }
    }
}

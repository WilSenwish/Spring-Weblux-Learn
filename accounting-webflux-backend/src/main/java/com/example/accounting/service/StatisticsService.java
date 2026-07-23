package com.example.accounting.service;

import com.example.accounting.dto.CategoryStat;
import com.example.accounting.dto.StatisticsResponse;
import com.example.accounting.dto.TimePeriodStat;
import com.example.accounting.repository.CategoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class StatisticsService {

    @Autowired
    private DatabaseClient databaseClient;

    @Autowired
    private R2dbcEntityTemplate template;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 按周统计
     */
    public Mono<StatisticsResponse> getWeeklyStats(Long userId, Long ledgerId) {
        String ledgerKey = ledgerId != null ? String.valueOf(ledgerId) : "all";
        String key = "statistics:weekly:" + userId + ":" + ledgerKey;
        return redisTemplate.opsForValue().get(key)
                .flatMap(json -> {
                    try {
                        StatisticsResponse stats = objectMapper.readValue(json, StatisticsResponse.class);
                        return Mono.just(stats);
                    } catch (Exception e) {
                        return Mono.empty();
                    }
                })
                .switchIfEmpty(calculateWeeklyStats(userId, ledgerId)
                        .flatMap(response -> cacheResponse(key, response).thenReturn(response))
                );
    }

    private Mono<StatisticsResponse> calculateWeeklyStats(Long userId, Long ledgerId) {
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        LocalDate sunday = today.with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY));

        StringBuilder sqlBuilder = new StringBuilder(
                "SELECT " +
                "DATE_FORMAT(bill_date, '%Y-%m-%d') as period, " +
                "SUM(CASE WHEN type = 1 THEN amount ELSE 0 END) as income, " +
                "SUM(CASE WHEN type = 2 THEN amount ELSE 0 END) as expense " +
                "FROM bill " +
                "WHERE user_id = :userId AND bill_date >= :start AND bill_date <= :end "
        );
        if (ledgerId != null) {
            sqlBuilder.append("AND ledger_id = :ledgerId ");
        }
        sqlBuilder.append("GROUP BY DATE_FORMAT(bill_date, '%Y-%m-%d') ORDER BY period");

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sqlBuilder.toString())
                .bind("userId", userId)
                .bind("start", monday)
                .bind("end", sunday);
        if (ledgerId != null) {
            spec = spec.bind("ledgerId", ledgerId);
        }

        return spec.map((row, metadata) -> TimePeriodStat.builder()
                        .period(row.get("period", String.class))
                        .income(Optional.ofNullable(row.get("income", BigDecimal.class)).orElse(BigDecimal.ZERO))
                        .expense(Optional.ofNullable(row.get("expense", BigDecimal.class)).orElse(BigDecimal.ZERO))
                        .build())
                .all()
                .collectList()
                .map(dbList -> {
                    List<TimePeriodStat> fullList = new ArrayList<>();
                    for (int i = 0; i < 7; i++) {
                        LocalDate date = monday.plusDays(i);
                        String period = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
                        fullList.add(TimePeriodStat.builder().period(period).income(BigDecimal.ZERO).expense(BigDecimal.ZERO).build());
                    }

                    Map<String, TimePeriodStat> dbMap = dbList.stream()
                            .collect(Collectors.toMap(TimePeriodStat::getPeriod, s -> s, (a, b) -> a));

                    for (TimePeriodStat stat : fullList) {
                        TimePeriodStat dbStat = dbMap.get(stat.getPeriod());
                        if (dbStat != null) {
                            stat.setIncome(dbStat.getIncome());
                            stat.setExpense(dbStat.getExpense());
                        }
                    }

                    BigDecimal totalIncome = fullList.stream()
                            .map(TimePeriodStat::getIncome)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal totalExpense = fullList.stream()
                            .map(TimePeriodStat::getExpense)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal balance = totalIncome.subtract(totalExpense);

                    StatisticsResponse response = StatisticsResponse.builder()
                            .periodStats(fullList)
                            .totalIncome(totalIncome)
                            .totalExpense(totalExpense)
                            .balance(balance)
                            .build();
                    return response;
                });
    }

    /**
     * 按月统计
     */
    public Mono<StatisticsResponse> getMonthlyStats(Long userId, Long ledgerId, int year, int month) {
        String ledgerKey = ledgerId != null ? String.valueOf(ledgerId) : "all";
        String key = "statistics:monthly:" + userId + ":" + ledgerKey + ":" + year + ":" + month;
        return redisTemplate.opsForValue().get(key)
                .flatMap(json -> {
                    try {
                        StatisticsResponse stats = objectMapper.readValue(json, StatisticsResponse.class);
                        return Mono.just(stats);
                    } catch (Exception e) {
                        return Mono.empty();
                    }
                })
                .switchIfEmpty(calculateMonthlyStats(userId, ledgerId, year, month)
                        .flatMap(response -> cacheResponse(key, response).thenReturn(response))
                );
    }

    private Mono<StatisticsResponse> calculateMonthlyStats(Long userId, Long ledgerId, int year, int month) {
        LocalDate startOfMonth = LocalDate.of(year, month, 1);
        LocalDate endOfMonth = startOfMonth.with(TemporalAdjusters.lastDayOfMonth());

        StringBuilder sqlBuilder = new StringBuilder(
                "SELECT " +
                "DATE_FORMAT(bill_date, '%Y-%m-%d') as period, " +
                "SUM(CASE WHEN type = 1 THEN amount ELSE 0 END) as income, " +
                "SUM(CASE WHEN type = 2 THEN amount ELSE 0 END) as expense " +
                "FROM bill " +
                "WHERE user_id = :userId AND bill_date >= :start AND bill_date <= :end "
        );
        if (ledgerId != null) {
            sqlBuilder.append("AND ledger_id = :ledgerId ");
        }
        sqlBuilder.append("GROUP BY DATE_FORMAT(bill_date, '%Y-%m-%d') ORDER BY period");

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sqlBuilder.toString())
                .bind("userId", userId)
                .bind("start", startOfMonth)
                .bind("end", endOfMonth);
        if (ledgerId != null) {
            spec = spec.bind("ledgerId", ledgerId);
        }

        return spec.map((row, metadata) -> TimePeriodStat.builder()
                        .period(row.get("period", String.class))
                        .income(Optional.ofNullable(row.get("income", BigDecimal.class)).orElse(BigDecimal.ZERO))
                        .expense(Optional.ofNullable(row.get("expense", BigDecimal.class)).orElse(BigDecimal.ZERO))
                        .build())
                .all()
                .collectList()
                .map(dbList -> {
                    List<TimePeriodStat> fullList = new ArrayList<>();
                    for (int i = 1; i <= endOfMonth.getDayOfMonth(); i++) {
                        String day = String.format("%04d-%02d-%02d", year, month, i);
                        fullList.add(TimePeriodStat.builder().period(day).income(BigDecimal.ZERO).expense(BigDecimal.ZERO).build());
                    }

                    Map<String, TimePeriodStat> dbMap = dbList.stream()
                            .collect(Collectors.toMap(TimePeriodStat::getPeriod, s -> s, (a, b) -> a));

                    for (TimePeriodStat stat : fullList) {
                        TimePeriodStat dbStat = dbMap.get(stat.getPeriod());
                        if (dbStat != null) {
                            stat.setIncome(dbStat.getIncome());
                            stat.setExpense(dbStat.getExpense());
                        }
                    }

                    BigDecimal totalIncome = fullList.stream()
                            .map(TimePeriodStat::getIncome)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal totalExpense = fullList.stream()
                            .map(TimePeriodStat::getExpense)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal balance = totalIncome.subtract(totalExpense);

                    StatisticsResponse response = StatisticsResponse.builder()
                            .periodStats(fullList)
                            .totalIncome(totalIncome)
                            .totalExpense(totalExpense)
                            .balance(balance)
                            .build();
                    return response;
                });
    }

    /**
     * 按年统计
     */
    public Mono<StatisticsResponse> getYearlyStats(Long userId, Long ledgerId, int year) {
        String ledgerKey = ledgerId != null ? String.valueOf(ledgerId) : "all";
        String key = "statistics:yearly:" + userId + ":" + ledgerKey + ":" + year;
        return redisTemplate.opsForValue().get(key)
                .flatMap(json -> {
                    try {
                        StatisticsResponse stats = objectMapper.readValue(json, StatisticsResponse.class);
                        return Mono.just(stats);
                    } catch (Exception e) {
                        return Mono.empty();
                    }
                })
                .switchIfEmpty(calculateYearlyStats(userId, ledgerId, year)
                        .flatMap(response -> cacheResponse(key, response).thenReturn(response))
                );
    }

    private Mono<StatisticsResponse> calculateYearlyStats(Long userId, Long ledgerId, int year) {
        LocalDate startOfYear = LocalDate.of(year, 1, 1);
        LocalDate endOfYear = LocalDate.of(year, 12, 31);

        StringBuilder sqlBuilder = new StringBuilder(
                "SELECT " +
                "DATE_FORMAT(bill_date, '%Y-%m') as period, " +
                "SUM(CASE WHEN type = 1 THEN amount ELSE 0 END) as income, " +
                "SUM(CASE WHEN type = 2 THEN amount ELSE 0 END) as expense " +
                "FROM bill " +
                "WHERE user_id = :userId AND bill_date >= :start AND bill_date <= :end "
        );
        if (ledgerId != null) {
            sqlBuilder.append("AND ledger_id = :ledgerId ");
        }
        sqlBuilder.append("GROUP BY DATE_FORMAT(bill_date, '%Y-%m') ORDER BY period");

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sqlBuilder.toString())
                .bind("userId", userId)
                .bind("start", startOfYear)
                .bind("end", endOfYear);
        if (ledgerId != null) {
            spec = spec.bind("ledgerId", ledgerId);
        }

        return spec.map((row, metadata) -> TimePeriodStat.builder()
                        .period(row.get("period", String.class))
                        .income(Optional.ofNullable(row.get("income", BigDecimal.class)).orElse(BigDecimal.ZERO))
                        .expense(Optional.ofNullable(row.get("expense", BigDecimal.class)).orElse(BigDecimal.ZERO))
                        .build())
                .all()
                .collectList()
                .map(dbList -> {
                    List<TimePeriodStat> fullList = new ArrayList<>();
                    for (int i = 1; i <= 12; i++) {
                        String monthStr = String.format("%04d-%02d", year, i);
                        fullList.add(TimePeriodStat.builder().period(monthStr).income(BigDecimal.ZERO).expense(BigDecimal.ZERO).build());
                    }

                    Map<String, TimePeriodStat> dbMap = dbList.stream()
                            .collect(Collectors.toMap(TimePeriodStat::getPeriod, s -> s, (a, b) -> a));

                    for (TimePeriodStat stat : fullList) {
                        TimePeriodStat dbStat = dbMap.get(stat.getPeriod());
                        if (dbStat != null) {
                            stat.setIncome(dbStat.getIncome());
                            stat.setExpense(dbStat.getExpense());
                        }
                    }

                    BigDecimal totalIncome = fullList.stream()
                            .map(TimePeriodStat::getIncome)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal totalExpense = fullList.stream()
                            .map(TimePeriodStat::getExpense)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal balance = totalIncome.subtract(totalExpense);

                    StatisticsResponse response = StatisticsResponse.builder()
                            .periodStats(fullList)
                            .totalIncome(totalIncome)
                            .totalExpense(totalExpense)
                            .balance(balance)
                            .build();
                    return response;
                });
    }

    /**
     * 按分类统计
     */
    public Mono<StatisticsResponse> getCategoryStats(Long userId, Long ledgerId, Integer type, LocalDate startDate, LocalDate endDate) {
        String ledgerKey = ledgerId != null ? String.valueOf(ledgerId) : "all";
        String typeKey = type != null ? String.valueOf(type) : "all";
        String startKey = startDate != null ? startDate.toString() : "all";
        String endKey = endDate != null ? endDate.toString() : "all";
        String key = "statistics:category:" + userId + ":" + ledgerKey + ":" + typeKey + ":" + startKey + ":" + endKey;

        return redisTemplate.opsForValue().get(key)
                .flatMap(json -> {
                    try {
                        StatisticsResponse stats = objectMapper.readValue(json, StatisticsResponse.class);
                        return Mono.just(stats);
                    } catch (Exception e) {
                        return Mono.empty();
                    }
                })
                .switchIfEmpty(calculateCategoryStats(userId, ledgerId, type, startDate, endDate)
                        .flatMap(response -> cacheResponse(key, response).thenReturn(response))
                );
    }

    private Mono<StatisticsResponse> calculateCategoryStats(Long userId, Long ledgerId, Integer type, LocalDate startDate, LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.of(1970, 1, 1);
        LocalDate end = endDate != null ? endDate : LocalDate.now();

        StringBuilder sqlBuilder = new StringBuilder(
                "SELECT " +
                "b.category_id as category_id, " +
                "c.name as category_name, " +
                "c.type as type, " +
                "SUM(b.amount) as amount " +
                "FROM bill b " +
                "JOIN category c ON b.category_id = c.id " +
                "WHERE b.user_id = :userId " +
                "AND b.bill_date >= :start AND b.bill_date <= :end "
        );
        if (ledgerId != null) {
            sqlBuilder.append("AND b.ledger_id = :ledgerId ");
        }
        if (type != null) {
            sqlBuilder.append(" AND c.type = :type");
        }
        sqlBuilder.append(" GROUP BY b.category_id, c.name, c.type");

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sqlBuilder.toString())
                .bind("userId", userId)
                .bind("start", start)
                .bind("end", end);

        if (ledgerId != null) {
            spec = spec.bind("ledgerId", ledgerId);
        }
        if (type != null) {
            spec = spec.bind("type", type);
        }

        return spec.map((row, metadata) -> CategoryStat.builder()
                        .categoryId(row.get("category_id", Long.class))
                        .categoryName(row.get("category_name", String.class))
                        .type(row.get("type", Integer.class))
                        .amount(Optional.ofNullable(row.get("amount", BigDecimal.class)).orElse(BigDecimal.ZERO))
                        .build())
                .all()
                .collectList()
                .map(dbList -> {
                    BigDecimal totalIncome = dbList.stream()
                            .filter(s -> s.getType() != null && s.getType() == 1)
                            .map(CategoryStat::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal totalExpense = dbList.stream()
                            .filter(s -> s.getType() != null && s.getType() == 2)
                            .map(CategoryStat::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    for (CategoryStat stat : dbList) {
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

                    StatisticsResponse response = StatisticsResponse.builder()
                            .categoryStats(dbList)
                            .totalIncome(totalIncome)
                            .totalExpense(totalExpense)
                            .balance(totalIncome.subtract(totalExpense))
                            .build();
                    return response;
                });
    }

    /**
     * 缓存统计结果到 Redis，TTL 5 分钟
     */
    private Mono<Void> cacheResponse(String key, StatisticsResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            return redisTemplate.opsForValue()
                    .set(key, json, Duration.ofMinutes(5))
                    .then();
        } catch (Exception e) {
            return Mono.empty();
        }
    }
}

package com.example.accounting.controller;

import com.example.accounting.dto.StatisticsResponse;
import com.example.accounting.service.StatisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/statistics")
public class StatisticsController extends BaseController {

    @Autowired
    private StatisticsService statisticsService;

    @GetMapping("/weekly")
    public StatisticsResponse weekly(@RequestParam(required = false) Long ledgerId) {
        Long userId = getCurrentUserId();
        return statisticsService.getWeeklyStats(userId, ledgerId);
    }

    @GetMapping("/monthly")
    public StatisticsResponse monthly(
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam(required = false) Long ledgerId) {
        Long userId = getCurrentUserId();
        return statisticsService.getMonthlyStats(userId, ledgerId, year, month);
    }

    @GetMapping("/yearly")
    public StatisticsResponse yearly(
            @RequestParam int year,
            @RequestParam(required = false) Long ledgerId) {
        Long userId = getCurrentUserId();
        return statisticsService.getYearlyStats(userId, ledgerId, year);
    }

    @GetMapping("/category")
    public StatisticsResponse category(
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Long ledgerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Long userId = getCurrentUserId();
        return statisticsService.getCategoryStats(userId, ledgerId, type, startDate, endDate);
    }
}

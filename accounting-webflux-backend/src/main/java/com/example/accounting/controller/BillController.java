package com.example.accounting.controller;

import com.example.accounting.common.PageResult;
import com.example.accounting.dto.BillQueryRequest;
import com.example.accounting.dto.BillRequest;
import com.example.accounting.entity.Bill;
import com.example.accounting.service.BillService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/bills")
public class BillController extends BaseController {

    @Autowired
    private BillService billService;

    @PostMapping
    public Mono<Bill> create(@Valid @RequestBody BillRequest request) {
        return getCurrentUserId()
                .flatMap(userId -> billService.createBill(userId, request));
    }

    @PutMapping("/{id}")
    public Mono<Bill> update(@PathVariable Long id, @Valid @RequestBody BillRequest request) {
        return getCurrentUserId()
                .flatMap(userId -> billService.updateBill(userId, id, request));
    }

    @DeleteMapping("/{id}")
    public Mono<Void> delete(@PathVariable Long id) {
        return getCurrentUserId()
                .flatMap(userId -> billService.deleteBill(userId, id));
    }

    @GetMapping
    public Mono<PageResult<Bill>> list(
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        BillQueryRequest query = BillQueryRequest.builder()
                .type(type)
                .categoryId(categoryId)
                .startDate(startDate)
                .endDate(endDate)
                .page(page)
                .size(size)
                .build();
        return getCurrentUserId()
                .flatMap(userId -> billService.listBills(userId, query));
    }
}

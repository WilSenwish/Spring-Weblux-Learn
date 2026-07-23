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

import java.time.LocalDate;

@RestController
@RequestMapping("/api/bills")
public class BillController extends BaseController {

    @Autowired
    private BillService billService;

    @PostMapping
    public Bill create(@Valid @RequestBody BillRequest request) {
        Long userId = getCurrentUserId();
        return billService.createBill(userId, request);
    }

    @PutMapping("/{id}")
    public Bill update(@PathVariable Long id, @Valid @RequestBody BillRequest request) {
        Long userId = getCurrentUserId();
        return billService.updateBill(userId, id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        billService.deleteBill(userId, id);
    }

    @GetMapping
    public PageResult<Bill> list(
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long ledgerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        BillQueryRequest query = BillQueryRequest.builder()
                .type(type)
                .categoryId(categoryId)
                .ledgerId(ledgerId)
                .startDate(startDate)
                .endDate(endDate)
                .page(page)
                .size(size)
                .build();
        Long userId = getCurrentUserId();
        return billService.listBills(userId, query);
    }
}

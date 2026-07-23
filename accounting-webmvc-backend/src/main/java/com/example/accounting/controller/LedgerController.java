package com.example.accounting.controller;

import com.example.accounting.dto.LedgerMemberRequest;
import com.example.accounting.dto.LedgerRequest;
import com.example.accounting.entity.Ledger;
import com.example.accounting.entity.LedgerMember;
import com.example.accounting.service.LedgerService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ledgers")
public class LedgerController extends BaseController {

    @Autowired
    private LedgerService ledgerService;

    @PostMapping
    public Ledger create(@Valid @RequestBody LedgerRequest request) {
        Long userId = getCurrentUserId();
        return ledgerService.createLedger(userId, request);
    }

    @GetMapping
    public List<Ledger> list() {
        Long userId = getCurrentUserId();
        return ledgerService.listLedgers(userId);
    }

    @GetMapping("/{id}")
    public Ledger get(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        return ledgerService.getLedger(userId, id);
    }

    @PutMapping("/{id}")
    public Ledger update(@PathVariable Long id, @Valid @RequestBody LedgerRequest request) {
        Long userId = getCurrentUserId();
        return ledgerService.updateLedger(userId, id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        ledgerService.deleteLedger(userId, id);
    }

    @GetMapping("/{id}/members")
    public List<LedgerMember> listMembers(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        return ledgerService.listMembers(userId, id);
    }

    @PostMapping("/{id}/members")
    public LedgerMember addMember(@PathVariable Long id, @Valid @RequestBody LedgerMemberRequest request) {
        Long userId = getCurrentUserId();
        return ledgerService.addMember(userId, id, request);
    }

    @DeleteMapping("/{id}/members/{userId}")
    public void removeMember(@PathVariable Long id, @PathVariable Long userId) {
        Long currentUserId = getCurrentUserId();
        ledgerService.removeMember(currentUserId, id, userId);
    }
}

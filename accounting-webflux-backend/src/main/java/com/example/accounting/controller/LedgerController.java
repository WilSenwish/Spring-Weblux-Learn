package com.example.accounting.controller;

import com.example.accounting.dto.LedgerMemberRequest;
import com.example.accounting.dto.LedgerRequest;
import com.example.accounting.entity.Ledger;
import com.example.accounting.entity.LedgerMember;
import com.example.accounting.service.LedgerService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/ledgers")
public class LedgerController extends BaseController {

    @Autowired
    private LedgerService ledgerService;

    /** 创建账本 */
    @PostMapping
    public Mono<Ledger> create(@Valid @RequestBody LedgerRequest request) {
        return getCurrentUserId()
                .flatMap(userId -> ledgerService.createLedger(userId, request));
    }

    /** 查询当前用户参与的所有账本 */
    @GetMapping
    public Mono<List<Ledger>> list() {
        return getCurrentUserId()
                .flatMap(ledgerService::listLedgers);
    }

    /** 获取账本详情 */
    @GetMapping("/{id}")
    public Mono<Ledger> get(@PathVariable Long id) {
        return getCurrentUserId()
                .flatMap(userId -> ledgerService.getLedger(userId, id));
    }

    /** 更新账本 */
    @PutMapping("/{id}")
    public Mono<Ledger> update(@PathVariable Long id, @Valid @RequestBody LedgerRequest request) {
        return getCurrentUserId()
                .flatMap(userId -> ledgerService.updateLedger(userId, id, request));
    }

    /** 删除账本 */
    @DeleteMapping("/{id}")
    public Mono<Void> delete(@PathVariable Long id) {
        return getCurrentUserId()
                .flatMap(userId -> ledgerService.deleteLedger(userId, id));
    }

    /** 获取账本成员列表 */
    @GetMapping("/{id}/members")
    public Mono<List<LedgerMember>> listMembers(@PathVariable Long id) {
        return getCurrentUserId()
                .flatMap(userId -> ledgerService.listMembers(userId, id));
    }

    /** 邀请账本成员 */
    @PostMapping("/{id}/members")
    public Mono<LedgerMember> addMember(@PathVariable Long id, @Valid @RequestBody LedgerMemberRequest request) {
        return getCurrentUserId()
                .flatMap(userId -> ledgerService.addMember(userId, id, request));
    }

    /** 移除账本成员 */
    @DeleteMapping("/{id}/members/{userId}")
    public Mono<Void> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        return getCurrentUserId()
                .flatMap(currentUserId -> ledgerService.removeMember(currentUserId, id, userId));
    }
}

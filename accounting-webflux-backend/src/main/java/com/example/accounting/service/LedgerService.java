package com.example.accounting.service;

import com.example.accounting.common.BusinessException;
import com.example.accounting.dto.LedgerMemberRequest;
import com.example.accounting.dto.LedgerRequest;
import com.example.accounting.entity.Ledger;
import com.example.accounting.entity.LedgerMember;
import com.example.accounting.repository.LedgerMemberRepository;
import com.example.accounting.repository.LedgerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LedgerService {

    /** 所有者角色 */
    private static final int ROLE_OWNER = 1;
    /** 管理员角色 */
    private static final int ROLE_ADMIN = 2;

    @Autowired
    private LedgerRepository ledgerRepository;

    @Autowired
    private LedgerMemberRepository ledgerMemberRepository;

    /**
     * 创建账本，同时在 ledger_member 表插入所有者记录（role=1）
     */
    public Mono<Ledger> createLedger(Long userId, LedgerRequest request) {
        Integer type = request.getType() != null ? request.getType() : 1;
        Integer allowMemberEdit = request.getAllowMemberEdit() != null ? request.getAllowMemberEdit() : 1;
        Ledger ledger = Ledger.builder()
                .name(request.getName())
                .description(request.getDescription())
                .ownerId(userId)
                .type(type)
                .allowMemberEdit(allowMemberEdit)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return ledgerRepository.save(ledger)
                .flatMap(savedLedger -> {
                    LedgerMember member = LedgerMember.builder()
                            .ledgerId(savedLedger.getId())
                            .userId(userId)
                            .role(ROLE_OWNER)
                            .joinedAt(LocalDateTime.now())
                            .build();
                    return ledgerMemberRepository.save(member)
                            .thenReturn(savedLedger);
                });
    }

    /**
     * 查询用户参与的所有账本（通过 ledger_member 关联）
     */
    public Mono<List<Ledger>> listLedgers(Long userId) {
        return ledgerMemberRepository.findByUserId(userId)
                .flatMap(member -> ledgerRepository.findById(member.getLedgerId()))
                .collectList();
    }

    /**
     * 获取账本详情，需校验用户是否为账本成员
     */
    public Mono<Ledger> getLedger(Long userId, Long ledgerId) {
        return checkMembership(userId, ledgerId)
                .flatMap(member -> ledgerRepository.findById(ledgerId)
                        .switchIfEmpty(Mono.error(new BusinessException(404, "账本不存在"))));
    }

    /**
     * 更新账本（仅所有者/管理员）
     */
    public Mono<Ledger> updateLedger(Long userId, Long ledgerId, LedgerRequest request) {
        return checkManagePermission(userId, ledgerId)
                .flatMap(member -> ledgerRepository.findById(ledgerId)
                        .switchIfEmpty(Mono.error(new BusinessException(404, "账本不存在")))
                        .flatMap(ledger -> {
                            ledger.setName(request.getName());
                            ledger.setDescription(request.getDescription());
                            if (request.getType() != null) {
                                ledger.setType(request.getType());
                            }
                            if (request.getAllowMemberEdit() != null) {
                                ledger.setAllowMemberEdit(request.getAllowMemberEdit());
                            }
                            ledger.setUpdatedAt(LocalDateTime.now());
                            return ledgerRepository.save(ledger);
                        }));
    }

    /**
     * 删除账本（仅所有者），同时删除所有 ledger_member 记录
     */
    public Mono<Void> deleteLedger(Long userId, Long ledgerId) {
        return checkOwnerPermission(userId, ledgerId)
                .flatMap(member -> ledgerMemberRepository.findByLedgerId(ledgerId)
                        .flatMap(ledgerMemberRepository::delete)
                        .then(ledgerRepository.findById(ledgerId)
                                .flatMap(ledgerRepository::delete)));
    }

    /**
     * 获取成员列表，需校验用户是否为账本成员
     */
    public Mono<List<LedgerMember>> listMembers(Long userId, Long ledgerId) {
        return checkMembership(userId, ledgerId)
                .flatMap(member -> ledgerMemberRepository.findByLedgerId(ledgerId).collectList());
    }

    /**
     * 邀请成员（仅所有者/管理员）
     */
    public Mono<LedgerMember> addMember(Long userId, Long ledgerId, LedgerMemberRequest request) {
        return checkManagePermission(userId, ledgerId)
                .flatMap(member -> ledgerMemberRepository.findByLedgerIdAndUserId(ledgerId, request.getUserId())
                        .flatMap(existing -> Mono.<LedgerMember>error(new BusinessException("该用户已是账本成员")))
                        .switchIfEmpty(Mono.defer(() -> {
                            Integer role = request.getRole() != null ? request.getRole() : 3;
                            LedgerMember newMember = LedgerMember.builder()
                                    .ledgerId(ledgerId)
                                    .userId(request.getUserId())
                                    .role(role)
                                    .joinedAt(LocalDateTime.now())
                                    .build();
                            return ledgerMemberRepository.save(newMember);
                        })));
    }

    /**
     * 移除成员（仅所有者/管理员，不能移除所有者 role=1）
     */
    public Mono<Void> removeMember(Long userId, Long ledgerId, Long targetUserId) {
        return checkManagePermission(userId, ledgerId)
                .flatMap(member -> ledgerMemberRepository.findByLedgerIdAndUserId(ledgerId, targetUserId)
                        .switchIfEmpty(Mono.error(new BusinessException("该成员不存在")))
                        .flatMap(targetMember -> {
                            if (ROLE_OWNER == targetMember.getRole()) {
                                return Mono.error(new BusinessException("不能移除账本所有者"));
                            }
                            return ledgerMemberRepository.deleteByLedgerIdAndUserId(ledgerId, targetUserId).then();
                        }));
    }

    /**
     * 校验当前用户是否为账本成员，返回成员记录
     */
    private Mono<LedgerMember> checkMembership(Long userId, Long ledgerId) {
        return ledgerMemberRepository.findByLedgerIdAndUserId(ledgerId, userId)
                .switchIfEmpty(Mono.error(new BusinessException(403, "无权访问该账本")));
    }

    /**
     * 校验当前用户是否具有管理权限（所有者/管理员），返回成员记录
     */
    private Mono<LedgerMember> checkManagePermission(Long userId, Long ledgerId) {
        return checkMembership(userId, ledgerId)
                .flatMap(member -> {
                    if (ROLE_OWNER == member.getRole() || ROLE_ADMIN == member.getRole()) {
                        return Mono.just(member);
                    }
                    return Mono.error(new BusinessException(403, "无管理权限"));
                });
    }

    /**
     * 校验当前用户是否为账本所有者，返回成员记录
     */
    private Mono<LedgerMember> checkOwnerPermission(Long userId, Long ledgerId) {
        return checkMembership(userId, ledgerId)
                .flatMap(member -> {
                    if (ROLE_OWNER == member.getRole()) {
                        return Mono.just(member);
                    }
                    return Mono.error(new BusinessException(403, "仅所有者可执行该操作"));
                });
    }
}

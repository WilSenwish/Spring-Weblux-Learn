package com.example.accounting.service;

import com.example.accounting.common.BusinessException;
import com.example.accounting.dto.LedgerMemberRequest;
import com.example.accounting.dto.LedgerRequest;
import com.example.accounting.entity.Ledger;
import com.example.accounting.entity.LedgerMember;
import com.example.accounting.mapper.LedgerMapper;
import com.example.accounting.mapper.LedgerMemberMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LedgerService {

    @Autowired
    private LedgerMapper ledgerMapper;

    @Autowired
    private LedgerMemberMapper ledgerMemberMapper;

    /**
     * 创建账本，同时在 ledger_member 表插入所有者记录（role=1）
     */
    @Transactional
    public Ledger createLedger(Long userId, LedgerRequest request) {
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
        ledgerMapper.insert(ledger);

        LedgerMember member = LedgerMember.builder()
                .ledgerId(ledger.getId())
                .userId(userId)
                .role(1)
                .joinedAt(LocalDateTime.now())
                .build();
        ledgerMemberMapper.insert(member);

        return ledger;
    }

    /**
     * 查询用户参与的所有账本
     */
    public List<Ledger> listLedgers(Long userId) {
        return ledgerMapper.findByUserId(userId);
    }

    /**
     * 获取账本详情，需校验用户是否为账本成员
     */
    public Ledger getLedger(Long userId, Long ledgerId) {
        Ledger ledger = ledgerMapper.findById(ledgerId);
        if (ledger == null) {
            throw new BusinessException(404, "账本不存在");
        }
        checkMember(ledgerId, userId);
        return ledger;
    }

    /**
     * 更新账本（仅所有者/管理员）
     */
    @Transactional
    public Ledger updateLedger(Long userId, Long ledgerId, LedgerRequest request) {
        Ledger ledger = ledgerMapper.findById(ledgerId);
        if (ledger == null) {
            throw new BusinessException(404, "账本不存在");
        }
        checkManager(ledgerId, userId);
        ledger.setName(request.getName());
        ledger.setDescription(request.getDescription());
        if (request.getType() != null) {
            ledger.setType(request.getType());
        }
        if (request.getAllowMemberEdit() != null) {
            ledger.setAllowMemberEdit(request.getAllowMemberEdit());
        }
        ledger.setUpdatedAt(LocalDateTime.now());
        ledgerMapper.update(ledger);
        return ledger;
    }

    /**
     * 删除账本（仅所有者），同时删除所有 ledger_member 记录
     */
    @Transactional
    public void deleteLedger(Long userId, Long ledgerId) {
        Ledger ledger = ledgerMapper.findById(ledgerId);
        if (ledger == null) {
            throw new BusinessException(404, "账本不存在");
        }
        if (!userId.equals(ledger.getOwnerId())) {
            throw new BusinessException("无权删除该账本");
        }
        ledgerMemberMapper.deleteByLedgerId(ledgerId);
        ledgerMapper.delete(ledgerId);
    }

    /**
     * 获取成员列表，需校验用户是否为账本成员
     */
    public List<LedgerMember> listMembers(Long userId, Long ledgerId) {
        Ledger ledger = ledgerMapper.findById(ledgerId);
        if (ledger == null) {
            throw new BusinessException(404, "账本不存在");
        }
        checkMember(ledgerId, userId);
        return ledgerMemberMapper.findByLedgerId(ledgerId);
    }

    /**
     * 邀请成员（仅所有者/管理员）
     */
    @Transactional
    public LedgerMember addMember(Long userId, Long ledgerId, LedgerMemberRequest request) {
        Ledger ledger = ledgerMapper.findById(ledgerId);
        if (ledger == null) {
            throw new BusinessException(404, "账本不存在");
        }
        checkManager(ledgerId, userId);
        if (ledgerMemberMapper.findByLedgerIdAndUserId(ledgerId, request.getUserId()) != null) {
            throw new BusinessException("该用户已是账本成员");
        }
        Integer role = request.getRole() != null ? request.getRole() : 3;
        LedgerMember member = LedgerMember.builder()
                .ledgerId(ledgerId)
                .userId(request.getUserId())
                .role(role)
                .joinedAt(LocalDateTime.now())
                .build();
        ledgerMemberMapper.insert(member);
        return member;
    }

    /**
     * 移除成员（仅所有者/管理员，不能移除所有者 role=1）
     */
    @Transactional
    public void removeMember(Long userId, Long ledgerId, Long targetUserId) {
        Ledger ledger = ledgerMapper.findById(ledgerId);
        if (ledger == null) {
            throw new BusinessException(404, "账本不存在");
        }
        checkManager(ledgerId, userId);
        LedgerMember target = ledgerMemberMapper.findByLedgerIdAndUserId(ledgerId, targetUserId);
        if (target == null) {
            throw new BusinessException(404, "成员不存在");
        }
        if (Integer.valueOf(1).equals(target.getRole())) {
            throw new BusinessException("不能移除账本所有者");
        }
        ledgerMemberMapper.deleteByLedgerIdAndUserId(ledgerId, targetUserId);
    }

    /**
     * 校验当前用户是否为账本成员
     */
    private void checkMember(Long ledgerId, Long userId) {
        if (ledgerMemberMapper.findByLedgerIdAndUserId(ledgerId, userId) == null) {
            throw new BusinessException("无权访问该账本");
        }
    }

    /**
     * 校验当前用户是否为账本所有者或管理员
     */
    private void checkManager(Long ledgerId, Long userId) {
        LedgerMember member = ledgerMemberMapper.findByLedgerIdAndUserId(ledgerId, userId);
        if (member == null) {
            throw new BusinessException("无权管理该账本");
        }
        if (!Integer.valueOf(1).equals(member.getRole()) && !Integer.valueOf(2).equals(member.getRole())) {
            throw new BusinessException("无权管理该账本");
        }
    }
}

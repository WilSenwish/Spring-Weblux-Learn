package com.example.accounting.service;

import com.example.accounting.common.BusinessException;
import com.example.accounting.common.PageResult;
import com.example.accounting.dto.BillQueryRequest;
import com.example.accounting.dto.BillRequest;
import com.example.accounting.entity.Bill;
import com.example.accounting.entity.BillDocument;
import com.example.accounting.entity.Category;
import com.example.accounting.entity.Ledger;
import com.example.accounting.entity.LedgerMember;
import com.example.accounting.mapper.BillMapper;
import com.example.accounting.mapper.CategoryMapper;
import com.example.accounting.mapper.LedgerMapper;
import com.example.accounting.mapper.LedgerMemberMapper;
import com.example.accounting.repository.BillDocumentRepository;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BillService {

    @Autowired
    private BillMapper billMapper;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private BillDocumentRepository billDocumentRepository;

    @Autowired
    private LedgerMapper ledgerMapper;

    @Autowired
    private LedgerMemberMapper ledgerMemberMapper;

    @Transactional
    public Bill createBill(Long userId, BillRequest request) {
        Category category = categoryMapper.findById(request.getCategoryId());
        if (category == null) {
            throw new BusinessException(400, "分类不存在");
        }
        if (!userId.equals(category.getUserId()) && !Integer.valueOf(1).equals(category.getIsPreset())) {
            throw new BusinessException("无权使用该分类");
        }
        if (!request.getType().equals(category.getType())) {
            throw new BusinessException("账单类型与分类类型不一致");
        }

        // 解析账本ID：指定则校验成员身份，未指定则查找默认个人账本
        Long ledgerId = request.getLedgerId();
        if (ledgerId != null) {
            if (ledgerMemberMapper.findByLedgerIdAndUserId(ledgerId, userId) == null) {
                throw new BusinessException("无权使用该账本");
            }
        } else {
            List<Ledger> ownedLedgers = ledgerMapper.findByOwnerId(userId);
            for (Ledger l : ownedLedgers) {
                if (Integer.valueOf(1).equals(l.getType())) {
                    ledgerId = l.getId();
                    break;
                }
            }
            if (ledgerId == null) {
                throw new BusinessException(400, "请先创建账本");
            }
        }

        Bill bill = Bill.builder()
                .userId(userId)
                .categoryId(request.getCategoryId())
                .ledgerId(ledgerId)
                .amount(request.getAmount())
                .type(request.getType())
                .remark(request.getRemark())
                .billDate(request.getBillDate())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        billMapper.insert(bill);

        BillDocument document = BillDocument.builder()
                .mysqlId(bill.getId())
                .userId(bill.getUserId())
                .categoryId(bill.getCategoryId())
                .ledgerId(bill.getLedgerId())
                .amount(bill.getAmount())
                .type(bill.getType())
                .remark(bill.getRemark())
                .billDate(bill.getBillDate())
                .createdAt(bill.getCreatedAt())
                .updatedAt(bill.getUpdatedAt())
                .build();
        billDocumentRepository.save(document);

        return bill;
    }

    @Transactional
    public Bill updateBill(Long userId, Long billId, BillRequest request) {
        Bill bill = billMapper.findById(billId);
        if (bill == null) {
            throw new BusinessException(404, "账单不存在或无权限");
        }
        // 校验账本成员修改权限
        if (!checkBillEditPermission(userId, bill)) {
            throw new BusinessException(403, "无权修改他人账单");
        }
        Category category = categoryMapper.findById(request.getCategoryId());
        if (category == null) {
            throw new BusinessException(400, "分类不存在");
        }
        if (!userId.equals(category.getUserId()) && !Integer.valueOf(1).equals(category.getIsPreset())) {
            throw new BusinessException("无权使用该分类");
        }
        if (!request.getType().equals(category.getType())) {
            throw new BusinessException("账单类型与分类类型不一致");
        }
        bill.setCategoryId(request.getCategoryId());
        bill.setAmount(request.getAmount());
        bill.setType(request.getType());
        bill.setRemark(request.getRemark());
        bill.setBillDate(request.getBillDate());
        bill.setUpdatedAt(LocalDateTime.now());
        billMapper.update(bill);

        // MongoDB 一致性修复：文档不存在时新建，存在时更新
        BillDocument document = billDocumentRepository.findByMysqlId(billId).orElse(null);
        if (document == null) {
            document = BillDocument.builder()
                    .mysqlId(bill.getId())
                    .userId(bill.getUserId())
                    .categoryId(bill.getCategoryId())
                    .ledgerId(bill.getLedgerId())
                    .amount(bill.getAmount())
                    .type(bill.getType())
                    .remark(bill.getRemark())
                    .billDate(bill.getBillDate())
                    .createdAt(bill.getCreatedAt())
                    .updatedAt(bill.getUpdatedAt())
                    .build();
        }
        document.setCategoryId(bill.getCategoryId());
        document.setLedgerId(bill.getLedgerId());
        document.setAmount(bill.getAmount());
        document.setType(bill.getType());
        document.setRemark(bill.getRemark());
        document.setBillDate(bill.getBillDate());
        document.setUpdatedAt(bill.getUpdatedAt());
        billDocumentRepository.save(document);

        return bill;
    }

    @Transactional
    public void deleteBill(Long userId, Long billId) {
        Bill bill = billMapper.findById(billId);
        if (bill == null) {
            throw new BusinessException(404, "账单不存在或无权限");
        }
        // 校验账本成员修改权限
        if (!checkBillEditPermission(userId, bill)) {
            throw new BusinessException(403, "无权删除他人账单");
        }
        billMapper.delete(billId);
        billDocumentRepository.deleteByMysqlId(billId);
    }

    public PageResult<Bill> listBills(Long userId, BillQueryRequest query) {
        PageHelper.startPage(query.getPage(), query.getSize());
        List<Bill> bills = billMapper.findByUserId(
                userId,
                query.getType(),
                query.getCategoryId(),
                query.getLedgerId(),
                query.getStartDate(),
                query.getEndDate()
        );
        PageInfo<Bill> pageInfo = new PageInfo<>(bills);
        return PageResult.<Bill>builder()
                .list(pageInfo.getList())
                .total(pageInfo.getTotal())
                .page(query.getPage())
                .size(query.getSize())
                .build();
    }

    /**
     * 校验用户是否有权限编辑该账单
     * 规则：自己的账单可直接编辑；他人的账单需账本 allow_member_edit=1 或用户是所有者/管理员
     */
    private boolean checkBillEditPermission(Long userId, Bill bill) {
        // 自己的账单，直接允许
        if (userId.equals(bill.getUserId())) {
            return true;
        }
        // 他人的账单，检查账本权限
        if (bill.getLedgerId() == null) {
            return false;
        }
        Ledger ledger = ledgerMapper.findById(bill.getLedgerId());
        if (ledger == null) {
            return false;
        }
        // allow_member_edit=1 允许成员修改
        if (Integer.valueOf(1).equals(ledger.getAllowMemberEdit())) {
            return true;
        }
        // allow_member_edit=0，检查是否为所有者/管理员
        LedgerMember member = ledgerMemberMapper.findByLedgerIdAndUserId(bill.getLedgerId(), userId);
        if (member == null) {
            return false;
        }
        return Integer.valueOf(1).equals(member.getRole()) || Integer.valueOf(2).equals(member.getRole());
    }
}

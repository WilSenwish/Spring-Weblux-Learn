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
import com.example.accounting.repository.BillRepository;
import com.example.accounting.repository.CategoryRepository;
import com.example.accounting.repository.LedgerMemberRepository;
import com.example.accounting.repository.LedgerRepository;
import com.example.accounting.repository.ReactiveBillDocumentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BillService {

    @Autowired
    private BillRepository billRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private R2dbcEntityTemplate template;

    @Autowired
    private ReactiveBillDocumentRepository billDocumentRepository;

    @Autowired
    private LedgerRepository ledgerRepository;

    @Autowired
    private LedgerMemberRepository ledgerMemberRepository;

    public Mono<Bill> createBill(Long userId, BillRequest request) {
        return validateCategory(request.getCategoryId(), userId, request.getType())
                .flatMap(category -> resolveLedgerId(userId, request.getLedgerId()))
                .flatMap(ledgerId -> {
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
                    return billRepository.save(bill)
                            .flatMap(this::syncBillToMongo);
                });
    }

    public Mono<Bill> updateBill(Long userId, Long billId, BillRequest request) {
        return billRepository.findById(billId)
                .switchIfEmpty(Mono.error(new BusinessException(404, "账单不存在或无权限")))
                .filterWhen(bill -> checkBillEditPermission(userId, bill))
                .switchIfEmpty(Mono.error(new BusinessException(403, "无权修改他人账单")))
                .flatMap(bill -> validateCategory(request.getCategoryId(), userId, request.getType())
                        .thenReturn(bill))
                .flatMap(bill -> {
                    bill.setCategoryId(request.getCategoryId());
                    bill.setAmount(request.getAmount());
                    bill.setType(request.getType());
                    bill.setRemark(request.getRemark());
                    bill.setBillDate(request.getBillDate());
                    bill.setUpdatedAt(LocalDateTime.now());
                    return billRepository.save(bill);
                })
                .flatMap(this::syncBillToMongo);
    }

    public Mono<Void> deleteBill(Long userId, Long billId) {
        return billRepository.findById(billId)
                .switchIfEmpty(Mono.error(new BusinessException(404, "账单不存在或无权限")))
                .filterWhen(bill -> checkBillEditPermission(userId, bill))
                .switchIfEmpty(Mono.error(new BusinessException(403, "无权删除他人账单")))
                .flatMap(bill -> billRepository.delete(bill).thenReturn(billId))
                .flatMap(this::deleteBillFromMongo);
    }

    public Mono<PageResult<Bill>> listBills(Long userId, BillQueryRequest query) {
        Criteria criteria = Criteria.where("userId").is(userId);
        if (query.getType() != null) {
            criteria = criteria.and("type").is(query.getType());
        }
        if (query.getCategoryId() != null) {
            criteria = criteria.and("categoryId").is(query.getCategoryId());
        }
        if (query.getLedgerId() != null) {
            criteria = criteria.and("ledgerId").is(query.getLedgerId());
        }
        if (query.getStartDate() != null) {
            criteria = criteria.and("billDate").greaterThanOrEquals(query.getStartDate());
        }
        if (query.getEndDate() != null) {
            criteria = criteria.and("billDate").lessThanOrEquals(query.getEndDate());
        }

        Query listQuery = Query.query(criteria)
                .sort(Sort.by(Sort.Direction.DESC, "billDate"))
                .offset((long) (query.getPage() - 1) * query.getSize())
                .limit(query.getSize());

        Mono<List<Bill>> listMono = template.select(Bill.class).matching(listQuery).all().collectList();
        Mono<Long> countMono = template.select(Bill.class).matching(Query.query(criteria)).count();

        return Mono.zip(listMono, countMono)
                .map(tuple -> PageResult.<Bill>builder()
                        .list(tuple.getT1())
                        .total(tuple.getT2())
                        .page(query.getPage())
                        .size(query.getSize())
                        .build());
    }

    private Mono<Category> validateCategory(Long categoryId, Long userId, Integer type) {
        return categoryRepository.findById(categoryId)
                .switchIfEmpty(Mono.error(new BusinessException(400, "分类不存在")))
                .flatMap(category -> {
                    if (!userId.equals(category.getUserId()) && !Integer.valueOf(1).equals(category.getIsPreset())) {
                        return Mono.error(new BusinessException("无权使用该分类"));
                    }
                    if (!type.equals(category.getType())) {
                        return Mono.error(new BusinessException("账单类型与分类类型不一致"));
                    }
                    return Mono.just(category);
                });
    }

    private Mono<Bill> syncBillToMongo(Bill bill) {
        return billDocumentRepository.findByMysqlId(bill.getId())
                .switchIfEmpty(Mono.defer(() -> Mono.just(BillDocument.builder()
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
                        .build())))
                .flatMap(document -> {
                    document.setCategoryId(bill.getCategoryId());
                    document.setLedgerId(bill.getLedgerId());
                    document.setAmount(bill.getAmount());
                    document.setType(bill.getType());
                    document.setRemark(bill.getRemark());
                    document.setBillDate(bill.getBillDate());
                    document.setUpdatedAt(bill.getUpdatedAt());
                    return billDocumentRepository.save(document);
                })
                .thenReturn(bill);
    }

    private Mono<Void> deleteBillFromMongo(Long billId) {
        return billDocumentRepository.findByMysqlId(billId)
                .flatMap(billDocumentRepository::delete)
                .then();
    }

    /**
     * 解析账本ID：指定则校验成员身份，未指定则使用默认个人账本（type=1 的第一个）
     */
    private Mono<Long> resolveLedgerId(Long userId, Long requestLedgerId) {
        if (requestLedgerId != null) {
            return ledgerMemberRepository.findByLedgerIdAndUserId(requestLedgerId, userId)
                    .switchIfEmpty(Mono.error(new BusinessException(403, "无权使用该账本")))
                    .map(LedgerMember::getLedgerId);
        }
        return ledgerMemberRepository.findByUserId(userId)
                .flatMap(member -> ledgerRepository.findById(member.getLedgerId()))
                .filter(ledger -> Integer.valueOf(1).equals(ledger.getType()))
                .next()
                .switchIfEmpty(Mono.error(new BusinessException(400, "请先创建账本")))
                .map(Ledger::getId);
    }

    /**
     * 校验用户是否有权限编辑该账单
     * 规则：自己的账单可直接编辑；他人的账单需账本 allow_member_edit=1 或用户是所有者/管理员
     */
    private Mono<Boolean> checkBillEditPermission(Long userId, Bill bill) {
        if (userId.equals(bill.getUserId())) {
            return Mono.just(true);
        }
        if (bill.getLedgerId() == null) {
            return Mono.just(false);
        }
        return ledgerRepository.findById(bill.getLedgerId())
                .flatMap(ledger -> {
                    if (Integer.valueOf(1).equals(ledger.getAllowMemberEdit())) {
                        return Mono.just(true);
                    }
                    return ledgerMemberRepository.findByLedgerIdAndUserId(bill.getLedgerId(), userId)
                            .map(member -> Integer.valueOf(1).equals(member.getRole()) || Integer.valueOf(2).equals(member.getRole()))
                            .defaultIfEmpty(false);
                })
                .defaultIfEmpty(false);
    }
}

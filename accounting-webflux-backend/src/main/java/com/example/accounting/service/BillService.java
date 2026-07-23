package com.example.accounting.service;

import com.example.accounting.common.BusinessException;
import com.example.accounting.common.PageResult;
import com.example.accounting.entity.BillDocument;
import com.example.accounting.dto.BillQueryRequest;
import com.example.accounting.dto.BillRequest;
import com.example.accounting.entity.Bill;
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
        return categoryRepository.findById(request.getCategoryId())
                .switchIfEmpty(Mono.error(new BusinessException(400, "分类不存在")))
                .flatMap(category -> {
                    if (!userId.equals(category.getUserId()) && !Integer.valueOf(1).equals(category.getIsPreset())) {
                        return Mono.error(new BusinessException("无权使用该分类"));
                    }
                    if (!request.getType().equals(category.getType())) {
                        return Mono.error(new BusinessException("账单类型与分类类型不一致"));
                    }
                    // 解析账本ID：指定则校验成员身份，未指定则使用默认个人账本
                    return resolveLedgerId(userId, request.getLedgerId());
                })
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
                            .flatMap(savedBill -> {
                                BillDocument document = BillDocument.builder()
                                        .mysqlId(savedBill.getId())
                                        .userId(savedBill.getUserId())
                                        .categoryId(savedBill.getCategoryId())
                                        .ledgerId(savedBill.getLedgerId())
                                        .amount(savedBill.getAmount())
                                        .type(savedBill.getType())
                                        .remark(savedBill.getRemark())
                                        .billDate(savedBill.getBillDate())
                                        .createdAt(savedBill.getCreatedAt())
                                        .updatedAt(savedBill.getUpdatedAt())
                                        .build();
                                return billDocumentRepository.save(document)
                                        .thenReturn(savedBill);
                            });
                });
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
        // 未指定账本时，查找用户的默认个人账本（type=1）
        return ledgerMemberRepository.findByUserId(userId)
                .flatMap(member -> ledgerRepository.findById(member.getLedgerId()))
                .filter(ledger -> Integer.valueOf(1).equals(ledger.getType()))
                .next()
                .switchIfEmpty(Mono.error(new BusinessException(400, "请先创建账本")))
                .map(Ledger::getId);
    }

    public Mono<Bill> updateBill(Long userId, Long billId, BillRequest request) {
        return billRepository.findById(billId)
                .switchIfEmpty(Mono.error(new BusinessException(404, "账单不存在或无权限")))
                .flatMap(bill -> {
                    // 校验账本成员修改权限
                    return checkBillEditPermission(userId, bill)
                            .flatMap(allowed -> {
                                if (!allowed) {
                                    return Mono.error(new BusinessException(403, "无权修改他人账单"));
                                }
                                return categoryRepository.findById(request.getCategoryId())
                                        .switchIfEmpty(Mono.error(new BusinessException("分类不存在")))
                                        .flatMap(category -> {
                                            if (!userId.equals(category.getUserId()) && !Integer.valueOf(1).equals(category.getIsPreset())) {
                                                return Mono.error(new BusinessException("无权使用该分类"));
                                            }
                                            if (!request.getType().equals(category.getType())) {
                                                return Mono.error(new BusinessException("账单类型与分类类型不一致"));
                                            }
                                            bill.setCategoryId(request.getCategoryId());
                                            bill.setAmount(request.getAmount());
                                            bill.setType(request.getType());
                                            bill.setRemark(request.getRemark());
                                            bill.setBillDate(request.getBillDate());
                                            bill.setUpdatedAt(LocalDateTime.now());
                                            return billRepository.save(bill)
                                                    .flatMap(savedBill -> billDocumentRepository.findByMysqlId(savedBill.getId())
                                                            .switchIfEmpty(Mono.defer(() -> {
                                                                // MongoDB 文档不存在时创建新文档，保证一致性
                                                                BillDocument newDoc = BillDocument.builder()
                                                                        .mysqlId(savedBill.getId())
                                                                        .userId(savedBill.getUserId())
                                                                        .categoryId(savedBill.getCategoryId())
                                                                        .ledgerId(savedBill.getLedgerId())
                                                                        .amount(savedBill.getAmount())
                                                                        .type(savedBill.getType())
                                                                        .remark(savedBill.getRemark())
                                                                        .billDate(savedBill.getBillDate())
                                                                        .createdAt(savedBill.getCreatedAt())
                                                                        .updatedAt(savedBill.getUpdatedAt())
                                                                        .build();
                                                                return Mono.just(newDoc);
                                                            }))
                                                            .flatMap(document -> {
                                                                document.setCategoryId(savedBill.getCategoryId());
                                                                document.setLedgerId(savedBill.getLedgerId());
                                                                document.setAmount(savedBill.getAmount());
                                                                document.setType(savedBill.getType());
                                                                document.setRemark(savedBill.getRemark());
                                                                document.setBillDate(savedBill.getBillDate());
                                                                document.setUpdatedAt(savedBill.getUpdatedAt());
                                                                return billDocumentRepository.save(document);
                                                            })
                                                            .thenReturn(savedBill));
                                        });
                            });
                });
    }

    public Mono<Void> deleteBill(Long userId, Long billId) {
        return billRepository.findById(billId)
                .switchIfEmpty(Mono.error(new BusinessException(404, "账单不存在或无权限")))
                .flatMap(bill -> {
                    // 校验账本成员修改权限
                    return checkBillEditPermission(userId, bill)
                            .flatMap(allowed -> {
                                if (!allowed) {
                                    return Mono.error(new BusinessException(403, "无权删除他人账单"));
                                }
                                return billRepository.delete(bill)
                                        .then(billDocumentRepository.findByMysqlId(billId)
                                                .flatMap(billDocumentRepository::delete)
                                                .then());
                            });
                });
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
                .map(tuple -> {
                    PageResult<Bill> result = PageResult.<Bill>builder()
                            .list(tuple.getT1())
                            .total(tuple.getT2())
                            .page(query.getPage())
                            .size(query.getSize())
                            .build();
                    return result;
                });
    }

    /**
     * 校验用户是否有权限编辑该账单
     * 规则：自己的账单可直接编辑；他人的账单需账本 allow_member_edit=1 或用户是所有者/管理员
     */
    private Mono<Boolean> checkBillEditPermission(Long userId, Bill bill) {
        // 自己的账单，直接允许
        if (userId.equals(bill.getUserId())) {
            return Mono.just(true);
        }
        // 他人的账单，检查账本权限
        if (bill.getLedgerId() == null) {
            return Mono.just(false);
        }
        return ledgerRepository.findById(bill.getLedgerId())
                .flatMap(ledger -> {
                    // allow_member_edit=1 允许成员修改
                    if (Integer.valueOf(1).equals(ledger.getAllowMemberEdit())) {
                        return Mono.just(true);
                    }
                    // allow_member_edit=0，检查是否为所有者/管理员
                    return ledgerMemberRepository.findByLedgerIdAndUserId(bill.getLedgerId(), userId)
                            .map(member -> Integer.valueOf(1).equals(member.getRole()) || Integer.valueOf(2).equals(member.getRole()))
                            .defaultIfEmpty(false);
                })
                .defaultIfEmpty(false);
    }
}

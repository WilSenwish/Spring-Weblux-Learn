package com.example.accounting.repository;

import com.example.accounting.entity.Category;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CategoryRepository extends ReactiveCrudRepository<Category, Long> {
    Flux<Category> findByUserIdOrIsPreset(Long userId, Integer isPreset);
    Flux<Category> findByUserIdAndType(Long userId, Integer type);
    Mono<Boolean> existsByUserIdAndNameAndType(Long userId, String name, Integer type);
    /** 检查同一账本下是否已存在同名同类型分类 */
    Mono<Boolean> existsByUserIdAndLedgerIdAndNameAndType(Long userId, Long ledgerId, String name, Integer type);
    /** 查询指定账本的自定义分类或预设分类 */
    Flux<Category> findByLedgerIdOrIsPreset(Long ledgerId, Integer isPreset);
}

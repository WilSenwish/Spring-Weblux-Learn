package com.example.accounting.service;

import com.example.accounting.common.BusinessException;
import com.example.accounting.dto.CategoryRequest;
import com.example.accounting.entity.Category;
import com.example.accounting.repository.CategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CategoryService {

    @Autowired
    private CategoryRepository categoryRepository;

    public Mono<Category> createCategory(Long userId, CategoryRequest request) {
        return checkCategoryExists(userId, request.getLedgerId(), request.getName(), request.getType())
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        return Mono.error(new BusinessException("该分类已存在"));
                    }
                    Category category = Category.builder()
                            .userId(userId)
                            .ledgerId(request.getLedgerId())
                            .name(request.getName())
                            .type(request.getType())
                            .isPreset(0)
                            .createdAt(LocalDateTime.now())
                            .build();
                    return categoryRepository.save(category);
                });
    }

    public Mono<Category> updateCategory(Long userId, Long categoryId, CategoryRequest request) {
        return categoryRepository.findById(categoryId)
                .switchIfEmpty(Mono.error(new BusinessException("分类不存在")))
                .filter(category -> userId.equals(category.getUserId()))
                .switchIfEmpty(Mono.error(new BusinessException("无权操作该分类")))
                .filter(category -> !Integer.valueOf(1).equals(category.getIsPreset()))
                .switchIfEmpty(Mono.error(new BusinessException("预设分类不允许修改")))
                .flatMap(category -> {
                    category.setName(request.getName());
                    category.setType(request.getType());
                    return categoryRepository.save(category);
                });
    }

    public Mono<Void> deleteCategory(Long userId, Long categoryId) {
        return categoryRepository.findById(categoryId)
                .switchIfEmpty(Mono.error(new BusinessException("分类不存在")))
                .filter(category -> userId.equals(category.getUserId()))
                .switchIfEmpty(Mono.error(new BusinessException("无权操作该分类")))
                .filter(category -> !Integer.valueOf(1).equals(category.getIsPreset()))
                .switchIfEmpty(Mono.error(new BusinessException("预设分类不允许删除")))
                .flatMap(categoryRepository::delete);
    }

    /**
     * 查询分类列表
     * @param userId 用户ID
     * @param ledgerId 账本ID（传值时查该账本自定义分类+预设分类，不传时查用户自定义分类+预设分类）
     * @param type 分类类型
     */
    public Mono<List<Category>> listCategories(Long userId, Long ledgerId, Integer type) {
        Flux<Category> flux = ledgerId != null
                ? categoryRepository.findByLedgerIdOrIsPreset(ledgerId, 1)
                : categoryRepository.findByUserIdOrIsPreset(userId, 1);
        if (type != null) {
            flux = flux.filter(c -> type.equals(c.getType()));
        }
        return flux.collectList();
    }

    private Mono<Boolean> checkCategoryExists(Long userId, Long ledgerId, String name, Integer type) {
        if (ledgerId == null) {
            return categoryRepository.existsByUserIdAndNameAndType(userId, name, type);
        }
        return categoryRepository.existsByUserIdAndLedgerIdAndNameAndType(userId, ledgerId, name, type);
    }
}

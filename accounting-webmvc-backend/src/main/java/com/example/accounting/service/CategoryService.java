package com.example.accounting.service;

import com.example.accounting.common.BusinessException;
import com.example.accounting.dto.CategoryRequest;
import com.example.accounting.entity.Category;
import com.example.accounting.mapper.CategoryMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CategoryService {

    @Autowired
    private CategoryMapper categoryMapper;

    public Category createCategory(Long userId, CategoryRequest request) {
        // 检查同账本下是否已存在同名同类型分类
        if (categoryMapper.existsByUserIdAndLedgerIdAndNameAndType(userId, request.getLedgerId(), request.getName(), request.getType())) {
            throw new BusinessException("该分类已存在");
        }
        Category category = Category.builder()
                .userId(userId)
                .ledgerId(request.getLedgerId())
                .name(request.getName())
                .type(request.getType())
                .isPreset(0)
                .createdAt(LocalDateTime.now())
                .build();
        categoryMapper.insert(category);
        return category;
    }

    public Category updateCategory(Long userId, Long categoryId, CategoryRequest request) {
        Category category = categoryMapper.findById(categoryId);
        if (category == null) {
            throw new BusinessException("分类不存在");
        }
        if (!userId.equals(category.getUserId())) {
            throw new BusinessException("无权操作该分类");
        }
        if (Integer.valueOf(1).equals(category.getIsPreset())) {
            throw new BusinessException("预设分类不允许修改");
        }
        category.setName(request.getName());
        category.setType(request.getType());
        categoryMapper.update(category);
        return category;
    }

    public void deleteCategory(Long userId, Long categoryId) {
        Category category = categoryMapper.findById(categoryId);
        if (category == null) {
            throw new BusinessException("分类不存在");
        }
        if (!userId.equals(category.getUserId())) {
            throw new BusinessException("无权操作该分类");
        }
        if (Integer.valueOf(1).equals(category.getIsPreset())) {
            throw new BusinessException("预设分类不允许删除");
        }
        categoryMapper.delete(categoryId);
    }

    /**
     * 查询分类列表
     * @param userId 用户ID
     * @param ledgerId 账本ID（传值时查该账本自定义分类+预设分类，不传时查用户自定义分类+预设分类）
     * @param type 分类类型
     */
    public List<Category> listCategories(Long userId, Long ledgerId, Integer type) {
        if (ledgerId != null) {
            return categoryMapper.findByLedgerIdOrPreset(ledgerId, type);
        }
        return categoryMapper.findByUserIdOrPreset(userId, type);
    }
}

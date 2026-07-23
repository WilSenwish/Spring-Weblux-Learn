package com.example.accounting.controller;

import com.example.accounting.dto.CategoryRequest;
import com.example.accounting.entity.Category;
import com.example.accounting.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController extends BaseController {

    @Autowired
    private CategoryService categoryService;

    @PostMapping
    public Category create(@Valid @RequestBody CategoryRequest request) {
        Long userId = getCurrentUserId();
        return categoryService.createCategory(userId, request);
    }

    @PutMapping("/{id}")
    public Category update(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        Long userId = getCurrentUserId();
        return categoryService.updateCategory(userId, id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        categoryService.deleteCategory(userId, id);
    }

    @GetMapping
    public List<Category> list(
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Long ledgerId) {
        Long userId = getCurrentUserId();
        return categoryService.listCategories(userId, ledgerId, type);
    }
}

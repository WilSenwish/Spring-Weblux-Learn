package com.example.accounting.controller;

import com.example.accounting.dto.CategoryRequest;
import com.example.accounting.entity.Category;
import com.example.accounting.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController extends BaseController {

    @Autowired
    private CategoryService categoryService;

    @PostMapping
    public Mono<Category> create(@Valid @RequestBody CategoryRequest request) {
        return getCurrentUserId()
                .flatMap(userId -> categoryService.createCategory(userId, request));
    }

    @PutMapping("/{id}")
    public Mono<Category> update(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return getCurrentUserId()
                .flatMap(userId -> categoryService.updateCategory(userId, id, request));
    }

    @DeleteMapping("/{id}")
    public Mono<Void> delete(@PathVariable Long id) {
        return getCurrentUserId()
                .flatMap(userId -> categoryService.deleteCategory(userId, id));
    }

    @GetMapping
    public Mono<List<Category>> list(
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Long ledgerId) {
        return getCurrentUserId()
                .flatMap(userId -> categoryService.listCategories(userId, ledgerId, type));
    }
}

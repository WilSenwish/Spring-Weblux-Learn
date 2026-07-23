package com.example.accounting.mapper;

import com.example.accounting.entity.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CategoryMapper {
    Category findById(Long id);
    List<Category> findByUserIdAndType(@Param("userId") Long userId, @Param("type") Integer type);
    List<Category> findByUserIdOrPreset(@Param("userId") Long userId, @Param("type") Integer type);
    boolean existsByUserIdAndNameAndType(@Param("userId") Long userId, @Param("name") String name, @Param("type") Integer type);
    /** 检查同一账本下是否已存在同名同类型分类 */
    boolean existsByUserIdAndLedgerIdAndNameAndType(@Param("userId") Long userId, @Param("ledgerId") Long ledgerId, @Param("name") String name, @Param("type") Integer type);
    /** 查询指定账本的自定义分类或预设分类 */
    List<Category> findByLedgerIdOrPreset(@Param("ledgerId") Long ledgerId, @Param("type") Integer type);
    void insert(Category category);
    void update(Category category);
    void delete(Long id);
}

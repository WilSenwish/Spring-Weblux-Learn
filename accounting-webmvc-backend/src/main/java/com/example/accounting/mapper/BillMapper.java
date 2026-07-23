package com.example.accounting.mapper;

import com.example.accounting.entity.Bill;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface BillMapper {
    Bill findById(Long id);
    List<Bill> findByUserId(@Param("userId") Long userId,
                            @Param("type") Integer type,
                            @Param("categoryId") Long categoryId,
                            @Param("ledgerId") Long ledgerId,
                            @Param("startDate") LocalDate startDate,
                            @Param("endDate") LocalDate endDate);
    void insert(Bill bill);
    void update(Bill bill);
    void delete(Long id);
    long countByUserId(Long userId);

    /** 查询所有账单，用于 MongoDB 数据同步 */
    List<Bill> findAll();
}

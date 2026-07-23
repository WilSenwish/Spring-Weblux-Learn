package com.example.accounting.mapper;

import com.example.accounting.entity.LedgerMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface LedgerMemberMapper {
    int insert(LedgerMember member);

    LedgerMember findById(Long id);

    List<LedgerMember> findByLedgerId(Long ledgerId);

    /**
     * 查询指定账本中指定用户的成员记录
     */
    LedgerMember findByLedgerIdAndUserId(@Param("ledgerId") Long ledgerId, @Param("userId") Long userId);

    List<LedgerMember> findByUserId(Long userId);

    int deleteByLedgerIdAndUserId(@Param("ledgerId") Long ledgerId, @Param("userId") Long userId);

    int deleteByLedgerId(Long ledgerId);
}

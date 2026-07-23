package com.example.accounting.mapper;

import com.example.accounting.entity.Ledger;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface LedgerMapper {
    int insert(Ledger ledger);

    Ledger findById(Long id);

    List<Ledger> findByOwnerId(Long ownerId);

    /**
     * 通过 ledger_member 关联查询用户参与的所有账本
     */
    List<Ledger> findByUserId(@Param("userId") Long userId);

    int update(Ledger ledger);

    int delete(Long id);
}

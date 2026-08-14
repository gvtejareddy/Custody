package com.bank.custody.position;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PositionRepository extends JpaRepository<Position, Long> {

    Optional<Position> findByAccountIdAndAssetId(Long accountId, String assetId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Position p where p.accountId = :accountId and p.assetId = :assetId")
    Optional<Position> findByAccountIdAndAssetIdForUpdate(@Param("accountId") Long accountId, @Param("assetId") String assetId);
}

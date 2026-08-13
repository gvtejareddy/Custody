package com.bank.custody.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {
    @Query("select o from OutboxEvent o where o.processed = false and (o.nextAttemptAt is null or o.nextAttemptAt <= :now)")
    List<OutboxEvent> findPending(@Param("now") OffsetDateTime now);
}

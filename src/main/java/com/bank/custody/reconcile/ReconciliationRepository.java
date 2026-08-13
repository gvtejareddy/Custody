package com.bank.custody.reconcile;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationRepository extends JpaRepository<ReconciliationResult, Long> {

}

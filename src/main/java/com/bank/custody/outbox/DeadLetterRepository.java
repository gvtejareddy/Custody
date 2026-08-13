package com.bank.custody.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeadLetterRepository extends JpaRepository<DeadLetter, Long> {

}

package com.bank.custody.providerevent;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ProviderEventRepository extends JpaRepository<ProviderEvent, Long> {
    Optional<ProviderEvent> findByProviderAndProviderEventId(String provider, String providerEventId);
}

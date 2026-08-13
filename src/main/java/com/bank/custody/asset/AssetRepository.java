package com.bank.custody.asset;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetRepository extends JpaRepository<Asset, String> {
}

package com.bank.custody.asset;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import java.util.List;

@RestController
@RequestMapping("/api/v1/assets")
public class AssetController {
    private final AssetRepository assetRepository;

    public AssetController(AssetRepository assetRepository) {
        this.assetRepository = assetRepository;
    }

    @GetMapping
    public List<Asset> list() {
        return assetRepository.findAll();
    }

    @PostMapping
    public ResponseEntity<Asset> add(@RequestBody Asset a) {
        Asset saved = assetRepository.save(a);
        return new ResponseEntity<>(saved, HttpStatus.CREATED);
    }
}

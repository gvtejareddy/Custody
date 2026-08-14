package com.bank.custody.asset;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import java.util.List;
import java.util.Map;

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
    public ResponseEntity<Asset> add(@RequestBody Map<String, Object> request) {
        Asset a = new Asset();
        a.setAssetId(string(request, "assetId", "symbol"));
        a.setDisplayName(string(request, "displayName", "name"));
        a.setNetwork(string(request, "network"));
        Object enabled = request.containsKey("enabled") ? request.get("enabled") : request.get("active");
        a.setEnabled(enabled == null || Boolean.parseBoolean(enabled.toString()));
        if (a.getAssetId() == null || a.getDisplayName() == null || a.getNetwork() == null) {
            return ResponseEntity.badRequest().build();
        }
        Asset saved = assetRepository.save(a);
        return new ResponseEntity<>(saved, HttpStatus.CREATED);
    }

    private String string(Map<String, Object> request, String... keys) {
        for (String key : keys) {
            Object value = request.get(key);
            if (value != null && !value.toString().isBlank()) return value.toString();
        }
        return null;
    }
}

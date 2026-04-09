package com.pochampally.controller;

import com.pochampally.entity.Policy;
import com.pochampally.service.PolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyService policyService;

    // --- Public ---

    @GetMapping("/api/policies")
    public ResponseEntity<List<Policy>> listPolicies() {
        return ResponseEntity.ok(policyService.listAll());
    }

    @GetMapping("/api/policies/{slug}")
    public ResponseEntity<Policy> getPolicy(@PathVariable String slug) {
        return ResponseEntity.ok(policyService.getBySlug(slug));
    }

    // --- Admin ---

    @PutMapping("/api/admin/policies/{slug}")
    public ResponseEntity<Policy> updatePolicy(@PathVariable String slug,
                                                @RequestBody Map<String, String> body) {
        Policy updated = policyService.update(
                slug,
                body.get("title"),
                body.get("metaDescription"),
                body.get("content")
        );
        return ResponseEntity.ok(updated);
    }
}

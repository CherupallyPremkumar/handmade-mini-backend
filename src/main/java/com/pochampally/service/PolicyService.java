package com.pochampally.service;

import com.pochampally.entity.Policy;
import com.pochampally.repository.PolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PolicyService {

    private final PolicyRepository policyRepository;

    public List<Policy> listAll() {
        return policyRepository.findAllByOrderBySlugAsc();
    }

    public Policy getBySlug(String slug) {
        return policyRepository.findById(slug)
                .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + slug));
    }

    @Transactional
    public Policy update(String slug, String title, String metaDescription, String content) {
        Policy policy = policyRepository.findById(slug)
                .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + slug));

        if (title != null && !title.isBlank()) policy.setTitle(title.trim());
        if (metaDescription != null) policy.setMetaDescription(metaDescription.trim());
        if (content != null && !content.isBlank()) policy.setContent(content);

        Policy saved = policyRepository.save(policy);
        log.info("Policy updated: {}", slug);
        return saved;
    }
}

package com.example.cfchat.service;

import com.example.cfchat.model.ModelAccessRule;
import com.example.cfchat.model.ModelInfo;
import com.example.cfchat.repository.ModelAccessRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModelAccessService {

    private final ModelAccessRuleRepository ruleRepository;

    @Value("${app.model-access-control.enabled:false}")
    private boolean accessControlEnabled;

    public boolean isAccessControlEnabled() {
        return accessControlEnabled;
    }

    /**
     * Filter models based on user's role. Admins always see all models.
     */
    public List<ModelInfo> filterModelsForRole(List<ModelInfo> allModels, String role) {
        if (!accessControlEnabled || "ADMIN".equalsIgnoreCase(role)) {
            return allModels;
        }

        Set<String> allowedIds = ruleRepository.findAllowedModelIds(role)
                .stream().collect(Collectors.toSet());

        // If no rules exist for this role, show all models (permissive default)
        if (allowedIds.isEmpty()) {
            return allModels;
        }

        return allModels.stream()
                .filter(m -> allowedIds.contains(m.getId()))
                .toList();
    }

    @Transactional
    public ModelAccessRule addRule(String modelId, String roleName, boolean allowed) {
        ModelAccessRule rule = ModelAccessRule.builder()
                .modelId(modelId)
                .roleName(roleName)
                .allowed(allowed)
                .build();
        return ruleRepository.save(rule);
    }

    @Transactional
    public void removeRule(String modelId, String roleName) {
        ruleRepository.deleteByModelIdAndRoleName(modelId, roleName);
    }

    @Transactional(readOnly = true)
    public List<ModelAccessRule> getRulesForModel(String modelId) {
        return ruleRepository.findByModelId(modelId);
    }

    @Transactional(readOnly = true)
    public List<ModelAccessRule> getAllRules() {
        return ruleRepository.findAll();
    }
}

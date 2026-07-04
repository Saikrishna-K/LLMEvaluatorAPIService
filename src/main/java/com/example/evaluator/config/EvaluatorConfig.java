package com.example.evaluator.config;

import com.example.evaluator.evaluation.ActionFieldEvaluator;
import com.example.evaluator.evaluation.JsonEqualityEvaluator;
import com.example.evaluator.evaluation.ResponseEvaluator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EvaluatorConfig {

    /**
     * Produces exactly one ResponseEvaluator bean, selected by shadow.evaluation-mode.
     * Switches the active evaluation phase without any code change.
     */
    @Bean
    public ResponseEvaluator activeEvaluator(ShadowProperties props, ObjectMapper objectMapper) {
        if ("json-equality".equalsIgnoreCase(props.evaluationMode())) {
            return new JsonEqualityEvaluator(objectMapper);
        }
        return new ActionFieldEvaluator(objectMapper);
    }
}

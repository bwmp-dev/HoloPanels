package dev.aether.holopanels.api;

@FunctionalInterface
public interface ConditionEvaluator {
    boolean test(ConditionContext context);
}

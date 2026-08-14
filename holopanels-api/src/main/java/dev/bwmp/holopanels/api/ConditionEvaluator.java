package dev.bwmp.holopanels.api;

@FunctionalInterface
public interface ConditionEvaluator {
    boolean test(ConditionContext context);
}

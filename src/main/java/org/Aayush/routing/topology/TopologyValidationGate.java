package org.Aayush.routing.topology;

/**
 * Validation hook executed before a rebuilt topology candidate is published.
 */
@FunctionalInterface
public interface TopologyValidationGate {
    void validate(TopologyValidationContext context);
}

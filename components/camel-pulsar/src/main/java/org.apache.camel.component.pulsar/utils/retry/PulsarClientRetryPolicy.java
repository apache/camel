package org.apache.camel.component.pulsar.utils.retry;

public interface PulsarClientRetryPolicy {
    void retry();
}

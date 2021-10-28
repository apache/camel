package org.apache.camel.support;

import java.util.Map;

import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckConfiguration;
import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.util.URISupport;

public class ScheduledPollConsumerHealthCheck implements HealthCheck {

    public static final String FAILURE_COUNT = "failure.count";
    public static final String ROUTE = "route.id";

    private final HealthCheckConfiguration configuration = new HealthCheckConfiguration();
    private final ScheduledPollConsumer consumer;
    private final String id;

    public ScheduledPollConsumerHealthCheck(ScheduledPollConsumer consumer, String id) {
        this.consumer = consumer;
        this.id = id;
    }

    @Override
    public HealthCheckConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public Result call(Map<String, Object> options) {
        final HealthCheckResultBuilder builder = HealthCheckResultBuilder.on(this);
        builder.detail(ROUTE, consumer.getRouteId());

        long ec = consumer.getErrorCounter();
        Throwable cause = consumer.getLastError();

        boolean healthy = ec == 0 || ec < configuration.getFailureThreshold();
        if (healthy) {
            builder.up();
        } else {
            builder.down();
            builder.detail(FAILURE_COUNT, ec);
            String uri = consumer.getEndpoint().getEndpointBaseUri();
            uri = URISupport.sanitizeUri(uri);
            String rid = consumer.getRouteId();
            String msg = "Consumer failed polling %s times route: %s (%s)";
            builder.message(String.format(msg, ec, rid, uri));
            builder.error(cause);
        }

        return builder.build();
    }

    @Override
    public String getGroup() {
        return "camel";
    }

    @Override
    public String getId() {
        return id;
    }
}

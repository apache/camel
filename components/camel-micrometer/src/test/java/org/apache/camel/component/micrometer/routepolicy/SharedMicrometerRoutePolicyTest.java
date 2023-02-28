package org.apache.camel.component.micrometer.routepolicy;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.micrometer.MicrometerConstants;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SharedMicrometerRoutePolicyTest extends CamelTestSupport {

    protected MeterRegistry meterRegistry = new SimpleMeterRegistry();

    protected MicrometerRoutePolicy singletonPolicy = new MicrometerRoutePolicy();

    @Test
    public void testSharedPolicy() throws Exception {
        template.request("direct:foo", x -> {});
        template.request("direct:bar", x -> {});
        List<Meter> meters = meterRegistry.getMeters();
        long timers = meters.stream()
                .filter(it -> it instanceof Timer)
                .count();
        assertEquals(2L, timers, "timers count incorrect");
    }

    @BindToRegistry(MicrometerConstants.METRICS_REGISTRY_NAME)
    public MeterRegistry addRegistry() {
        return meterRegistry;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:foo").routeId("foo").routePolicy(singletonPolicy)
                        .to("mock:result");

                from("direct:bar").routeId("bar").routePolicy(singletonPolicy)
                        .to("mock:result");
            }
        };
    }
}

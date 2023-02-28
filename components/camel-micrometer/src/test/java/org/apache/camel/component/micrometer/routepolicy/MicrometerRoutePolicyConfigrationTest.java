package org.apache.camel.component.micrometer.routepolicy;

import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MicrometerRoutePolicyConfigrationTest extends AbstractMicrometerRoutePolicyTest {

    @Override
    protected MicrometerRoutePolicyFactory createMicrometerRoutePolicyFactory() {
        MicrometerRoutePolicyFactory factory = new MicrometerRoutePolicyFactory();
        MicrometerRoutePolicyConfiguration policyConfiguration = new MicrometerRoutePolicyConfiguration();
        policyConfiguration.setExchangesSucceeded(false);
        policyConfiguration.setExchangesFailed(false);
        policyConfiguration.setExchangesTotal(false);
        policyConfiguration.setExternalRedeliveries(false);
        policyConfiguration.setFailuresHandled(false);
        policyConfiguration.setTimerInitiator(builder ->
            builder.tags("firstTag", "hello", "secondTag", "world")
                    .description("Test Description")
        );
        policyConfiguration.setLongTask(true);
        policyConfiguration.setLongTaskInitiator(builder -> builder.description("Test long task"));
        factory.setPolicyConfiguration(policyConfiguration);
        return factory;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:foo").routeId("foo").delay(1);
            }
        };
    }

    @Test
    public void testConfigurationPolicy() throws Exception {
        template.request("direct:foo", x -> {});
        List<Meter> meters = meterRegistry.getMeters();
        assertEquals(2, meters.size(), "additional counters does not disable");
        Timer timer = (Timer) meters.stream().filter(it -> it instanceof Timer)
                        .findFirst().orElse(null);

        assertNotNull(timer, "timer is null");
        Meter.Id id = timer.getId();
        assertEquals("Test Description", id.getDescription(), "incorrect description");
        assertEquals("hello", id.getTag("firstTag"), "firstTag not setted");
        assertEquals("world", id.getTag("secondTag"), "secondTag not setted");

        LongTaskTimer longTaskTimer = (LongTaskTimer) meters.stream().filter(it -> it instanceof LongTaskTimer)
                .findFirst().orElse(null);
        assertNotNull(longTaskTimer, "LongTaskTimer is null");
        id = longTaskTimer.getId();
        assertEquals("Test long task", id.getDescription(), "incorrect long task description");
    }

}

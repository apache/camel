package org.apache.camel.impl.health;

import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class ContextHealthCheckTest {

    @Test
    public void testOverallStatusOnceTheContextIsStarted() {
        CamelContext context = new DefaultCamelContext();
        context.start();

        ContextHealthCheck check = new ContextHealthCheck();
        check.setCamelContext(context);

        HealthCheck.Result result = check.call();

        assertEquals(HealthCheck.State.UP, result.getState());
        assertFalse(result.getMessage().isPresent());
        assertFalse(result.getDetails().containsKey(AbstractHealthCheck.CHECK_ENABLED));
        assertEquals(ServiceStatus.Started, result.getDetails().get("context.status"));
    }

    @Test
    public void testOverallStatusOnceTheContextIsSuspended() {
        CamelContext context = new DefaultCamelContext();
        context.suspend();

        ContextHealthCheck check = new ContextHealthCheck();
        check.setCamelContext(context);

        HealthCheck.Result result = check.call();

        assertEquals(HealthCheck.State.DOWN, result.getState());
        assertTrue(result.getMessage().isPresent());
        assertFalse(result.getDetails().containsKey(AbstractHealthCheck.CHECK_ENABLED));
        assertEquals(ServiceStatus.Suspended, result.getDetails().get("context.status"));
    }


}

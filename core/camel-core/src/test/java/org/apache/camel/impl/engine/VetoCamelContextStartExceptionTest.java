package org.apache.camel.impl.engine;

import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.VetoCamelContextStartException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.LifecycleStrategySupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VetoCamelContextStartExceptionTest {

    @Test
    public void testVetoOnStarting() {
        CamelContext context = new DefaultCamelContext();
        context.addLifecycleStrategy(
                new LifecycleStrategySupport() {
                    @Override
                    public void onContextStarting(CamelContext context) throws VetoCamelContextStartException {
                        throw new VetoCamelContextStartException("Veto on starting", context, true);
                    }
                });

        Throwable throwable = assertThrows(Throwable.class, context::start);

        assertTrue(context.isVetoStarted());
        assertEquals(ServiceStatus.Stopped, context.getStatus());
        assertEquals("org.apache.camel.VetoCamelContextStartException: Veto on starting", throwable.getMessage());
    }

    @Test
    public void testVetoOnStartingWithoutRethrow() {
        CamelContext context = new DefaultCamelContext();
        context.addLifecycleStrategy(
                new LifecycleStrategySupport() {
                    @Override
                    public void onContextStarting(CamelContext context) throws VetoCamelContextStartException {
                        throw new VetoCamelContextStartException("Veto on starting", context, false);
                    }
                });
        context.start();
        assertTrue(context.isVetoStarted());
        assertEquals(ServiceStatus.Stopped, context.getStatus());
    }

    @Test
    public void testVetoOnInitializing() {
        CamelContext context = new DefaultCamelContext();
        context.addLifecycleStrategy(
                new LifecycleStrategySupport() {
                    @Override
                    public void onContextInitializing(CamelContext context) throws VetoCamelContextStartException {
                        throw new VetoCamelContextStartException("Veto on initializing", context, true);
                    }
                });

        Throwable throwable = assertThrows(Throwable.class, context::start);
        assertTrue(context.isVetoStarted());
        assertEquals(ServiceStatus.Stopped, context.getStatus());
        assertEquals("org.apache.camel.VetoCamelContextStartException: Veto on initializing", throwable.getMessage());
    }

    @Test
    public void testVetoOnInitializingWithoutRethrow() {
        CamelContext context = new DefaultCamelContext();
        context.addLifecycleStrategy(
                new LifecycleStrategySupport() {
                    @Override
                    public void onContextInitializing(CamelContext context) throws VetoCamelContextStartException {
                        throw new VetoCamelContextStartException("Veto on initializing", context, false);
                    }
                });
        context.start();
        assertTrue(context.isVetoStarted());
        assertEquals(ServiceStatus.Stopped, context.getStatus());
    }

    @Test
    public void testVetoOnInitialized() {
        CamelContext context = new DefaultCamelContext();
        context.addLifecycleStrategy(
                new LifecycleStrategySupport() {
                    @Override
                    public void onContextInitialized(CamelContext context) throws VetoCamelContextStartException {
                        throw new VetoCamelContextStartException("Veto on initialized", context, true);
                    }
                });

        Throwable throwable = assertThrows(Throwable.class, context::start);
        assertTrue(context.isVetoStarted());
        assertEquals(ServiceStatus.Stopped, context.getStatus());
        assertEquals("org.apache.camel.VetoCamelContextStartException: Veto on initialized", throwable.getMessage());
    }

    @Test
    public void testVetoOnInitializedWithoutRethrow() {
        CamelContext context = new DefaultCamelContext();
        context.addLifecycleStrategy(
                new LifecycleStrategySupport() {
                    @Override
                    public void onContextInitialized(CamelContext context) throws VetoCamelContextStartException {
                        throw new VetoCamelContextStartException("Veto on initialized", context, false);
                    }
                });
        context.start();
        assertTrue(context.isVetoStarted());
        assertEquals(ServiceStatus.Stopped, context.getStatus());
    }

    @Test
    public void testVetoReset() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            boolean[] needThrowVeto = new boolean[] { true };
            context.addLifecycleStrategy(
                    new LifecycleStrategySupport() {
                        @Override
                        public void onContextInitialized(CamelContext context) throws VetoCamelContextStartException {
                            if (needThrowVeto[0]) {
                                needThrowVeto[0] = false;
                                throw new VetoCamelContextStartException("Veto on initialized", context, false);
                            }
                        }
                    });

            context.start();
            assertTrue(context.isVetoStarted());
            assertEquals(ServiceStatus.Stopped, context.getStatus());

            context.start();
            assertFalse(context.isVetoStarted());
            assertEquals(ServiceStatus.Started, context.getStatus());
        }

    }
}

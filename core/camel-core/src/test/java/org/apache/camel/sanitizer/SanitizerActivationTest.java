/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.sanitizer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.PooledExchangeFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.ClearSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When the sanitizer is switched on: profile dev, camel.sanitizer.enabled, a context built on a thread marked by the
 * JUnit extension, never profile prod; and its options. The contexts are built on a thread of their own, which a test
 * marks or not, so this class gives the same results with the CI switch on (whose system property is cleared here). The
 * contexts that break the rule are tied to a session of their own, so their violations are not charged to the test
 * classes that run at the same time (see {@link SanitizerUntiedContextTest}).
 */
@ClearSystemProperty(key = "camel.sanitizer.enabled")
class SanitizerActivationTest {

    private final ExecutorService builder = Executors.newSingleThreadExecutor(r -> new Thread(r, "context-builder"));
    private CamelContext context;

    @AfterEach
    void stop() {
        if (context != null) {
            context.stop();
        }
        builder.shutdownNow();
    }

    @Test
    void offByDefault() throws Exception {
        start(Map.of(), null, null);
        assertNull(Sanitizer.get(context));
        assertTrue(context.getCamelContextExtension().getStepListeners().isEmpty());
    }

    @Test
    void onWithEnabled() throws Exception {
        start(Map.of("camel.sanitizer.enabled", "true"), null, null);
        assertNotNull(Sanitizer.get(context));
        assertEquals(1, context.getCamelContextExtension().getStepListeners().size());
    }

    @Test
    void onWithProfileDev() throws Exception {
        start(Map.of(), "dev", null);
        assertNotNull(Sanitizer.get(context));
    }

    @Test
    void offWithProfileProdAlsoWhenEnabledOrMarked() throws Exception {
        SanitizerTestSession session = SanitizerTestSession.open("prod");
        try {
            start(Map.of("camel.sanitizer.enabled", "true"), "prod", session);
            assertNull(Sanitizer.get(context));
            assertTrue(context.getCamelContextExtension().getStepListeners().isEmpty());
        } finally {
            session.close();
        }
    }

    @Test
    void onWhenBuiltOnAMarkedThreadAndTiedToItsSession() throws Exception {
        SanitizerTestSession session = SanitizerTestSession.open("marked");
        try {
            start(Map.of(), null, session);
            assertNotNull(Sanitizer.get(context));
            sendToBrokenRoute();
            List<SanitizerViolation> charged = session.drain();
            assertEquals(1, charged.size());
            assertEquals(Violation.THREW_BEFORE_CALLBACK, charged.get(0).getKind());
            assertEquals("broken", charged.get(0).getStepId());
        } finally {
            session.close();
        }
    }

    @Test
    void enabledFalseWinsOverTheMark() throws Exception {
        SanitizerTestSession session = SanitizerTestSession.open("off");
        try {
            start(Map.of("camel.sanitizer.enabled", "false"), "dev", session);
            assertNull(Sanitizer.get(context));
        } finally {
            session.close();
        }
    }

    @Test
    void invalidOptionsStopTheStart() {
        Exception e = assertThrows(Exception.class,
                () -> start(Map.of("camel.sanitizer.enabled", "true", "camel.sanitizer.checks", "callback,threads"), null,
                        null));
        assertTrue(messages(e).contains("camel.sanitizer.checks=callback,threads has the unknown check threads"),
                messages(e));
        e = assertThrows(Exception.class,
                () -> start(Map.of("camel.sanitizer.enabled", "true", "camel.sanitizer.stackDepth", "-1"), null, null));
        assertTrue(messages(e).contains("camel.sanitizer.stackDepth=-1 must be 0 or more"), messages(e));
    }

    @Test
    void noCheckInstallsNothing() throws Exception {
        start(Map.of("camel.sanitizer.enabled", "true", "camel.sanitizer.checks", "none"), null, null);
        assertNull(Sanitizer.get(context));
        assertTrue(context.getCamelContextExtension().getStepListeners().isEmpty());
    }

    @Test
    void stackDepthZeroKeepsOnlyTheThreadNames() throws Exception {
        SanitizerTestSession session = SanitizerTestSession.open("stackDepth");
        try {
            start(Map.of("camel.sanitizer.stackDepth", "0"), null, session);
            sendToBrokenRoute();
            String text = session.drain().get(0).getText();
            assertTrue(text.contains("process threw java.lang.IllegalStateException: Forced by ThrowsInProcess: thread "),
                    text);
            assertFalse(text.contains("      at "), text);
            assertTrue(Sanitizer.get(context).report().contains("process threw before calling the callback: 1"));
        } finally {
            session.close();
        }
    }

    @Test
    void offWithPooledExchanges() throws Exception {
        SanitizerTestSession session = SanitizerTestSession.open("pooled");
        try {
            start(Map.of(), null, session,
                    c -> c.getCamelContextExtension().setExchangeFactory(new PooledExchangeFactory()));
            assertNull(Sanitizer.get(context));
            // the expected violations of a test whose contexts stay off on purpose are not required
            assertTrue(session.isTurnedOff());
            assertFalse(session.isInstalled());
        } finally {
            session.close();
        }
    }

    @Test
    void stillOnAfterTheContextIsStoppedAndStartedAgain() throws Exception {
        SanitizerTestSession session = SanitizerTestSession.open("restart");
        try {
            start(Map.of(), null, session);
            Sanitizer sanitizer = Sanitizer.get(context);
            assertNotNull(sanitizer);
            context.stop();
            context.start();
            assertSame(sanitizer, Sanitizer.get(context));
            assertEquals(1, context.getCamelContextExtension().getStepListeners().size());
            sendToBrokenRoute();
            assertEquals(1, session.drain().size());
        } finally {
            session.close();
        }
    }

    private void sendToBrokenRoute() {
        Exchange out = context.createProducerTemplate().send("direct:broken", e -> e.getMessage().setBody("x"));
        assertNotNull(out.getException());
    }

    /**
     * Builds and starts a context on the builder thread, marked with the session if one is given.
     */
    private void start(Map<String, String> options, String profile, SanitizerTestSession session) throws Exception {
        start(options, profile, session, c -> {
        });
    }

    private void start(
            Map<String, String> options, String profile, SanitizerTestSession session, Consumer<CamelContext> customizer)
            throws Exception {
        Future<CamelContext> f = builder.submit(() -> {
            SanitizerTestSession before = session != null ? session.mark() : null;
            CamelContext c = new DefaultCamelContext();
            try {
                options.forEach((k, v) -> c.getPropertiesComponent().addOverrideProperty(k, v));
                if (profile != null) {
                    c.getCamelContextExtension().setProfile(profile);
                }
                customizer.accept(c);
                c.addRoutes(new RouteBuilder() {
                    @Override
                    public void configure() {
                        from("direct:broken").process(new BrokenUserCode.ThrowsInProcess()).id("broken");
                    }
                });
                context = c;
                c.start();
                return c;
            } finally {
                if (session != null) {
                    SanitizerTestSession.restore(before);
                }
            }
        });
        try {
            f.get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw (Exception) e.getCause();
        }
    }

    private static String messages(Throwable e) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause()) {
            sb.append(t.getMessage()).append(" | ");
        }
        return sb.toString();
    }
}

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
package org.apache.camel.main;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultRouteController;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// uses JVM system properties and captures logging, so it must not run in parallel with other tests
@Isolated
@ResourceLock(Resources.SYSTEM_PROPERTIES)
public class MainBootstrapEdgeCasesTest {

    private static class CountingRouteController extends DefaultRouteController {
        private final AtomicInteger stopAll = new AtomicInteger();

        @Override
        public void stopAllRoutes() throws Exception {
            stopAll.incrementAndGet();
            super.stopAllRoutes();
        }
    }

    private static class MyRoute extends RouteBuilder {
        @Override
        public void configure() {
            from("timer:foo?period=60000").routeId("foo").to("log:foo");
        }
    }

    @Test
    public void testDevProfileKeepsOptionInLowerCase() {
        Main main = new Main();
        main.configure().withProfile("dev");
        // the ENV variable CAMEL_MAIN_SHUTDOWNTIMEOUT is in lower case
        main.addInitialProperty("camel.main.shutdowntimeout", "5");
        main.addInitialProperty("camel.main.message-history", "false");
        main.start();
        try {
            assertEquals(5, main.getCamelContext().getShutdownStrategy().getTimeout());
            assertFalse(main.getCamelContext().isMessageHistory());
        } finally {
            main.stop();
        }
    }

    @Test
    public void testSystemPropertyOverridesLowerCaseOption() {
        Main main = new Main();
        main.addInitialProperty("camel.main.shutdowntimeout", "11");
        System.setProperty("camel.main.shutdownTimeout", "22");
        try {
            main.start();
            assertEquals(22, main.getCamelContext().getShutdownStrategy().getTimeout());
        } finally {
            System.clearProperty("camel.main.shutdownTimeout");
            main.stop();
        }
    }

    @Test
    public void testSystemPropertyProfileWins() {
        Main main = new Main();
        main.configure().withProfile("dev");
        System.setProperty("camel.main.profile", "prod");
        try {
            main.start();
            List<String> locations = main.getCamelContext().getPropertiesComponent().getLocations();
            assertTrue(locations.stream().anyMatch(l -> l.contains("application-prod")), locations.toString());
            assertFalse(locations.stream().anyMatch(l -> l.contains("application-dev")), locations.toString());
        } finally {
            System.clearProperty("camel.main.profile");
            main.stop();
        }
    }

    @Test
    public void testSslConfiguredOnce() {
        List<String> messages = new CopyOnWriteArrayList<>();
        String loggerName = BaseMainSupport.class.getName();
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        AbstractAppender appender = new AbstractAppender("MainBootstrapEdgeCasesTest", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                messages.add(event.getMessage().getFormattedMessage());
            }
        };
        appender.start();
        LoggerConfig loggerConfig = new LoggerConfig(loggerName, Level.INFO, true);
        loggerConfig.addAppender(appender, Level.INFO, null);
        config.addLogger(loggerName, loggerConfig);
        ctx.updateLoggers();

        Main main = new Main();
        main.addInitialProperty("camel.ssl.enabled", "true");
        main.addInitialProperty("camel.ssl.selfSigned", "true");
        try {
            main.start();
            long count = messages.stream().filter(m -> m.startsWith("Generating self-signed SSL certificate")).count();
            assertEquals(1, count, messages.toString());
        } finally {
            main.stop();
            config.removeLogger(loggerName);
            ctx.updateLoggers();
            appender.stop();
        }
    }

    @Test
    public void testModelineUsesExcludePatternFromProperties() {
        AtomicReference<String> exclude = new AtomicReference<>();
        Main main = new Main() {
            @Override
            protected void modelineRoutes(CamelContext camelContext) throws Exception {
                exclude.set(mainConfigurationProperties.getRoutesExcludePattern());
                super.modelineRoutes(camelContext);
            }
        };
        main.addInitialProperty("camel.main.routesExcludePattern", "**/broken.yaml");
        main.start();
        try {
            assertEquals("**/broken.yaml", exclude.get());
        } finally {
            main.stop();
        }
    }

    @Test
    public void testDurationMaxSecondsMinusOneWithStopAction() throws Exception {
        CountingRouteController rc = new CountingRouteController();
        Main main = new Main() {
            @Override
            protected CamelContext createCamelContext() {
                CamelContext answer = super.createCamelContext();
                answer.setRouteController(rc);
                return answer;
            }
        };
        main.configure().addRoutesBuilder(new MyRoute());
        main.configure().withDurationMaxSeconds(-1);
        main.configure().withDurationMaxAction("stop");

        Thread t = new Thread(() -> {
            try {
                main.run();
            } catch (Exception e) {
                // ignore
            }
        });
        t.start();
        try {
            await().atMost(Duration.ofSeconds(20)).until(() -> rc.stopAll.get() >= 1);
            // stopping the routes is done once (and not in a busy loop)
            await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(5)).until(() -> rc.stopAll.get() == 1);
        } finally {
            main.completed();
            t.join(20000);
        }
    }

    @Test
    public void testDurationEventNotifierStopsIdleExecutor() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.start();
        try {
            MainDurationEventNotifier notifier = new MainDurationEventNotifier(
                    context, 0, 5, new SimpleMainShutdownStrategy(), false, false, "shutdown");
            notifier.start();
            Field field = MainDurationEventNotifier.class.getDeclaredField("idleExecutorService");
            field.setAccessible(true);
            ScheduledExecutorService executor = (ScheduledExecutorService) field.get(notifier);
            assertFalse(executor.isShutdown());
            notifier.stop();
            assertTrue(executor.isShutdown());
        } finally {
            context.stop();
        }
    }

    @Test
    public void testDurationIdleWithStopActionTriggersOnce() throws Exception {
        CountingRouteController rc = new CountingRouteController();
        CamelContext context = new DefaultCamelContext();
        context.setRouteController(rc);
        MainDurationEventNotifier notifier = new MainDurationEventNotifier(
                context, 0, 1, new SimpleMainShutdownStrategy(), false, false, "stop");
        notifier.start();
        context.start();
        try {
            await().atMost(Duration.ofSeconds(20)).until(() -> rc.stopAll.get() >= 1);
            await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(6)).until(() -> rc.stopAll.get() == 1);
        } finally {
            notifier.stop();
            context.stop();
        }
    }

    @Test
    public void testDurationActionIgnoresCase() throws Exception {
        CamelContext context = new DefaultCamelContext();
        MainDurationEventNotifier notifier = new MainDurationEventNotifier(
                context, 10, 0, new SimpleMainShutdownStrategy(), false, false, "STOP");
        assertDoesNotThrow(notifier::start);
        notifier.stop();
    }
}

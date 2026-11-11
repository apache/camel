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

import java.util.Collection;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.VetoCamelContextStartException;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.ContextServicePlugin;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.support.LifecycleStrategySupport;
import org.apache.camel.support.component.PropertyConfigurerSupport;
import org.apache.camel.util.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Switches the exchange sanitizer on in a {@link CamelContext}, found on the classpath ({@code META-INF/services}).
 * <p/>
 * When the context initializes, before its routes are created, the plugin reads the {@code camel.sanitizer.*} options
 * from the properties component, and installs the sanitizer only with profile {@code dev}, with
 * {@code camel.sanitizer.enabled=true}, or for a context built on a thread that the JUnit extension marked
 * ({@link SanitizerTestSession}), and never with profile {@code prod} (checked first). Otherwise nothing is installed
 * but the lifecycle strategy that decides it. When it installs, it adds the step listener of rule R1 and logs a WARN
 * that the sanitizer is on. For the still pending warning of R1 it also adds an event notifier of the exchanges that
 * are done, and looks for still pending calls while the context runs.
 *
 * @since 4.24
 */
public class SanitizerContextServicePlugin implements ContextServicePlugin {

    static final String ENABLED = "camel.sanitizer.enabled";
    static final String CHECKS = "camel.sanitizer.checks";
    static final String STACK_DEPTH = "camel.sanitizer.stackDepth";
    static final String REPORT_ON_SHUTDOWN = "camel.sanitizer.reportOnShutdown";
    static final String PENDING_TIMEOUT = "camel.sanitizer.pendingTimeout";

    /**
     * The checks of this version: {@code callback} is rule R1.
     */
    static final String CHECK_CALLBACK = "callback";

    private static final Logger LOG = LoggerFactory.getLogger(SanitizerContextServicePlugin.class);

    // in tests the WARN that the sanitizer is on is logged once per JVM, then at DEBUG
    private static final AtomicBoolean WARNED_IN_TESTS = new AtomicBoolean();

    @Override
    public void load(CamelContext camelContext) {
        // the plugin is loaded again when the context starts again; the context is initialized only once
        for (LifecycleStrategy strategy : camelContext.getLifecycleStrategies()) {
            if (strategy instanceof Activation) {
                return;
            }
        }
        // the plugin is loaded on the thread that builds the context, which a test marked with its session
        camelContext.addLifecycleStrategy(new Activation(SanitizerTestSession.current()));
    }

    /**
     * Decides when the context initializes whether the sanitizer is switched on, installs it, and logs the report when
     * the context stops.
     */
    static final class Activation extends LifecycleStrategySupport {

        private final SanitizerTestSession builtBy;
        private volatile Sanitizer sanitizer;
        private volatile boolean reportOnShutdown;

        Activation(SanitizerTestSession builtBy) {
            this.builtBy = builtBy;
        }

        @Override
        public void onContextInitializing(CamelContext context) throws VetoCamelContextStartException {
            if (sanitizer != null) {
                return;
            }
            SanitizerTestSession session = builtBy != null ? builtBy : SanitizerTestSession.current();
            PropertiesComponent pc = context.getPropertiesComponent();
            String profile = context.getCamelContextExtension().getProfile();
            if ("prod".equalsIgnoreCase(profile)) {
                // checked first, so no option that the plugin reads can stop an application in prod
                if (session != null || "true".equalsIgnoreCase(property(pc, ENABLED))) {
                    LOG.warn("camel-sanitizer is on the classpath but stays off with profile prod");
                }
                turnedOff(session);
                return;
            }
            Boolean enabled;
            int stackDepth;
            long pendingTimeout;
            boolean callback;
            try {
                String text = property(pc, ENABLED);
                enabled = text != null ? parseBoolean(ENABLED, text) : null;
                boolean on = enabled != null ? enabled : session != null || "dev".equalsIgnoreCase(profile);
                if (!on) {
                    turnedOff(session);
                    LOG.debug("camel-sanitizer is on the classpath but off (it is switched on with profile dev, {}=true"
                              + " or @CamelSanitizer)",
                            ENABLED);
                    return;
                }
                callback = parseChecks(orElse(property(pc, CHECKS), CHECK_CALLBACK));
                stackDepth = parseStackDepth(orElse(property(pc, STACK_DEPTH), "32"));
                pendingTimeout = parsePendingTimeout(orElse(property(pc, PENDING_TIMEOUT), "60s"));
                reportOnShutdown = parseBoolean(REPORT_ON_SHUTDOWN, orElse(property(pc, REPORT_ON_SHUTDOWN), "true"));
            } catch (IllegalArgumentException e) {
                throw new VetoCamelContextStartException("camel-sanitizer: " + e.getMessage(), e, context);
            }
            if (context.getCamelContextExtension().getExchangeFactory().isPooled()) {
                LOG.warn("camel-sanitizer stays off: it does not support pooled exchanges (exchangeFactory=pooled)");
                turnedOff(session);
                return;
            }
            if (!callback) {
                LOG.info("camel-sanitizer is switched on with no check in {}, so nothing is installed", CHECKS);
                turnedOff(session);
                return;
            }
            Sanitizer s = new Sanitizer(context, stackDepth, session, pendingTimeout);
            if (session != null) {
                session.installed();
            }
            context.getCamelContextExtension().addContextPlugin(Sanitizer.class, s);
            context.getCamelContextExtension().addStepListener(new SanitizerStepListener(s));
            if (pendingTimeout > 0) {
                // an exchange that is done no longer waits for a call that is still pending (such as one that threw)
                context.getManagementStrategy().addEventNotifier(new ExchangeDoneNotifier(s));
            }
            sanitizer = s;
            String why = enabled != null ? ENABLED + "=true" : session != null ? "@CamelSanitizer" : "profile dev";
            String message = "The exchange sanitizer is switched on ({}): it checks each call of the steps of the routes"
                             + " (rule R1), which costs time at each step. Do not use in production.";
            if (session == null || WARNED_IN_TESTS.compareAndSet(false, true)) {
                LOG.warn(message, why);
            } else {
                LOG.debug(message, why);
            }
        }

        @Override
        public void onContextStarted(CamelContext context) {
            Sanitizer s = sanitizer;
            if (s != null) {
                s.startPendingCheck();
            }
        }

        @Override
        public void onRoutesAdd(Collection<Route> routes) {
            Sanitizer s = sanitizer;
            if (s != null) {
                s.routesAdded(routes);
            }
        }

        @Override
        public void onRoutesRemove(Collection<Route> routes) {
            Sanitizer s = sanitizer;
            if (s != null) {
                s.routesRemoved(routes);
            }
        }

        @Override
        public void onContextStopped(CamelContext context) {
            Sanitizer s = sanitizer;
            if (s != null) {
                // the calls still pending when the context stops are listed
                s.stopPendingCheck(reportOnShutdown);
            }
            if (s != null && reportOnShutdown) {
                if (s.hasViolations() || s.hasWarnings()) {
                    LOG.warn("{}", s.summary());
                }
                if (LOG.isDebugEnabled()) {
                    LOG.debug("{}", s.report());
                }
            }
        }

        /**
         * A context of a test stays off on purpose (profile prod, camel.sanitizer.enabled=false, pooled exchanges, no
         * check): the expected violations of the test are then not required.
         */
        private static void turnedOff(SanitizerTestSession session) {
            if (session != null) {
                session.turnedOff();
            }
        }

        private static String property(PropertiesComponent pc, String key) {
            String value = pc.resolveProperty(key).orElse(null);
            if (value == null || value.isBlank() || isPlaceholder(value)) {
                return null;
            }
            return value.trim();
        }

        /**
         * Whether the value stands for a property that is not set: tooling such as camel export and the route diagram
         * of the Camel CLI resolves a missing property to a placeholder, and must still start the context.
         */
        private static boolean isPlaceholder(String value) {
            return PropertyConfigurerSupport.MAGIC_VALUE.equals(value) || value.startsWith(PropertiesComponent.PREFIX_TOKEN);
        }

        private static String orElse(String value, String defaultValue) {
            return value != null ? value : defaultValue;
        }

        static boolean parseBoolean(String key, String text) {
            if ("true".equalsIgnoreCase(text)) {
                return true;
            }
            if ("false".equalsIgnoreCase(text)) {
                return false;
            }
            throw new IllegalArgumentException(key + "=" + text + " is not true or false");
        }

        /**
         * Whether the checks include rule R1 ({@code callback}), the only check of this version.
         */
        static boolean parseChecks(String text) {
            boolean callback = false;
            for (String part : text.split(",")) {
                String check = part.trim().toLowerCase(Locale.ROOT);
                if (check.isEmpty() || "none".equals(check)) {
                    continue;
                }
                if (!CHECK_CALLBACK.equals(check)) {
                    throw new IllegalArgumentException(
                            CHECKS + "=" + text + " has the unknown check " + part.trim() + " (the checks of this version: "
                                                       + CHECK_CALLBACK + ")");
                }
                callback = true;
            }
            return callback;
        }

        /**
         * The timeout of the still pending warning in milliseconds, such as {@code 60s} or {@code 500} (ms); 0 is off.
         */
        static long parsePendingTimeout(String text) {
            long millis;
            try {
                millis = TimeUtils.toMilliSeconds(text);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException(PENDING_TIMEOUT + "=" + text + " is not a duration", e);
            }
            if (millis < 0) {
                throw new IllegalArgumentException(PENDING_TIMEOUT + "=" + text + " must be 0 or more");
            }
            return millis;
        }

        static int parseStackDepth(String text) {
            int depth;
            try {
                depth = Integer.parseInt(text);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(STACK_DEPTH + "=" + text + " is not a whole number", e);
            }
            if (depth < 0) {
                throw new IllegalArgumentException(STACK_DEPTH + "=" + text + " must be 0 or more");
            }
            return depth;
        }
    }

    /**
     * Tells the sanitizer that an exchange is done (its unit of work), so its calls that are still pending, such as one
     * that threw, are no longer waited for. It asks for no other event.
     */
    static final class ExchangeDoneNotifier extends EventNotifierSupport {

        private final Sanitizer sanitizer;

        ExchangeDoneNotifier(Sanitizer sanitizer) {
            this.sanitizer = sanitizer;
            setIgnoreCamelContextEvents(true);
            setIgnoreCamelContextInitEvents(true);
            setIgnoreRouteEvents(true);
            setIgnoreServiceEvents(true);
            setIgnoreExchangeCreatedEvent(true);
            setIgnoreExchangeRedeliveryEvents(true);
            setIgnoreExchangeSendingEvents(true);
            setIgnoreExchangeSentEvents(true);
            setIgnoreStepEvents(true);
        }

        @Override
        public boolean isEnabled(CamelEvent event) {
            return event instanceof CamelEvent.ExchangeCompletedEvent || event instanceof CamelEvent.ExchangeFailedEvent;
        }

        @Override
        public void notify(CamelEvent event) {
            if (event instanceof CamelEvent.ExchangeEvent e && e.getExchange() != null) {
                sanitizer.exchangeDone(e.getExchange().getExchangeId());
            }
        }

        @Override
        public String toString() {
            return "ExchangeSanitizer[R1 still pending]";
        }
    }
}

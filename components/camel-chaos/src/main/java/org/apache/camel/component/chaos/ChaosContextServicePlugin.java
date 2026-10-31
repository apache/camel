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
package org.apache.camel.component.chaos;

import java.security.SecureRandom;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.VetoCamelContextStartException;
import org.apache.camel.spi.ContextServicePlugin;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.support.LifecycleStrategySupport;
import org.apache.camel.util.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Switches camel-chaos on in a {@link CamelContext}, found on the classpath ({@code META-INF/services}).
 * <p/>
 * When the context initializes, before its routes are created, the plugin reads the {@code camel.chaos.*} options from
 * the properties component, and installs the hooks of chaos only with profile {@code dev} or with
 * {@code camel.chaos.enabled=true}, and never with profile {@code prod}. Otherwise it fails closed: nothing is
 * installed, and {@code camel.chaos.rules} is ignored. When it installs the hooks, it logs a WARN that chaos is
 * switched on, adds the rules of {@code camel.chaos.rules} (a rule that is not valid stops the context from starting),
 * and makes the {@link ChaosManager} available with {@link ChaosManager#get(CamelContext)}. Without rules the hooks do
 * nothing.
 *
 * @since 4.24
 */
public class ChaosContextServicePlugin implements ContextServicePlugin {

    static final String ENABLED = "camel.chaos.enabled";
    static final String SEED = "camel.chaos.seed";
    static final String RULES = "camel.chaos.rules";
    static final String MAX_DURATION = "camel.chaos.maxDuration";
    static final String REPORT_ON_SHUTDOWN = "camel.chaos.reportOnShutdown";

    private static final Logger LOG = LoggerFactory.getLogger(ChaosContextServicePlugin.class);

    @Override
    public void load(CamelContext camelContext) {
        // the plugin is loaded again when the context starts again; the context is initialized only once
        for (LifecycleStrategy strategy : camelContext.getLifecycleStrategies()) {
            if (strategy instanceof Activation) {
                return;
            }
        }
        camelContext.addLifecycleStrategy(new Activation());
    }

    /**
     * Decides when the context initializes whether chaos is switched on, and then installs it, and logs the report when
     * the context stops.
     */
    static final class Activation extends LifecycleStrategySupport {

        private volatile ChaosManager manager;
        private volatile boolean reportOnShutdown;

        @Override
        public void onContextInitializing(CamelContext context) throws VetoCamelContextStartException {
            if (manager != null) {
                return;
            }
            PropertiesComponent pc = context.getPropertiesComponent();
            String profile = context.getCamelContextExtension().getProfile();
            String rules = property(pc, RULES);
            if ("prod".equalsIgnoreCase(profile)) {
                // checked first, so nothing of chaos, not even an invalid option, can stop an application in prod
                if ("true".equalsIgnoreCase(property(pc, ENABLED)) || rules != null) {
                    LOG.warn("camel-chaos is on the classpath but stays off with profile prod: {} and {} are ignored",
                            ENABLED, RULES);
                }
                return;
            }
            Boolean enabled;
            try {
                String text = property(pc, ENABLED);
                enabled = text != null ? parseBoolean(ENABLED, text) : null;
            } catch (IllegalArgumentException e) {
                throw new VetoCamelContextStartException(e.getMessage(), e, context);
            }
            boolean on = enabled != null ? enabled : "dev".equalsIgnoreCase(profile);
            if (!on) {
                if (rules != null) {
                    LOG.info("camel-chaos is on the classpath but off (it is switched on with profile dev or {}=true),"
                             + " so {} is ignored",
                            ENABLED, RULES);
                } else {
                    LOG.debug("camel-chaos is on the classpath but off (it is switched on with profile dev or {}=true)",
                            ENABLED);
                }
                return;
            }

            ChaosManager chaos;
            List<String> startup = ChaosRuleParser.splitRules(rules);
            try {
                long maxDuration = duration(MAX_DURATION, property(pc, MAX_DURATION), "10m");
                String seedText = property(pc, SEED);
                long seed = seedText != null ? parseLong(SEED, seedText) : new SecureRandom().nextLong();
                reportOnShutdown = parseBoolean(REPORT_ON_SHUTDOWN, orElse(property(pc, REPORT_ON_SHUTDOWN), "true"));
                chaos = new ChaosManager(context, seed, seedText != null, maxDuration);
                for (String rule : startup) {
                    chaos.addRule(rule);
                }
            } catch (IllegalArgumentException | IllegalStateException e) {
                throw new VetoCamelContextStartException("camel-chaos: " + e.getMessage(), e, context);
            }
            chaos.install();
            context.getCamelContextExtension().addContextPlugin(ChaosManager.class, chaos);
            manager = chaos;
            LOG.warn(
                    "Chaos is switched on ({}): rules can fail, delay and drop the sends and steps of this application."
                     + " Do not use in production. Seed: {}, rules from {}: {}",
                    enabled != null ? ENABLED + "=true" : "profile dev", chaos.getSeed(), RULES, startup.size());
        }

        @Override
        public void onContextStopped(CamelContext context) {
            ChaosManager chaos = manager;
            if (chaos != null && reportOnShutdown && chaos.isUsed()) {
                LOG.info("{}", chaos.report().toText());
            }
        }

        private static String property(PropertiesComponent pc, String key) {
            String value = pc.resolveProperty(key).orElse(null);
            return value != null && !value.isBlank() ? value.trim() : null;
        }

        private static String orElse(String value, String defaultValue) {
            return value != null ? value : defaultValue;
        }

        private static boolean parseBoolean(String key, String text) {
            if ("true".equalsIgnoreCase(text)) {
                return true;
            }
            if ("false".equalsIgnoreCase(text)) {
                return false;
            }
            throw new IllegalArgumentException(key + "=" + text + " is not true or false");
        }

        private static long parseLong(String key, String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(key + "=" + text + " is not a whole number", e);
            }
        }

        private static long duration(String key, String text, String defaultValue) {
            String value = orElse(text, defaultValue);
            long millis;
            try {
                millis = TimeUtils.toMilliSeconds(value);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(key + "=" + value + " is not a time, such as 10m", e);
            }
            if (millis <= 0) {
                throw new IllegalArgumentException(key + "=" + value + " must be longer than 0");
            }
            return millis;
        }
    }
}

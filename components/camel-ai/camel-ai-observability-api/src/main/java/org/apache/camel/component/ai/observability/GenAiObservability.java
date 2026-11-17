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
package org.apache.camel.component.ai.observability;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.support.ResolverHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for GenAI observability in Camel AI producers.
 * <p/>
 * The concrete tracing and metrics implementation lives in {@code camel-ai-observability}, which provides a
 * {@link GenAiObservationFactory}. Without it, calls return a no-op observation.
 */
public final class GenAiObservability {

    private static final Logger LOG = LoggerFactory.getLogger(GenAiObservability.class);
    private static final GenAiObservation NOOP = new NoopGenAiObservation();
    private static final GenAiObservationFactory NOOP_FACTORY = (exchange, context) -> NOOP;
    private static final Object FACTORY_LOCK = new Object();
    private static final Set<String> INVALID_LEVEL_REPORTED = ConcurrentHashMap.newKeySet();

    private GenAiObservability() {
    }

    /**
     * Whether GenAI observability is enabled for the given context.
     */
    public static boolean isEnabled(CamelContext camelContext) {
        if (camelContext == null) {
            return false;
        }
        Optional<String> property
                = camelContext.getPropertiesComponent().resolveProperty(GenAiObservabilityProperties.ENABLED);
        if (property.isPresent()) {
            return Boolean.parseBoolean(property.get().trim());
        }
        return true;
    }

    /**
     * The GenAI observability level for the given context, from {@link GenAiObservabilityProperties#LEVEL}. Defaults to
     * {@link GenAiObservabilityLevel#BASIC}; an unknown value logs a warning once and falls back to the default.
     *
     * @since 4.24
     */
    public static GenAiObservabilityLevel level(CamelContext camelContext) {
        if (camelContext == null) {
            return GenAiObservabilityLevel.BASIC;
        }
        Optional<String> property
                = camelContext.getPropertiesComponent().resolveProperty(GenAiObservabilityProperties.LEVEL);
        if (property.isEmpty()) {
            return GenAiObservabilityLevel.BASIC;
        }
        try {
            return GenAiObservabilityLevel.parse(property.get());
        } catch (IllegalArgumentException e) {
            if (INVALID_LEVEL_REPORTED.add(property.get())) {
                LOG.warn("{}; using basic", e.getMessage());
            }
            return GenAiObservabilityLevel.BASIC;
        }
    }

    /**
     * Whether the given context records at least the given level.
     *
     * @since 4.24
     */
    public static boolean isLevel(CamelContext camelContext, GenAiObservabilityLevel level) {
        return isEnabled(camelContext) && level(camelContext).includes(level);
    }

    /**
     * The maximum length of every text recorded at level {@link GenAiObservabilityLevel#FULL}, from
     * {@link GenAiObservabilityProperties#CONTENT_MAX_LENGTH}.
     *
     * @since 4.24
     */
    public static int contentMaxLength(CamelContext camelContext) {
        if (camelContext == null) {
            return GenAiObservabilityProperties.DEFAULT_CONTENT_MAX_LENGTH;
        }
        Optional<String> property = camelContext.getPropertiesComponent()
                .resolveProperty(GenAiObservabilityProperties.CONTENT_MAX_LENGTH);
        if (property.isPresent()) {
            try {
                int maxLength = Integer.parseInt(property.get().trim());
                if (maxLength > 0) {
                    return maxLength;
                }
                LOG.debug("{} must be positive, using the default", GenAiObservabilityProperties.CONTENT_MAX_LENGTH);
            } catch (NumberFormatException e) {
                LOG.debug("Invalid {}: {}", GenAiObservabilityProperties.CONTENT_MAX_LENGTH, property.get());
            }
        }
        return GenAiObservabilityProperties.DEFAULT_CONTENT_MAX_LENGTH;
    }

    /**
     * Whether Camel leaves the model calls and tool calls already traced by the AI framework to that framework, from
     * {@link GenAiObservabilityProperties#DEFER_TO_FRAMEWORK} (default {@code true}).
     *
     * @since 4.24
     */
    public static boolean deferToFramework(CamelContext camelContext) {
        if (camelContext == null) {
            return true;
        }
        return camelContext.getPropertiesComponent().resolveProperty(GenAiObservabilityProperties.DEFER_TO_FRAMEWORK)
                .map(value -> Boolean.parseBoolean(value.trim()))
                .orElse(true);
    }

    /**
     * Starts a GenAI observation for a single LLM client call. Returns a no-op when disabled, when
     * {@code camel-ai-observability} is absent, or when no backend is available.
     */
    public static GenAiObservation start(Exchange exchange, GenAiObservationContext context) {
        if (exchange == null || context == null || !isEnabled(exchange.getContext())) {
            return NOOP;
        }
        if (context.tracedByFramework() && deferToFramework(exchange.getContext())) {
            // the AI framework records this call itself; a second span would count it twice
            return NOOP;
        }
        GenAiObservationFactory factory = factory(exchange.getContext());
        try {
            return factory.start(exchange, context);
        } catch (RuntimeException | LinkageError e) {
            // observability must never fail the LLM call, not even with a Micrometer or OpenTelemetry version mismatch
            LOG.debug("Unable to start GenAI observation via {}", factory, e);
            return NOOP;
        }
    }

    private static GenAiObservationFactory factory(CamelContext camelContext) {
        // kept as a context plugin so it goes away with its context
        ExtendedCamelContext extension = camelContext.getCamelContextExtension();
        GenAiObservationFactory factory = extension.getContextPlugin(GenAiObservationFactory.class);
        if (factory == null) {
            synchronized (FACTORY_LOCK) {
                factory = extension.getContextPlugin(GenAiObservationFactory.class);
                if (factory == null) {
                    factory = resolveFactory(camelContext);
                    extension.addContextPlugin(GenAiObservationFactory.class, factory);
                }
            }
        }
        return factory;
    }

    private static GenAiObservationFactory resolveFactory(CamelContext camelContext) {
        GenAiObservationFactory factory;
        try {
            factory = camelContext.getRegistry().findSingleByType(GenAiObservationFactory.class);
            if (factory == null) {
                factory = ResolverHelper.resolveService(camelContext, GenAiObservationFactory.FACTORY,
                        GenAiObservationFactory.class).orElse(null);
            }
        } catch (RuntimeException | LinkageError e) {
            // a broken classpath must not fail the LLM call; the no-op factory is kept so this is reported once
            LOG.warn("Unable to load the GenAI observability implementation; spans and metrics will not be emitted", e);
            return NOOP_FACTORY;
        }
        if (factory == null) {
            LOG.info(
                    "GenAI observability is enabled but camel-ai-observability is not on the classpath; "
                     + "spans and metrics will not be emitted");
            return NOOP_FACTORY;
        }
        return factory;
    }

    private static final class NoopGenAiObservation implements GenAiObservation {
        @Override
        public void recordSuccess(GenAiUsage usage) {
            // noop
        }

        @Override
        public void recordError(Throwable error) {
            // noop
        }

        @Override
        public void close() {
            // noop
        }
    }
}

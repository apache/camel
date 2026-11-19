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
package org.apache.camel.component.springai.chat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;

import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.model.tool.ToolCallingManager;

/**
 * Tells whether Spring AI traces the calls of a model. Spring AI records a Micrometer Observation for every model call
 * and tool call when the model (and its tool calling manager) is built with an ObservationRegistry; the Observation
 * becomes a span only when the registry has a Micrometer Tracing handler. Camel does not record the traced calls a
 * second time.
 * <p/>
 * Spring AI keeps the registries in private fields, which are not public API: when they cannot be read, the calls are
 * considered not traced, so Camel records them.
 */
final class SpringAiTracing {

    private static final Logger LOG = LoggerFactory.getLogger(SpringAiTracing.class);
    private static final String TRACING_HANDLER = "io.micrometer.tracing.handler.TracingObservationHandler";

    private SpringAiTracing() {
    }

    /**
     * Whether Spring AI traces the model calls of the given model.
     */
    static boolean isModelTraced(Object model) {
        return hasTracingHandler(fieldOfType(model, ObservationRegistry.class));
    }

    /**
     * Whether Spring AI traces the tool calls of the given model, which go through its tool calling manager and its
     * registry (some models use a default manager without one).
     */
    static boolean areToolCallsTraced(Object model) {
        Object toolCallingManager = fieldOfType(model, ToolCallingManager.class);
        return hasTracingHandler(fieldOfType(toolCallingManager, ObservationRegistry.class));
    }

    static boolean hasTracingHandler(Object registry) {
        if (!(registry instanceof ObservationRegistry observationRegistry) || observationRegistry.isNoop()) {
            return false;
        }
        try {
            // package-private in Micrometer
            Method getHandlers = ObservationRegistry.ObservationConfig.class.getDeclaredMethod("getObservationHandlers");
            getHandlers.setAccessible(true);
            Collection<?> handlers = (Collection<?>) getHandlers.invoke(observationRegistry.observationConfig());
            return handlers.stream().anyMatch(SpringAiTracing::isTracingHandler);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.debug("Unable to read the handlers of the ObservationRegistry", e);
            return false;
        }
    }

    private static boolean isTracingHandler(Object handler) {
        if (handler instanceof ObservationHandler.CompositeObservationHandler composite) {
            return composite.getHandlers().stream().anyMatch(SpringAiTracing::isTracingHandler);
        }
        return handler != null && implementsType(handler.getClass(), TRACING_HANDLER);
    }

    private static boolean implementsType(Class<?> type, String name) {
        if (type == null) {
            return false;
        }
        if (type.getName().equals(name) || implementsType(type.getSuperclass(), name)) {
            return true;
        }
        for (Class<?> implemented : type.getInterfaces()) {
            if (implementsType(implemented, name)) {
                return true;
            }
        }
        return false;
    }

    private static Object fieldOfType(Object owner, Class<?> fieldType) {
        if (owner == null) {
            return null;
        }
        for (Class<?> type = owner.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                // skip the static defaults (such as a no-op registry or a default tool calling manager)
                if (!Modifier.isStatic(field.getModifiers()) && fieldType.isAssignableFrom(field.getType())) {
                    try {
                        field.setAccessible(true);
                        return field.get(owner);
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        LOG.debug("Unable to read {} of {}", fieldType.getSimpleName(), type.getName(), e);
                        return null;
                    }
                }
            }
        }
        return null;
    }
}

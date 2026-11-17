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
package org.apache.camel.component.openai;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;

import org.apache.camel.CamelContext;
import org.apache.camel.component.ai.observability.GenAiObservability;
import org.apache.camel.component.ai.observability.GenAiObservabilityLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tells whether the OpenTelemetry Java agent traces the openai-java calls of this JVM. Its {@code openai-java}
 * instrumentation records chat completions and embeddings as GenAI spans of its own, so Camel recording them too would
 * count every call twice.
 */
final class OpenAIJavaAgentTracing {

    private static final Logger LOG = LoggerFactory.getLogger(OpenAIJavaAgentTracing.class);
    private static final AtomicBoolean WARNED = new AtomicBoolean();
    private static volatile Boolean active;

    private OpenAIJavaAgentTracing() {
    }

    /**
     * Whether Camel leaves a chat completion or embeddings call to the agent. It does unless the observability level is
     * {@code full}: the agent records the messages as log events only, which tracing backends such as Langfuse do not
     * read, so at {@code full} Camel keeps its own span and warns once about the double recording.
     */
    static boolean tracedByAgent(CamelContext camelContext) {
        if (!isActive()) {
            return false;
        }
        if (GenAiObservability.isLevel(camelContext, GenAiObservabilityLevel.FULL)) {
            if (WARNED.compareAndSet(false, true)) {
                LOG.warn("The OpenTelemetry Java agent also traces the openai-java calls of camel-openai, so at"
                         + " observability level full each call is recorded twice. Start the JVM with"
                         + " -Dotel.instrumentation.openai-java.enabled=false to keep only the Camel spans, which carry"
                         + " the messages.");
            }
            return false;
        }
        return true;
    }

    static boolean isActive() {
        Boolean value = active;
        if (value == null) {
            value = isActive(ManagementFactory.getRuntimeMXBean().getInputArguments(), OpenAIJavaAgentTracing::config);
            active = value;
        }
        return value;
    }

    /**
     * Whether the agent is attached and its openai-java instrumentation is enabled.
     *
     * @param jvmArguments the arguments of the JVM
     * @param config       reads an agent setting by its system property name
     */
    static boolean isActive(List<String> jvmArguments, UnaryOperator<String> config) {
        boolean agent = jvmArguments.stream()
                .anyMatch(arg -> arg.startsWith("-javaagent") && arg.contains("opentelemetry"));
        if (!agent || "false".equalsIgnoreCase(config.apply("otel.javaagent.enabled"))) {
            return false;
        }
        String enabled = config.apply("otel.instrumentation.openai-java.enabled");
        if (enabled == null) {
            enabled = config.apply("otel.instrumentation.common.default-enabled");
        }
        return enabled == null || Boolean.parseBoolean(enabled.trim());
    }

    /**
     * Reads an agent setting from the system properties, then from the environment variables (OTEL_...).
     */
    private static String config(String key) {
        String value = System.getProperty(key);
        if (value == null) {
            value = System.getenv(key.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_'));
        }
        return value;
    }
}

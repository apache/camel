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

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.component.ai.observability.GenAiObservability;
import org.apache.camel.component.ai.observability.GenAiObservabilityLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tells whether OpenTelemetry already traces the calls of an OpenAI client. The {@code openai-java} instrumentation of
 * the OpenTelemetry Java agent (and the OpenTelemetry instrumentation library used directly) wraps the client built by
 * {@code OpenAIOkHttpClient} in a proxy whose handler records chat completions and embeddings as GenAI spans of its
 * own, so Camel recording them too would count every call twice.
 */
final class OpenAIClientTracing {

    private static final Logger LOG = LoggerFactory.getLogger(OpenAIClientTracing.class);
    private static final AtomicBoolean WARNED = new AtomicBoolean();

    private OpenAIClientTracing() {
    }

    /**
     * Whether Camel leaves a chat completion or embeddings call of this client to OpenTelemetry. It does unless the
     * observability level is {@code full}: the instrumentation records the messages as log events only, which tracing
     * backends such as Langfuse do not read, so at {@code full} Camel keeps its own span and warns once about the
     * double recording.
     */
    static boolean tracedByOpenTelemetry(CamelContext camelContext, Object client) {
        if (!isTraced(client)) {
            return false;
        }
        if (GenAiObservability.isLevel(camelContext, GenAiObservabilityLevel.FULL)) {
            if (WARNED.compareAndSet(false, true)) {
                LOG.warn("The OpenAI client of camel-openai is instrumented by OpenTelemetry (for example by the openai-java"
                         + " instrumentation of the OpenTelemetry Java agent), so at observability level full each call is"
                         + " recorded twice. Start the JVM with -Dotel.instrumentation.openai-java.enabled=false to keep"
                         + " only the Camel spans, which carry the messages.");
            }
            return false;
        }
        return true;
    }

    /**
     * Whether the client is wrapped by the OpenTelemetry instrumentation.
     */
    static boolean isTraced(Object client) {
        return client != null && Proxy.isProxyClass(client.getClass())
                && Proxy.getInvocationHandler(client).getClass().getName().startsWith("io.opentelemetry.");
    }
}

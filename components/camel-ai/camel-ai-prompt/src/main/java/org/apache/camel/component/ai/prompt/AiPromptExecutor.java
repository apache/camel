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
package org.apache.camel.component.ai.prompt;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Framework-neutral executor for prompt routes.
 *
 * @since 4.24
 */
public final class AiPromptExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(AiPromptExecutor.class);

    private AiPromptExecutor() {
    }

    public static AiPromptResult execute(AiPromptSpec spec, Map<String, String> arguments, Exchange exchange) {
        String promptName = spec.getName();
        DefaultConsumer consumer = spec.getConsumer();
        if (consumer == null) {
            return new AiPromptResult.ExecutionError(
                    "No consumer available for prompt '" + promptName + "'",
                    new IllegalStateException("No consumer available"));
        }
        Processor processor = consumer.getProcessor();
        if (processor == null) {
            return new AiPromptResult.ExecutionError(
                    "No route processor available for prompt '" + promptName + "'",
                    new IllegalStateException("No route processor available"));
        }

        Map<String, String> values = arguments != null ? new HashMap<>(arguments) : new HashMap<>();
        Set<String> declared = spec.getDeclaredArgumentNames();
        values.keySet().removeIf(name -> {
            if (!declared.contains(name)) {
                LOG.warn("Ignoring undeclared argument '{}' for prompt '{}'", name, promptName);
                return true;
            }
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.startsWith("camel") || lower.startsWith("org.apache.camel.")) {
                LOG.warn("Rejecting reserved prompt argument '{}' for prompt '{}'", name, promptName);
                return true;
            }
            return false;
        });

        for (String required : spec.getRequiredArgumentNames()) {
            if (!values.containsKey(required) || values.get(required) == null) {
                return new AiPromptResult.ArgumentError(
                        "Missing required argument '" + required + "' for prompt '" + promptName + "'");
            }
        }

        values.forEach(exchange.getMessage()::setHeader);
        try {
            processor.process(exchange);
            if (exchange.getException() != null) {
                Exception error = exchange.getException();
                LOG.error("Error executing prompt '{}': {}", promptName, error.getMessage(), error);
                return new AiPromptResult.ExecutionError(error.getMessage(), error);
            }
            Object body = exchange.getMessage().getBody();
            if (body == null) {
                IllegalStateException error = new IllegalStateException("Prompt route produced no result");
                return new AiPromptResult.ExecutionError(error.getMessage(), error);
            }
            return new AiPromptResult.Success(body);
        } catch (Exception e) {
            LOG.error("Error executing prompt '{}': {}", promptName, e.getMessage(), e);
            return new AiPromptResult.ExecutionError(e.getMessage(), e);
        }
    }
}

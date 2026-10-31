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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Processor;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consumer registering a prompt route while its route is started and removing it when stopped.
 *
 * @since 4.24
 */
public class AiPromptConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(AiPromptConsumer.class);

    private final String promptName;
    private final AiPromptConfiguration configuration;
    private AiPromptSpec registeredSpec;
    private String[] registeredTags;
    private boolean registeredInDefaultPool;

    public AiPromptConsumer(AiPromptEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        promptName = endpoint.getPromptName();
        configuration = endpoint.getConfiguration();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (registeredSpec == null) {
            prepare();
        }
        register();
    }

    /**
     * Register during route warm-up so other routes starting in the same context can already discover the prompt.
     */
    void registerEarly() {
        if (registeredSpec == null && getRoute() != null && CamelContextHelper.isAutoStartup(getRoute())) {
            prepare();
            register();
        }
    }

    void deregisterEarly() {
        if (registeredSpec != null && !isStarted()) {
            deregister();
            registeredSpec = null;
        }
    }

    private void prepare() {
        String description = configuration.getDescription();
        if (description == null || description.isBlank()) {
            description = promptName;
        }

        List<AiPromptArgument> arguments = parseArguments(configuration.getArguments());
        registeredSpec = new AiPromptSpec(promptName, description, configuration.getTitle(), arguments, this);

        String tags = configuration.getTags();
        registeredTags = tags == null || tags.isBlank()
                ? null
                : Arrays.stream(tags.trim().split("\\s*,\\s*")).filter(tag -> !tag.isEmpty()).toArray(String[]::new);
        registeredInDefaultPool = registeredTags == null || registeredTags.length == 0;
        if (registeredInDefaultPool) {
            registeredTags = null;
        }
    }

    private static List<AiPromptArgument> parseArguments(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        Map<String, MutableArgument> parsed = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = entry.getKey();
            String name;
            String property;
            if (key.endsWith(".description")) {
                name = key.substring(0, key.length() - ".description".length());
                property = "description";
            } else if (key.endsWith(".required")) {
                name = key.substring(0, key.length() - ".required".length());
                property = "required";
            } else {
                throw new IllegalArgumentException(
                        "Unsupported ai-prompt argument option '" + key
                                                   + "'. Use argument.NAME.description or argument.NAME.required");
            }
            if (name.isBlank()) {
                throw new IllegalArgumentException("Prompt argument name must not be blank");
            }
            MutableArgument argument = parsed.computeIfAbsent(name, ignored -> new MutableArgument());
            if ("description".equals(property)) {
                argument.description = entry.getValue();
            } else {
                String required = entry.getValue();
                if (!"true".equalsIgnoreCase(required) && !"false".equalsIgnoreCase(required)) {
                    throw new IllegalArgumentException(
                            "Invalid required flag for prompt argument '" + name + "': " + required);
                }
                argument.required = Boolean.parseBoolean(required);
            }
        }
        List<AiPromptArgument> answer = new ArrayList<>(parsed.size());
        parsed.forEach((name, argument) -> answer.add(
                new AiPromptArgument(name, argument.description, argument.required)));
        return List.copyOf(answer);
    }

    private static final class MutableArgument {
        private String description;
        private boolean required;
    }

    @Override
    protected void doSuspend() throws Exception {
        if (registeredSpec != null) {
            deregister();
        }
        super.doSuspend();
    }

    @Override
    protected void doResume() throws Exception {
        super.doResume();
        if (registeredSpec != null) {
            register();
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (registeredSpec != null) {
            deregister();
            registeredSpec = null;
            registeredTags = null;
            registeredInDefaultPool = false;
        }
        super.doStop();
    }

    private void register() {
        AiPromptRegistry registry = AiPromptRegistry.getOrCreate(getEndpoint().getCamelContext());
        if (registeredTags != null) {
            LOG.debug("Registering prompt '{}' with tags {}", promptName, java.util.Arrays.toString(registeredTags));
            registry.putAll(registeredTags, registeredSpec);
        } else if (registeredInDefaultPool) {
            LOG.debug("Registering prompt '{}' in default pool (no tags)", promptName);
            registry.putDefault(registeredSpec);
        }
    }

    private void deregister() {
        AiPromptRegistry registry = AiPromptRegistry.getOrCreate(getEndpoint().getCamelContext());
        if (registeredTags != null) {
            for (String tag : registeredTags) {
                LOG.debug("Removing prompt '{}' from tag '{}'", promptName, tag);
                registry.remove(tag, registeredSpec);
            }
        } else if (registeredInDefaultPool) {
            registry.removeDefault(registeredSpec);
        }
    }
}

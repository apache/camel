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

import java.util.Map;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.PropertiesHelper;

import static org.apache.camel.component.ai.prompt.AiPrompt.SCHEME;

/**
 * Camel component that registers routes as prompts in the shared {@link AiPromptRegistry}.
 *
 * @since 4.24
 */
@Component(SCHEME)
public class AiPromptComponent extends DefaultComponent {

    @Metadata(description = "The component configuration")
    private AiPromptConfiguration configuration;

    public AiPromptComponent() {
        this(null);
    }

    public AiPromptComponent(CamelContext context) {
        super(context);
        configuration = new AiPromptConfiguration();
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        if (ObjectHelper.isEmpty(remaining)) {
            throw new IllegalArgumentException("A promptName must be provided: ai-prompt:<promptName>");
        }
        if (remaining.contains("/")) {
            throw new IllegalArgumentException("Prompt name must not contain '/': ai-prompt:<promptName>");
        }

        AiPromptConfiguration config = configuration.copy();

        // Extract argument.NAME.* URI options before endpoint property binding.
        // These options configure the prompt's declared arguments, not endpoint properties.
        Map<String, Object> promptArguments = PropertiesHelper.extractProperties(parameters, "argument.");
        if (!promptArguments.isEmpty()) {
            config.setArguments(promptArguments.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue()))));
        }

        AiPromptEndpoint endpoint = new AiPromptEndpoint(uri, this, remaining, config);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    public AiPromptConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(AiPromptConfiguration configuration) {
        this.configuration = configuration;
    }
}

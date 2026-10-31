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

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

import static org.apache.camel.component.ai.prompt.AiPrompt.SCHEME;

/**
 * Consumer endpoint that registers a Camel route as an MCP prompt.
 *
 * @since 4.24
 */
@UriEndpoint(
             firstVersion = "4.24.0",
             scheme = SCHEME,
             title = "AI Prompt",
             syntax = "ai-prompt:promptName",
             consumerOnly = true,
             remote = false,
             category = { Category.AI })
@BodyTypes(consumerOut = @BodyType(value = Carrier.NULL,
                                   description = "No body is sent to the route. Each prompt argument is available as a "
                                                 + "message header with its configured argument name; values are "
                                                 + "strings."),
           consumerReply = @BodyType(value = Carrier.ANY,
                                     description = "A String becomes one user message. A List may contain Strings or "
                                                   + "Maps with role (user or assistant) and content. Content may be a "
                                                   + "String or an MCP content block."))
public class AiPromptEndpoint extends DefaultEndpoint {

    @Metadata(required = true)
    @UriPath(description = "The prompt name advertised to MCP clients.")
    private final String promptName;

    @UriParam(description = "Prompt configuration including tags, description, title and argument definitions.")
    private AiPromptConfiguration configuration;

    private volatile AiPromptConsumer consumer;

    public AiPromptEndpoint(String uri, AiPromptComponent component, String promptName,
                            AiPromptConfiguration configuration) {
        super(uri, component);
        this.promptName = promptName;
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() {
        throw new UnsupportedOperationException(
                "ai-prompt does not support producer mode. Prompts are requested by clients through camel-mcp-server.");
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        AiPromptConsumer answer = new AiPromptConsumer(this, processor);
        configureConsumer(answer);
        consumer = answer;
        return answer;
    }

    public String getPromptName() {
        return promptName;
    }

    public AiPromptConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(AiPromptConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (consumer != null) {
            consumer.registerEarly();
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (consumer != null) {
            consumer.deregisterEarly();
        }
        super.doStop();
    }
}

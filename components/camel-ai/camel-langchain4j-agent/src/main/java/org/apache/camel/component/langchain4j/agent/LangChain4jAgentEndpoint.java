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
package org.apache.camel.component.langchain4j.agent;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.langchain4j.agent.api.Headers;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

import static org.apache.camel.component.langchain4j.agent.LangChain4jAgent.SCHEME;

@UriEndpoint(firstVersion = "4.14.0", scheme = SCHEME,
             title = "LangChain4j Agent",
             syntax = "langchain4j-agent:agentId",
             producerOnly = true,
             category = { Category.AI }, headersClass = Headers.class)
@BodyTypes(producerIn = @BodyType(value = { Carrier.TEXT, Carrier.POJO, Carrier.BYTES, Carrier.STREAM, Carrier.FILE },
                                  description = "The user message as text, or an AiAgentBody with the user message, the system"
                                                + " message, the memory id and a content. Bytes, a stream or a file of the file"
                                                + " consumers is sent as an image, audio, video, PDF or text content after the"
                                                + " text of the CamelLangChain4jAgentUserMessage header; bytes and a stream need"
                                                + " the CamelLangChain4jAgentMediaType or Content-Type header. A Map, a"
                                                + " java.io.File or another object fails, and no body fails"),
           producerOut = @BodyType(value = Carrier.TEXT,
                                   description = "The answer of the agent as text, also with outputClass or jsonSchema:"
                                                 + " unmarshal the JSON to read its fields. The token counts, the finish"
                                                 + " reason, the sources and the tool executions are in headers"))
public class LangChain4jAgentEndpoint extends DefaultEndpoint {
    @Metadata(required = true)
    @UriPath(description = "The Agent id")
    private final String agentId;

    @UriParam
    private LangChain4jAgentConfiguration configuration;

    public LangChain4jAgentEndpoint(String endpointUri, Component component, String agentId,
                                    LangChain4jAgentConfiguration configuration) {
        super(endpointUri, component);
        this.agentId = agentId;
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new LangChain4jAgentProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer not supported");
    }

    /**
     * Return the Agent ID
     *
     * @return
     */
    public String getAgentId() {
        return agentId;
    }

    public LangChain4jAgentConfiguration getConfiguration() {
        return configuration;
    }
}

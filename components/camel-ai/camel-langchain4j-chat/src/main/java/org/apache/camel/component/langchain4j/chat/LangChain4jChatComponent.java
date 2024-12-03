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
package org.apache.camel.component.langchain4j.chat;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

import static org.apache.camel.component.langchain4j.chat.LangChain4jChat.SCHEME;

@Component(SCHEME)
public class LangChain4jChatComponent extends DefaultComponent {

    @Metadata
    LangChain4jChatConfiguration configuration;

    public LangChain4jChatComponent() {
        this(null);
    }

    public LangChain4jChatComponent(CamelContext context) {
        super(context);
        this.configuration = new LangChain4jChatConfiguration();

    }

    public LangChain4jChatConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * The configuration.
     */
    public void setConfiguration(LangChain4jChatConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        LangChain4jChatConfiguration langchain4jChatConfiguration = this.configuration.copy();

        Endpoint endpoint = new LangChain4jChatEndpoint(uri, this, remaining, langchain4jChatConfiguration);
        setProperties(endpoint, parameters);
        return endpoint;
    }
}

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

package org.apache.camel.component.langchain4j.tools;

import static org.apache.camel.component.langchain4j.tools.LangChain4jTools.SCHEME;

import java.util.Map;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.PropertiesHelper;
import org.apache.camel.util.StringHelper;

@Component(SCHEME)
public class LangChain4jToolsComponent extends DefaultComponent {

    @Metadata
    LangChain4jToolsConfiguration configuration;

    public LangChain4jToolsComponent() {
        this(null);
    }

    public LangChain4jToolsComponent(CamelContext context) {
        super(context);
        this.configuration = new LangChain4jToolsConfiguration();
    }

    public LangChain4jToolsConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * The configuration.
     */
    public void setConfiguration(LangChain4jToolsConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        if (ObjectHelper.isEmpty(remaining)) {
            throw new IllegalArgumentException(
                    "Tool set and, optionally, the function, must be configured on the endpoint using syntax langchain4j-tools:toolId");
        }

        // Defaults to remaining, because if there's no "/", then it's likely a producer
        final String toolId = StringHelper.before(remaining, "/", remaining);
        final String tagList = parameters.get("tags").toString();
        if (ObjectHelper.isEmpty(tagList)) {
            throw new IllegalArgumentException("At least one tag must be specified");
        }

        LangChain4jToolsConfiguration langchain4jChatConfiguration = this.configuration.copy();

        Map<String, Object> toolParameters = PropertiesHelper.extractProperties(parameters, "parameter.");
        LangChain4jToolsEndpoint endpoint =
                new LangChain4jToolsEndpoint(uri, this, toolId, tagList, langchain4jChatConfiguration);
        endpoint.setParameters(toolParameters.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> (String) e.getValue())));

        setProperties(endpoint, parameters);
        return endpoint;
    }
}

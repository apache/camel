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

package org.apache.camel.component.springai.tools;

import static org.apache.camel.component.springai.tools.SpringAiTools.SCHEME;

import java.util.Map;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.PropertiesHelper;

@Component(SCHEME)
public class SpringAiToolsComponent extends DefaultComponent {

    @Metadata
    SpringAiToolsConfiguration configuration;

    public SpringAiToolsComponent() {
        this(null);
    }

    public SpringAiToolsComponent(CamelContext context) {
        super(context);
        this.configuration = new SpringAiToolsConfiguration();
    }

    public SpringAiToolsConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * The configuration.
     */
    public void setConfiguration(SpringAiToolsConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        if (ObjectHelper.isEmpty(remaining)) {
            throw new IllegalArgumentException(
                    "Tool ID must be configured on the endpoint using syntax spring-ai-tools:toolId");
        }

        final String toolId = remaining;
        final String tagList = parameters.get("tags").toString();
        if (ObjectHelper.isEmpty(tagList)) {
            throw new IllegalArgumentException("At least one tag must be specified");
        }

        SpringAiToolsConfiguration springAiToolsConfiguration = this.configuration.copy();

        Map<String, Object> toolParameters = PropertiesHelper.extractProperties(parameters, "parameter.");
        SpringAiToolsEndpoint endpoint =
                new SpringAiToolsEndpoint(uri, this, toolId, tagList, springAiToolsConfiguration);
        endpoint.setParameters(toolParameters.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> (String) e.getValue())));

        setProperties(endpoint, parameters);
        return endpoint;
    }
}

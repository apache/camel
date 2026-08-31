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
package org.apache.camel.component.ai.resource;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.component.ai.resource.AiResource.SCHEME;

/**
 * Camel component that registers routes as read-only AI resources in the shared {@link AiResourceRegistry}.
 *
 * @since 4.23
 */
@Component(SCHEME)
public class AiResourceComponent extends DefaultComponent {

    @Metadata(description = "The component configuration")
    private AiResourceConfiguration configuration;

    public AiResourceComponent() {
        this(null);
    }

    public AiResourceComponent(CamelContext context) {
        super(context);
        this.configuration = new AiResourceConfiguration();
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        if (ObjectHelper.isEmpty(remaining)) {
            throw new IllegalArgumentException(
                    "A resourceName must be provided: ai-resource:<resourceName>?resourceUri=<uri>");
        }
        if (remaining.contains("/")) {
            throw new IllegalArgumentException(
                    "Resource name must not contain '/': ai-resource:<resourceName>?resourceUri=<uri>");
        }

        AiResourceEndpoint endpoint = new AiResourceEndpoint(uri, this, remaining, this.configuration.copy());

        setProperties(endpoint, parameters);
        return endpoint;
    }

    public AiResourceConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(AiResourceConfiguration configuration) {
        this.configuration = configuration;
    }
}

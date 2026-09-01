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

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

import static org.apache.camel.component.ai.resource.AiResource.SCHEME;

/**
 * Framework-agnostic consumer endpoint that registers a Camel route as a read-only AI resource in the shared
 * {@link AiResourceRegistry}.
 *
 * @since 4.23
 */
@UriEndpoint(
             firstVersion = "4.23.0",
             scheme = SCHEME,
             title = "AI Resource",
             syntax = "ai-resource:resourceName",
             consumerOnly = true,
             remote = false,
             category = { Category.AI })
public class AiResourceEndpoint extends DefaultEndpoint {

    @Metadata(required = true)
    @UriPath(description = "The resource name. This is the human-readable label clients see in resource listings.")
    private final String resourceName;

    @UriParam(description = "Resource configuration including the resource uri, tags, description and MIME type.")
    private AiResourceConfiguration configuration;

    public AiResourceEndpoint(String uri, AiResourceComponent component, String resourceName,
                              AiResourceConfiguration configuration) {
        super(uri, component);
        this.resourceName = resourceName;
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() {
        throw new UnsupportedOperationException(
                "ai-resource does not support producer mode. "
                                                + "Resources are read by clients through an adapter such as camel-mcp-server.");
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        AiResourceConsumer consumer = new AiResourceConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    public String getResourceName() {
        return resourceName;
    }

    public AiResourceConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(AiResourceConfiguration configuration) {
        this.configuration = configuration;
    }
}

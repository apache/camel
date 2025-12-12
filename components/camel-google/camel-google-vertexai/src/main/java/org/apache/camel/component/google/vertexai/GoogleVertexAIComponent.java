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
package org.apache.camel.component.google.vertexai;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.HealthCheckComponent;

@Component("google-vertexai")
public class GoogleVertexAIComponent extends HealthCheckComponent {

    @Metadata
    private GoogleVertexAIConfiguration configuration = new GoogleVertexAIConfiguration();

    public GoogleVertexAIComponent() {
        this(null);
    }

    public GoogleVertexAIComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        if (remaining == null || remaining.isBlank()) {
            throw new IllegalArgumentException("URI path must be specified in format: projectId:location:modelId");
        }

        String[] parts = remaining.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException(
                    "URI path must be in format: projectId:location:modelId, but was: " + remaining);
        }

        final GoogleVertexAIConfiguration configuration
                = this.configuration != null ? this.configuration.copy() : new GoogleVertexAIConfiguration();
        configuration.setProjectId(parts[0]);
        configuration.setLocation(parts[1]);
        configuration.setModelId(parts[2]);

        Endpoint endpoint = new GoogleVertexAIEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    public GoogleVertexAIConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * The component configuration
     */
    public void setConfiguration(GoogleVertexAIConfiguration configuration) {
        this.configuration = configuration;
    }
}

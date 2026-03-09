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
package org.apache.camel.component.google.vision;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

@Component("google-vision")
public class GoogleCloudVisionComponent extends DefaultComponent {

    @Metadata
    private GoogleCloudVisionConfiguration configuration = new GoogleCloudVisionConfiguration();

    public GoogleCloudVisionComponent() {
    }

    public GoogleCloudVisionComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        if (remaining == null || remaining.isBlank()) {
            throw new IllegalArgumentException("Operation must be specified.");
        }
        final GoogleCloudVisionConfiguration configurationCopy
                = this.configuration != null ? this.configuration.copy() : new GoogleCloudVisionConfiguration();
        configurationCopy.setOperation(remaining);

        Endpoint endpoint = new GoogleCloudVisionEndpoint(uri, this, configurationCopy);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    public GoogleCloudVisionConfiguration getConfiguration() {
        return configuration;
    }
}

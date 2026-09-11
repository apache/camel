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
package org.apache.camel.component.apicurioregistry;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

@Component("apicurio-registry")
public class ApicurioRegistryComponent extends DefaultComponent {

    @Metadata(label = "advanced", description = "The component configuration")
    private ApicurioRegistryConfiguration configuration = new ApicurioRegistryConfiguration();

    public ApicurioRegistryComponent() {
    }

    public ApicurioRegistryComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        ApicurioRegistryConfiguration config = this.configuration.copy();

        String groupId = null;
        String artifactId = null;
        if (remaining != null && !remaining.isEmpty()) {
            String[] parts = remaining.split("/", 2);
            groupId = parts[0];
            if (parts.length > 1 && !parts[1].isEmpty()) {
                artifactId = parts[1];
            }
        }

        ApicurioRegistryEndpoint endpoint = new ApicurioRegistryEndpoint(uri, this, config, groupId, artifactId);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    public ApicurioRegistryConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(ApicurioRegistryConfiguration configuration) {
        this.configuration = configuration;
    }
}

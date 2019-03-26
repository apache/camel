/**
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
package org.apache.camel.component.pulsar;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.pulsar.configuration.PulsarEndpointConfiguration;
import org.apache.camel.component.pulsar.utils.AutoConfiguration;
import org.apache.camel.impl.DefaultComponent;

import java.util.Map;

public class PulsarComponent extends DefaultComponent {

    private PulsarEndpointConfiguration configuration;
    private AutoConfiguration autoConfiguration;

    public PulsarComponent(CamelContext context, PulsarEndpointConfiguration configuration, AutoConfiguration autoConfiguration) {
        super(context);
        this.configuration = configuration;
        this.autoConfiguration = autoConfiguration;
    }

    public PulsarComponent() {}

    public PulsarComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String path, Map<String, Object> parameters) throws Exception {

        setProperties(configuration, parameters);

        if(autoConfiguration != null) {
            autoConfiguration.ensureNameSpaceAndTenant(path);
        }
        return PulsarEndpoint.create(uri, path, configuration, this);
    }

    public PulsarEndpointConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(PulsarEndpointConfiguration configuration) {
        this.configuration = configuration;
    }

    public AutoConfiguration getAutoConfiguration() {
        return autoConfiguration;
    }

    public void setAutoConfiguration(AutoConfiguration autoConfiguration) {
        this.autoConfiguration = autoConfiguration;
    }
}

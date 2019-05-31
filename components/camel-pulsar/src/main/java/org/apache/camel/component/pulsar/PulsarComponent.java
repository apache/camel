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
package org.apache.camel.component.pulsar;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.pulsar.configuration.PulsarConfiguration;
import org.apache.camel.component.pulsar.utils.AutoConfiguration;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.pulsar.client.api.PulsarClient;

@Component("pulsar")
public class PulsarComponent extends DefaultComponent {

    @Metadata(label = "advanced")
    private AutoConfiguration autoConfiguration;
    @Metadata(label = "advanced")
    private PulsarClient pulsarClient;

    public PulsarComponent() {
    }

    public PulsarComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(final String uri, final String path, final Map<String, Object> parameters) throws Exception {
        final PulsarConfiguration configuration = new PulsarConfiguration();

        setProperties(configuration, parameters);
        if (autoConfiguration != null) {
            setProperties(autoConfiguration, parameters);

            if (autoConfiguration.isAutoConfigurable()) {
                autoConfiguration.ensureNameSpaceAndTenant(path);
            }
        }

        return PulsarEndpoint.create(uri, path, configuration, this, pulsarClient);
    }

    public AutoConfiguration getAutoConfiguration() {
        return autoConfiguration;
    }

    /**
     * The pulsar auto configuration
     */
    public void setAutoConfiguration(AutoConfiguration autoConfiguration) {
        this.autoConfiguration = autoConfiguration;
    }

    public PulsarClient getPulsarClient() {
        return pulsarClient;
    }

    /**
     * The pulsar client
     */
    public void setPulsarClient(PulsarClient pulsarClient) {
        this.pulsarClient = pulsarClient;
    }
}

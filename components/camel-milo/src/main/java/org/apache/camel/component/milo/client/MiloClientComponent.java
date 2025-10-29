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
package org.apache.camel.component.milo.client;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

@Component("milo-client")
public class MiloClientComponent extends DefaultComponent {

    @Metadata
    private MiloClientConfiguration configuration = new MiloClientConfiguration();

    @Metadata(autowired = true, label = "client", description = "Instance for managing client connections")
    private MiloClientConnectionManager miloClientConnectionManager = new MiloClientCachingConnectionManager();

    @Override
    protected Endpoint createEndpoint(final String uri, final String remaining, final Map<String, Object> parameters)
            throws Exception {

        final MiloClientConfiguration configuration = new MiloClientConfiguration(this.configuration);
        configuration.setEndpointUri(remaining);

        final MiloClientEndpoint endpoint
                = new MiloClientEndpoint(uri, this, configuration.getEndpointUri(), miloClientConnectionManager);
        endpoint.setConfiguration(configuration);
        setProperties(endpoint, parameters);

        return endpoint;
    }

    public MiloClientConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * All default options for client configurations
     */
    public void setConfiguration(final MiloClientConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * Default application name
     */
    public void setApplicationName(final String applicationName) {
        this.configuration.setApplicationName(applicationName);
    }

    /**
     * Default application URI
     */
    public void setApplicationUri(final String applicationUri) {
        this.configuration.setApplicationUri(applicationUri);
    }

    /**
     * Default product URI
     */
    public void setProductUri(final String productUri) {
        this.configuration.setProductUri(productUri);
    }

    /**
     * Default reconnect timeout
     */
    public void setReconnectTimeout(final Long reconnectTimeout) {
        this.configuration.setRequestTimeout(reconnectTimeout);
    }

    public MiloClientConnectionManager getMiloClientConnectionManager() {
        return miloClientConnectionManager;
    }

    public void setMiloClientConnectionManager(MiloClientConnectionManager miloClientConnectionManager) {
        this.miloClientConnectionManager = miloClientConnectionManager;
    }
}

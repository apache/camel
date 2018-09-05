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
package org.apache.camel.component.yql;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.component.yql.configuration.YqlConfiguration;
import org.apache.camel.component.yql.configuration.YqlConfigurationValidator;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.spi.Metadata;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

public class YqlComponent extends DefaultComponent {

    @Metadata(label = "advanced")
    private HttpClientConnectionManager localConnectionManager;

    @Override
    protected Endpoint createEndpoint(final String uri, final String remaining, final Map<String, Object> parameters) throws Exception {
        final YqlConfiguration configuration = new YqlConfiguration();
        configuration.setQuery(remaining);
        setProperties(configuration, parameters);
        YqlConfigurationValidator.validateProperties(configuration);

        final HttpClientConnectionManager connectionManager = createConnectionManager();

        return new YqlEndpoint(uri, this, configuration, connectionManager);
    }

    @Override
    protected void doStop() throws Exception {
        if (localConnectionManager != null) {
            localConnectionManager.shutdown();
        }
    }

    public HttpClientConnectionManager getLocalConnectionManager() {
        return localConnectionManager;
    }

    /**
     * To use a custom configured HttpClientConnectionManager.
     */
    public void setConnectionManager(final HttpClientConnectionManager connectionManager) {
        this.localConnectionManager = connectionManager;
    }

    private HttpClientConnectionManager createConnectionManager() {
        if (localConnectionManager != null) {
            return localConnectionManager;
        }
        final PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(200);
        connectionManager.setDefaultMaxPerRoute(20);
        setConnectionManager(connectionManager);
        return connectionManager;
    }
}

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
package org.apache.camel.component.elasticsearch.rest.client;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ElasticsearchRestClientSslContextParametersTest {

    private static final String URI = "elasticsearch-rest-client:test-cluster";

    @Test
    public void componentPropertyRoundTrip() {
        ElasticsearchRestClientComponent component = new ElasticsearchRestClientComponent();
        assertNull(component.getSslContextParameters(),
                "SSLContextParameters should be null by default");

        SSLContextParameters sslContextParameters = new SSLContextParameters();
        component.setSslContextParameters(sslContextParameters);
        assertSame(sslContextParameters, component.getSslContextParameters(),
                "Getter should return the value set via setter");
    }

    @Test
    public void componentPropagatesSslContextParametersToEndpoint() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();

            SSLContextParameters sslContextParameters = new SSLContextParameters();
            ElasticsearchRestClientComponent component = new ElasticsearchRestClientComponent();
            component.setHostAddressesList("localhost:9200");
            component.setSslContextParameters(sslContextParameters);
            context.addComponent("elasticsearch-rest-client", component);

            ElasticsearchRestClientEndpoint endpoint = context.getEndpoint(URI, ElasticsearchRestClientEndpoint.class);
            assertNotNull(endpoint);
            assertSame(sslContextParameters, endpoint.getSslContextParameters(),
                    "Component-level SSLContextParameters must propagate to the endpoint");
        }
    }

    @Test
    public void endpointUsesGlobalSslContextParametersWhenEnabled() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            SSLContextParameters globalParameters = new SSLContextParameters();
            context.setSSLContextParameters(globalParameters);
            context.start();

            ElasticsearchRestClientComponent component = new ElasticsearchRestClientComponent();
            component.setHostAddressesList("localhost:9200");
            component.setUseGlobalSslContextParameters(true);
            context.addComponent("elasticsearch-rest-client", component);

            ElasticsearchRestClientEndpoint endpoint = context.getEndpoint(URI, ElasticsearchRestClientEndpoint.class);
            assertNotNull(endpoint);
            assertTrue(component.isUseGlobalSslContextParameters());
            assertSame(globalParameters, endpoint.getSslContextParameters(),
                    "Global SSLContextParameters must be used when useGlobalSslContextParameters is true");
        }
    }

    @Test
    public void explicitSslContextParametersWinOverGlobal() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            SSLContextParameters globalParameters = new SSLContextParameters();
            context.setSSLContextParameters(globalParameters);
            context.start();

            SSLContextParameters explicitParameters = new SSLContextParameters();
            ElasticsearchRestClientComponent component = new ElasticsearchRestClientComponent();
            component.setHostAddressesList("localhost:9200");
            component.setUseGlobalSslContextParameters(true);
            component.setSslContextParameters(explicitParameters);
            context.addComponent("elasticsearch-rest-client", component);

            ElasticsearchRestClientEndpoint endpoint = context.getEndpoint(URI, ElasticsearchRestClientEndpoint.class);
            assertNotNull(endpoint);
            assertSame(explicitParameters, endpoint.getSslContextParameters(),
                    "Component-level SSLContextParameters must win over the global one");
        }
    }
}

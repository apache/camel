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
package org.apache.camel.component.opensearch;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OpensearchSslContextParametersTest {

    @Test
    public void configurationPropertyRoundTrip() {
        OpensearchConfiguration configuration = new OpensearchConfiguration();
        assertNull(configuration.getSslContextParameters(),
                "SSLContextParameters should be null by default");

        SSLContextParameters sslContextParameters = new SSLContextParameters();
        configuration.setSslContextParameters(sslContextParameters);
        assertSame(sslContextParameters, configuration.getSslContextParameters(),
                "Getter should return the value set via setter");
    }

    @Test
    public void componentPropagatesSslContextParametersToEndpoint() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();

            SSLContextParameters sslContextParameters = new SSLContextParameters();
            OpensearchComponent component = new OpensearchComponent(context);
            component.setHostAddresses("localhost:9200");
            component.setSslContextParameters(sslContextParameters);
            context.addComponent("opensearch", component);

            OpensearchEndpoint endpoint
                    = (OpensearchEndpoint) context.getEndpoint("opensearch:test-cluster?enableSSL=true");
            assertNotNull(endpoint);
            assertSame(sslContextParameters, endpoint.getConfiguration().getSslContextParameters(),
                    "Component-level SSLContextParameters must propagate to the endpoint configuration");
        }
    }

    @Test
    public void endpointUsesGlobalSslContextParametersWhenEnabled() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            SSLContextParameters globalParameters = new SSLContextParameters();
            context.setSSLContextParameters(globalParameters);
            context.start();

            OpensearchComponent component = new OpensearchComponent(context);
            component.setHostAddresses("localhost:9200");
            component.setUseGlobalSslContextParameters(true);
            context.addComponent("opensearch", component);

            OpensearchEndpoint endpoint
                    = (OpensearchEndpoint) context.getEndpoint("opensearch:test-cluster?enableSSL=true");
            assertNotNull(endpoint);
            assertTrue(component.isUseGlobalSslContextParameters());
            assertSame(globalParameters, endpoint.getConfiguration().getSslContextParameters(),
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
            OpensearchComponent component = new OpensearchComponent(context);
            component.setHostAddresses("localhost:9200");
            component.setUseGlobalSslContextParameters(true);
            component.setSslContextParameters(explicitParameters);
            context.addComponent("opensearch", component);

            OpensearchEndpoint endpoint
                    = (OpensearchEndpoint) context.getEndpoint("opensearch:test-cluster?enableSSL=true");
            assertNotNull(endpoint);
            assertSame(explicitParameters, endpoint.getConfiguration().getSslContextParameters(),
                    "Component-level SSLContextParameters must win over the global one");
        }
    }
}

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
package org.apache.camel.component.grpc;

import java.net.URI;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import io.grpc.ClientInterceptor;
import io.grpc.ServerInterceptor;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component("grpc")
public class GrpcComponent extends DefaultComponent {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcComponent.class);

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        GrpcConfiguration config = new GrpcConfiguration();
        config = parseConfiguration(config, uri);

        if (config.getPort() == 0 && !uri.contains("hash=")) {
            LOG.warn("gRPC endpoint configured with port=0 (dynamic port). "
                     + "If multiple routes use the same URI, add a unique 'hash' query parameter "
                     + "to each route to avoid endpoint collisions, e.g. grpc://localhost:0/service?hash=1");
        }

        Endpoint endpoint = new GrpcEndpoint(uri, this, config);
        setProperties(endpoint, parameters);
        if (config.isAutoDiscoverClientInterceptors()) {
            checkAndSetRegistryClientInterceptors(config);
        }
        if (config.isAutoDiscoverServerInterceptors()) {
            checkAndSetRegistryServerInterceptors(config);
        }
        return endpoint;
    }

    /**
     * Parses the configuration
     *
     * @return the parsed and valid configuration to use
     */
    protected GrpcConfiguration parseConfiguration(GrpcConfiguration configuration, String remaining) throws Exception {
        configuration.parseURI(new URI(remaining));
        return configuration;
    }

    private void checkAndSetRegistryClientInterceptors(GrpcConfiguration configuration) {
        Set<ClientInterceptor> clientInterceptors = getCamelContext().getRegistry().findByType(ClientInterceptor.class);
        if (!clientInterceptors.isEmpty()) {
            configuration.setClientInterceptors(new ArrayList<>(clientInterceptors));
        }
    }

    private void checkAndSetRegistryServerInterceptors(GrpcConfiguration configuration) {
        Set<ServerInterceptor> serverInterceptors = getCamelContext().getRegistry().findByType(ServerInterceptor.class);
        if (!serverInterceptors.isEmpty()) {
            configuration.setServerInterceptors(new ArrayList<>(serverInterceptors));
        }
    }
}

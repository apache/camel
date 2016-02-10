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
package org.apache.camel.component.etcd;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;

/**
 * Represents the component that manages {@link AbstractEtcdEndpoint}.
 */
public class EtcdComponent extends UriEndpointComponent {
    public EtcdComponent() {
        super(AbstractEtcdEndpoint.class);
    }

    public EtcdComponent(CamelContext context) {
        super(context, AbstractEtcdEndpoint.class);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        EtcdNamespace namespace = EtcdNamespace.fromPath(remaining);
        if (namespace != null) {
            if (!remaining.startsWith("/")) {
                remaining = "/" + remaining;
            }

            switch (namespace) {
            case STATS:
                return new EtcdStatsEndpoint(
                    uri,
                    this,
                    loadConfiguration(new EtcdStatsConfiguration(), parameters),
                    namespace,
                    remaining
                );
            case WATCH:
                return new EtcdWatchEndpoint(
                    uri,
                    this,
                    loadConfiguration(new EtcdWatchConfiguration(), parameters),
                    namespace,
                    remaining
                );
            case KEYS:
                return new EtcdKeysEndpoint(
                    uri,
                    this,
                    loadConfiguration(new EtcdKeysConfiguration(), parameters),
                    namespace,
                    remaining
                );
            default:
                throw new IllegalStateException("No endpoint for " + remaining);
            }
        }

        throw new IllegalStateException("No endpoint for " + remaining);
    }

    protected <T> T loadConfiguration(T configuration, Map<String, Object> parameters) throws Exception {
        setProperties(configuration, parameters);
        return configuration;
    }
}

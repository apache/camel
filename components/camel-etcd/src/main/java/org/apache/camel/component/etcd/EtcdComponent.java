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
import org.apache.camel.util.ObjectHelper;

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
        String ns = ObjectHelper.before(remaining, "/");
        String path = ObjectHelper.after(remaining, "/");

        if (ns == null) {
            ns = remaining;
        }

        if (path == null) {
            path = remaining;
        }

        EtcdNamespace namespace = getCamelContext().getTypeConverter().mandatoryConvertTo(EtcdNamespace.class, ns);
        EtcdConfiguration configuration = loadConfiguration(new EtcdConfiguration(getCamelContext()), parameters);

        if (namespace != null) {
            // path must start with leading slash
            if (!path.startsWith("/")) {
                path = "/" + path;
            }

            switch (namespace) {
            case stats:
                return new EtcdStatsEndpoint(uri, this, configuration, namespace, path);
            case watch:
                return new EtcdWatchEndpoint(uri, this, configuration, namespace, path);
            case keys:
                return new EtcdKeysEndpoint(uri, this, configuration, namespace, path);
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

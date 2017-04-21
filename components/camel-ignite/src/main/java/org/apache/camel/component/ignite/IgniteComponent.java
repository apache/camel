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
package org.apache.camel.component.ignite;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.component.ignite.cache.IgniteCacheEndpoint;
import org.apache.camel.component.ignite.compute.IgniteComputeEndpoint;
import org.apache.camel.component.ignite.events.IgniteEventsEndpoint;
import org.apache.camel.component.ignite.idgen.IgniteIdGenEndpoint;
import org.apache.camel.component.ignite.messaging.IgniteMessagingEndpoint;
import org.apache.camel.component.ignite.queue.IgniteQueueEndpoint;
import org.apache.camel.component.ignite.set.IgniteSetEndpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Ignite Component integrates Apache Camel with Apache Ignite, providing endpoints for the following functions:
 * <ul>
 * <li>Cache operations.</li>
 * <li>Cluster computation.</li>
 * <li>Messaging.</li>
 * <li>Eventing.</li>
 * <li>Id Generation.</li>
 * <li>Set operations.</li>
 * <li>Queue operations.</li>
 * </ul>
 * @deprecated Use 
 * {@link org.apache.camel.component.ignite.cache.IgniteCacheComponent},
 * {@link org.apache.camel.component.ignite.compute.IgniteComputeComponent},
 * {@link org.apache.camel.component.ignite.events.IgniteEventsComponent},
 * {@link org.apache.camel.component.ignite.idgen.IgniteIdGenComponent},
 * {@link org.apache.camel.component.ignite.messaging.IgniteMessagingComponent},
 * {@link org.apache.camel.component.ignite.queue.IgniteQueueComponent} and
 * {@link org.apache.camel.component.ignite.set.IgniteSetComponent}
 */
@Deprecated
public class IgniteComponent extends AbstractIgniteComponent {

    private static final Logger LOG = LoggerFactory.getLogger(IgniteComponent.class);

    public static IgniteComponent fromIgnite(Ignite ignite) {
        IgniteComponent answer = new IgniteComponent();
        answer.setIgnite(ignite);
        return answer;
    }

    public static IgniteComponent fromConfiguration(IgniteConfiguration configuration) {
        IgniteComponent answer = new IgniteComponent();
        answer.setIgniteConfiguration(configuration);
        return answer;
    }

    public static IgniteComponent fromInputStream(InputStream inputStream) {
        IgniteComponent answer = new IgniteComponent();
        answer.setConfigurationResource(inputStream);
        return answer;
    }

    public static IgniteComponent fromUrl(URL url) {
        IgniteComponent answer = new IgniteComponent();
        answer.setConfigurationResource(url);
        return answer;
    }

    public static IgniteComponent fromLocation(String location) {
        IgniteComponent answer = new IgniteComponent();
        answer.setConfigurationResource(location);
        return answer;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        ObjectHelper.notNull(getCamelContext(), "Camel Context");

        AbstractIgniteEndpoint answer = null;
        URI remainingUri = new URI(URISupport.normalizeUri(remaining));
        String scheme = remainingUri.getScheme();

        LOG.warn("The scheme syntax 'ignite:{}' has been deprecated. Use 'ignite-{}' instead.", scheme, scheme);

        switch (scheme) {
        case "cache":
            answer = new IgniteCacheEndpoint(uri, remainingUri, parameters, this);
            break;
        case "compute":
            answer = new IgniteComputeEndpoint(uri, remainingUri, parameters, this);
            break;
        case "messaging":
            answer = new IgniteMessagingEndpoint(uri, remainingUri, parameters, this);
            break;
        case "events":
            answer = new IgniteEventsEndpoint(uri, remainingUri, parameters, this);
            break;
        case "set":
            answer = new IgniteSetEndpoint(uri, remainingUri, parameters, this);
            break;
        case "idgen":
            answer = new IgniteIdGenEndpoint(uri, remainingUri, parameters, this);
            break;
        case "queue":
            answer = new IgniteQueueEndpoint(uri, remainingUri, parameters, this);
            break;
            
        default:
            throw new MalformedURLException("An invalid Ignite endpoint URI was provided. Please check that "
                    + "it starts with:" + " ignite:[cache/compute/messaging/...]:...");
        }

        setProperties(answer, parameters);

        return answer;
    }

}

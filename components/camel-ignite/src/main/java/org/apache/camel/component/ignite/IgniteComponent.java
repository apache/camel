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
 */
public class IgniteComponent extends UriEndpointComponent {

    /**
     * Modes of managing the underlying {@link Ignite} instance. 
     */
    public enum IgniteLifecycleMode {
        USER_MANAGED, COMPONENT_MANAGED
    }

    /** Ignite configuration. */
    private IgniteConfiguration igniteConfiguration;

    /** Resource from where to load configuration. */
    private Object configurationResource;

    /** Ignite instance. */
    private Ignite ignite;

    /** How the Ignite lifecycle is managed. */
    private IgniteLifecycleMode lifecycleMode = IgniteLifecycleMode.COMPONENT_MANAGED;

    public IgniteComponent() {
        super(AbstractIgniteEndpoint.class);
    }

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

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (lifecycleMode == IgniteLifecycleMode.USER_MANAGED) {
            return;
        }

        // Try to load the configuration from the resource.
        if (configurationResource != null) {
            if (configurationResource instanceof URL) {
                ignite = Ignition.start((URL) configurationResource);
            } else if (configurationResource instanceof InputStream) {
                ignite = Ignition.start((InputStream) configurationResource);
            } else if (configurationResource instanceof String) {
                ignite = Ignition.start((String) configurationResource);
            } else {
                throw new IllegalStateException("An unsupported configuration resource was provided to the Ignite component. " + "Supported types are: URL, InputStream, String.");
            }
        } else if (igniteConfiguration != null) {
            ignite = Ignition.start(igniteConfiguration);
        } else {
            throw new IllegalStateException("No configuration resource or IgniteConfiguration was provided to the Ignite component.");
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (lifecycleMode == IgniteLifecycleMode.USER_MANAGED) {
            return;
        }

        if (ignite != null) {
            ignite.close();
        }
    }

    /**
     * Returns the {@link Ignite} instance.
     */
    public Ignite getIgnite() {
        return ignite;
    }

    /**
     * Sets the {@link Ignite} instance.
     */
    public void setIgnite(Ignite ignite) {
        this.ignite = ignite;
    }

    /**
     * Gets the resource from where to load the configuration. It can be a: {@link URI}, {@link String} (URI) 
     * or an {@link InputStream}.
     */
    public Object getConfigurationResource() {
        return configurationResource;
    }

    /**
     * Sets the resource from where to load the configuration. It can be a: {@link URI}, {@link String} (URI) 
     * or an {@link InputStream}.
     */
    public void setConfigurationResource(Object configurationResource) {
        this.configurationResource = configurationResource;
    }

    /**
     * Gets the {@link IgniteConfiguration} if the user set it explicitly.
     */
    public IgniteConfiguration getIgniteConfiguration() {
        return igniteConfiguration;
    }

    /**
     * Allows the user to set a programmatic {@link IgniteConfiguration}.
     */
    public void setIgniteConfiguration(IgniteConfiguration igniteConfiguration) {
        this.igniteConfiguration = igniteConfiguration;
    }

}

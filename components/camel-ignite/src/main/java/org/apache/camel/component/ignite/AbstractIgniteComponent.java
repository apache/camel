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
import java.net.URI;
import java.net.URL;
import org.apache.camel.impl.DefaultComponent;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.IgniteConfiguration;

/**
 * This is a base class of camel-ignite components which correspond to following Apache Ignite functions:
 * <ul>
 * <li>{@link org.apache.camel.component.ignite.cache.IgniteCacheComponent}: Cache operations.</li>
 * <li>{@link org.apache.camel.component.ignite.compute.IgniteComputeComponent}: Cluster computation.</li>
 * <li>{@link org.apache.camel.component.ignite.messaging.IgniteMessagingComponent}: Messaging.</li>
 * <li>{@link org.apache.camel.component.ignite.events.IgniteEventsComponent}: Eventing.</li>
 * <li>{@link org.apache.camel.component.ignite.idgen.IgniteIdGenComponent}: Id Generation.</li>
 * <li>{@link org.apache.camel.component.ignite.set.IgniteSetComponent}: Set operations.</li>
 * <li>{@link org.apache.camel.component.ignite.queue.IgniteQueueComponent}: Queue operations.</li>
 * </ul>
 */
public abstract class AbstractIgniteComponent extends DefaultComponent {

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
        lifecycleMode = IgniteLifecycleMode.USER_MANAGED;
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

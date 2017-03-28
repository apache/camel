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
package org.apache.camel.component.kestrel;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import net.spy.memcached.ConnectionFactory;
import net.spy.memcached.ConnectionFactoryBuilder;
import net.spy.memcached.FailureMode;
import net.spy.memcached.MemcachedClient;
import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Camel component which offers queueing over the Memcached protocol
 * as supported by Kestrel.
 */
public class KestrelComponent extends UriEndpointComponent {
    private static final Logger LOG = LoggerFactory.getLogger(KestrelComponent.class);

    private ConnectionFactory memcachedConnectionFactory;

    /**
     * We cache the memcached clients by queue for reuse
     */
    private final Map<String, MemcachedClient> memcachedClientCache = new HashMap<String, MemcachedClient>();

    @Metadata(label = "advanced")
    private KestrelConfiguration configuration;

    public KestrelComponent() {
        this(new KestrelConfiguration());
    }

    public KestrelComponent(KestrelConfiguration configuration) {
        super(KestrelEndpoint.class);
        this.configuration = configuration;
    }

    public KestrelComponent(CamelContext context) {
        super(context, KestrelEndpoint.class);
        configuration = new KestrelConfiguration();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        ConnectionFactoryBuilder builder = new ConnectionFactoryBuilder();
        // VERY IMPORTANT! Otherwise, spymemcached optimizes away concurrent gets
        builder.setShouldOptimize(false);
        // We never want spymemcached to time out
        builder.setOpTimeout(9999999);
        // Retry upon failure
        builder.setFailureMode(FailureMode.Retry);
        memcachedConnectionFactory = builder.build();
    }

    public KestrelConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * To use a shared configured configuration as base for creating new endpoints.
     */
    public void setConfiguration(KestrelConfiguration configuration) {
        this.configuration = configuration;
    }

    protected KestrelEndpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        // Copy the configuration as each endpoint can override defaults
        KestrelConfiguration config = getConfiguration().copy();

        // Parse the URI, expected to be in one of the following formats:
        // 1. Use the base KestrelConfiguration for host addresses:
        //      kestrel://queue[?parameters]
        //      kestrel:///queue[?parameters]
        // 2. Override the host, but use the default port:
        //      kestrel://host/queue[?parameters]
        // 3. Override the host and port:
        //      kestrel://host:port/queue[?parameters]
        // 4. Supply a list of host addresses:
        //      kestrel://host[:port],host[:port]/queue[?parameters]
        URI u = new URI(uri);
        String queue;
        String[] addresses = null;
        if (u.getPath() == null || "".equals(u.getPath())) {
            // This would be the case when they haven't specified any explicit
            // address(es), and the queue ends up in the "authority" portion of
            // the URI.  For example:
            //      kestrel://queue[?parameters]
            queue = u.getAuthority();
        } else if (u.getAuthority() == null || "".equals(u.getAuthority())) {
            // The "path" was present without an authority, such as:
            //      kestrel:///queue[?parameters]
            queue = u.getPath();
        } else {
            // Both "path" and "authority" were present in the URI, which
            // means both address(es) and the queue were specified, i.e.:
            //      kestrel://host/queue[?parameters]
            //      kestrel://host:port/queue[?parameters]
            //      kestrel://host[:port],host[:port]/queue[?parameters]
            addresses = u.getAuthority().split(",");
            queue = u.getPath();
        }

        // Trim off any slash(es), i.e. "/queue/" -> "queue"
        while (queue.startsWith("/")) {
            queue = queue.substring(1);
        }
        while (queue.endsWith("/")) {
            queue = queue.substring(0, queue.length() - 1);
        }

        if ("".equals(queue)) {
            // This would be the case if the URI didn't include a path, or if
            // the path was just "/" or something...throw an exception.
            throw new IllegalArgumentException("Queue not specified in endpoint URI: " + uri);
        }

        if (addresses != null && addresses.length > 0) {
            // Override the addresses on the copied config
            config.setAddresses(addresses);
        } else {
            // Explicit address(es) weren't specified on the URI, which is
            // no problem...just default the addresses to whatever was set on
            // the base KestrelConfiguration.  And since we've already copied
            // the config, there's nothing else we need to do there.  But let's
            // make sure the addresses field was indeed set on the base config.
            if (config.getAddresses() == null) {
                throw new IllegalArgumentException("Addresses not set in base configuration or endpoint: " + uri);
            }
        }

        LOG.info("Creating endpoint for queue \"" + queue + "\" on " + config.getAddressesAsString() + ", parameters=" + parameters);

        // Finally, override config with any supplied URI parameters
        setProperties(config, parameters);

        // Create the endpoint for the given queue with the config we built
        return new KestrelEndpoint(uri, this, config, queue);
    }

    public MemcachedClient getMemcachedClient(KestrelConfiguration config, String queue) {
        String key = config.getAddressesAsString() + "/" + queue;
        MemcachedClient memcachedClient = memcachedClientCache.get(key);
        if (memcachedClient != null) {
            return memcachedClient;
        }
        synchronized (memcachedClientCache) {
            if ((memcachedClient = memcachedClientCache.get(key)) == null) {
                LOG.info("Creating MemcachedClient for " + key);
                try {
                    memcachedClient = new MemcachedClient(memcachedConnectionFactory, config.getInetSocketAddresses());
                } catch (Exception e) {
                    throw new RuntimeCamelException("Failed to connect to " + key, e);
                }
                memcachedClientCache.put(key, memcachedClient);
            }
        }
        return memcachedClient;
    }

    public void closeMemcachedClient(String key, MemcachedClient memcachedClient) {
        try {
            LOG.debug("Closing client connection to {}", key);
            memcachedClient.shutdown();
            memcachedClientCache.remove(key);
        } catch (Exception e) {
            LOG.warn("Failed to close client connection to " + key, e);
        }
    }

    @Override
    protected synchronized void doStop() throws Exception {
        // Use a copy so we can clear the memcached client cache eagerly
        Map<String, MemcachedClient> copy;
        synchronized (memcachedClientCache) {
            copy = new HashMap<String, MemcachedClient>(memcachedClientCache);
            memcachedClientCache.clear();
        }

        for (Map.Entry<String, MemcachedClient> entry : copy.entrySet()) {
            closeMemcachedClient(entry.getKey(), entry.getValue());
        }

        super.doStop();
    }
}

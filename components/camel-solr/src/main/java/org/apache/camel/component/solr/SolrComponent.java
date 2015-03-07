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
package org.apache.camel.component.solr;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.impl.CloudSolrServer;
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrServer;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the component that manages {@link SolrEndpoint}.
 */
public class SolrComponent extends UriEndpointComponent {

    private static final Logger LOG = LoggerFactory.getLogger(SolrComponent.class);
    private final Map<SolrEndpoint, SolrServerReference> servers = new HashMap<SolrEndpoint, SolrServerReference>();

    protected static final class SolrServerReference {

        private final AtomicInteger referenceCounter = new AtomicInteger();
        private HttpSolrServer solrServer;
        private ConcurrentUpdateSolrServer updateSolrServer;
        private CloudSolrServer cloudSolrServer;

        public HttpSolrServer getSolrServer() {
            return solrServer;
        }

        public void setSolrServer(HttpSolrServer solrServer) {
            this.solrServer = solrServer;
        }

        public ConcurrentUpdateSolrServer getUpdateSolrServer() {
            return updateSolrServer;
        }

        public void setUpdateSolrServer(ConcurrentUpdateSolrServer updateSolrServer) {
            this.updateSolrServer = updateSolrServer;
        }
        
        public CloudSolrServer getCloudSolrServer() {
            return cloudSolrServer;
        }

        public void setCloudSolrServer(CloudSolrServer cloudServer) {
            cloudSolrServer = cloudServer;
        }

        public int addReference() {
            return referenceCounter.incrementAndGet();
        }

        public int decReference() {
            return referenceCounter.decrementAndGet();
        }
    }

    public SolrComponent() {
        super(SolrEndpoint.class);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        Endpoint endpoint = new SolrEndpoint(uri, this, remaining);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    public SolrServerReference getSolrServers(SolrEndpoint endpoint) {
        return servers.get(endpoint);
    }

    public void addSolrServers(SolrEndpoint endpoint, SolrServerReference servers) {
        this.servers.put(endpoint, servers);
    }

    @Override
    protected void doShutdown() throws Exception {
        for (SolrServerReference server : servers.values()) {
            shutdownServers(server);
        }
        servers.clear();
    }

    void shutdownServers(SolrServerReference ref) {
        shutdownServers(ref, false);
    }
    
    private void shutdownServer(SolrServer server) {
        if (server != null) {
            LOG.info("Shutting down solr server: {}", server);
            server.shutdown();
        }
    }

    void shutdownServers(SolrServerReference ref, boolean remove) {
        try {
            shutdownServer(ref.getSolrServer());
        } catch (Exception e) {
            LOG.warn("Error shutting down solr server. This exception is ignored.", e);
        }
        try {
            shutdownServer(ref.getUpdateSolrServer());
        } catch (Exception e) {
            LOG.warn("Error shutting down streaming solr server. This exception is ignored.", e);
        }
        
        try {
            shutdownServer(ref.getCloudSolrServer());
        } catch (Exception e) {
            LOG.warn("Error shutting down streaming solr server. This exception is ignored.", e);
        }

        if (remove) {
            SolrEndpoint key = null;
            for (Map.Entry<SolrEndpoint, SolrServerReference> entry : servers.entrySet()) {
                if (entry.getValue() == ref) {
                    key = entry.getKey();
                    break;
                }
            }
            if (key != null) {
                servers.remove(key);
            }
        }

    }
}

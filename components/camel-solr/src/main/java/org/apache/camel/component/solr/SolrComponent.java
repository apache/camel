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
package org.apache.camel.component.solr;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the component that manages {@link SolrEndpoint}.
 */
@Component("solr,solrCloud,solrs")
public class SolrComponent extends DefaultComponent {

    private static final Logger LOG = LoggerFactory.getLogger(SolrComponent.class);

    private final Map<String, SolrClient> solrClients = new HashMap<>();

    @Deprecated
    private final Map<SolrEndpoint, SolrServerReference> servers = new HashMap<>();

    @Deprecated
    protected static final class SolrServerReference {

        private final AtomicInteger referenceCounter = new AtomicInteger();
        private HttpSolrClient solrServer;
        private ConcurrentUpdateSolrClient updateSolrServer;
        private CloudSolrClient cloudSolrServer;

        public HttpSolrClient getSolrServer() {
            return solrServer;
        }

        public void setSolrServer(HttpSolrClient solrServer) {
            this.solrServer = solrServer;
        }

        public ConcurrentUpdateSolrClient getUpdateSolrServer() {
            return updateSolrServer;
        }

        public void setUpdateSolrServer(ConcurrentUpdateSolrClient updateSolrServer) {
            this.updateSolrServer = updateSolrServer;
        }

        public CloudSolrClient getCloudSolrServer() {
            return cloudSolrServer;
        }

        public void setCloudSolrServer(CloudSolrClient cloudServer) {
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
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        SolrConfiguration configuration = new SolrConfiguration(uri, remaining);
        Endpoint endpoint = new SolrEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    public SolrClient getSolrClient(SolrConfiguration solrConfiguration) throws Exception {
        String signature = solrConfiguration.getSignature();
        if (!solrClients.containsKey(signature)) {
            SolrClient solrClient = solrConfiguration.initSolrClient();
            solrClients.put(signature, solrClient);
            return solrClient;
        }
        return solrClients.get(signature);
    }

    public void closeSolrClient(SolrConfiguration solrConfiguration) {
        // close when generated for endpoint
        if (solrConfiguration.getSolrEndpoint() != null) {
            solrClients.remove(solrConfiguration.getSignature());
            try {
                solrClients.get(solrConfiguration.getSignature()).close();
            } catch (IOException e) {
                LOG.warn("Error shutting down solr client. This exception is ignored.", e);
            }
        }
    }

    @Deprecated
    public SolrServerReference getSolrServers(SolrEndpoint endpoint) {
        return servers.get(endpoint);
    }

    @Deprecated
    public void addSolrServers(SolrEndpoint endpoint, SolrServerReference servers) {
        this.servers.put(endpoint, servers);
    }

    @Override
    protected void doShutdown() throws Exception {
        for (SolrClient solrClient : solrClients.values()) {
            solrClient.close();
        }
        solrClients.clear();
        for (SolrServerReference server : servers.values()) {
            shutdownServers(server);
        }
        servers.clear();
    }

    @Deprecated
    void shutdownServers(SolrServerReference ref) {
        shutdownServers(ref, false);
    }

    @Deprecated
    private void shutdownServer(SolrClient server) throws IOException {
        if (server != null) {
            LOG.info("Shutting down solr server: {}", server);
            server.close();
        }
    }

    @Deprecated
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

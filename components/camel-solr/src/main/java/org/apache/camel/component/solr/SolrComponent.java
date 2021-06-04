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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collection;
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

    private final Map<String, SolrClientReference> solrClientMap = new HashMap<>();

    protected static final class SolrClientReference {
        private final SolrClient solrClient;
        private final List<SolrProducer> solrProducerList = new ArrayList<>();

        public SolrClientReference(SolrClient solrClient) {
            this.solrClient = solrClient;
        }

        public SolrClient getSolrClient() {
            return solrClient;
        }

        public int unRegisterSolrProducer(SolrProducer solrProducer) {
            solrProducerList.remove(solrProducer);
            return solrProducerList.size();
        }

        public int registerSolrProducer(SolrProducer solrProducer) {
            solrProducerList.add(solrProducer);
            return solrProducerList.size();
        }

    }

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

    public SolrClient getSolrClient(SolrProducer solrProducer, SolrConfiguration solrConfiguration) throws Exception {
        String signature = solrConfiguration.getSignature();
        SolrClientReference solrClientReference;
        if (!solrClientMap.containsKey(signature)) {
            solrClientReference = new SolrClientReference(solrConfiguration.initSolrClient());
            solrClientMap.put(signature, solrClientReference);
            // backward compatibility
            addSolrClientToSolrServerReference(solrConfiguration, solrClientReference.getSolrClient());
        } else {
            solrClientReference = solrClientMap.get(signature);
        }
        // register producer against solrClient (for later close of client)
        solrClientReference.registerSolrProducer(solrProducer);
        return solrClientReference.getSolrClient();
    }

    private void addSolrClientToSolrServerReference(SolrConfiguration solrConfiguration, SolrClient solrClient) {
        SolrEndpoint solrEndpoint = solrConfiguration.getSolrEndpoint();
        if (solrEndpoint != null) {
            SolrServerReference solrServerReference = servers.get(solrEndpoint);
            if (solrServerReference == null) {
                solrServerReference = new SolrServerReference();
                servers.put(solrEndpoint, solrServerReference);
            }
            if (solrClient instanceof CloudSolrClient) {
                solrServerReference.setCloudSolrServer((CloudSolrClient) solrClient);
            }
            if (solrClient instanceof ConcurrentUpdateSolrClient) {
                solrServerReference.setUpdateSolrServer((ConcurrentUpdateSolrClient) solrClient);
            }
            if (solrClient instanceof HttpSolrClient) {
                solrServerReference.setSolrServer((HttpSolrClient) solrClient);
            }
        }
    }

    public void closeSolrClient(SolrProducer solrProducer) {
        // close when generated for endpoint
        List<String> signatureToRemoveList = new ArrayList<>();
        for (Map.Entry<String, SolrClientReference> entry : solrClientMap.entrySet()) {
            SolrClientReference solrClientReference = entry.getValue();
            if (solrClientReference.unRegisterSolrProducer(solrProducer) == 0) {
                signatureToRemoveList.add(entry.getKey());
            }
        }
        removeFromSolrClientMap(signatureToRemoveList);
    }

    private void removeFromSolrClientMap(Collection<String> signatureToRemoveList) {
        for (String signature : signatureToRemoveList) {
            SolrClientReference solrClientReference = solrClientMap.get(signature);
            solrClientMap.remove(signature);
            try {
                solrClientReference.getSolrClient().close();
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
        removeFromSolrClientMap(solrClientMap.keySet());
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

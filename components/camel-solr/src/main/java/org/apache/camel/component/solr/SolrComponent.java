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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.solr.client.solrj.SolrClient;
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

    public SolrComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        SolrConfiguration configuration = SolrConfiguration.newInstance(uri, remaining);
        Endpoint endpoint = new SolrEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    public SolrClient getSolrClient(SolrProducer solrProducer, SolrConfiguration solrConfiguration) {
        String signature = SolrClientHandler.getSignature(solrConfiguration);
        SolrClientReference solrClientReference;
        if (!solrClientMap.containsKey(signature)) {
            solrClientReference = new SolrClientReference(SolrClientHandler.getSolrClient(solrConfiguration));
            solrClientMap.put(signature, solrClientReference);
        } else {
            solrClientReference = solrClientMap.get(signature);
        }
        // register producer against solrClient (for later close of client)
        solrClientReference.registerSolrProducer(solrProducer);
        return solrClientReference.getSolrClient();
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

    @Override
    protected void doShutdown() throws Exception {
        removeFromSolrClientMap(solrClientMap.keySet());
    }

}

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

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Perform operations against Apache Lucene Solr.
 */
@UriEndpoint(firstVersion = "2.9.0", scheme = "solr,solrs,solrCloud", title = "Solr", syntax = "solr:url", producerOnly = true,
             category = { Category.MONITORING, Category.SEARCH }, headersClass = SolrConstants.class)
public class SolrEndpoint extends DefaultEndpoint {

    @UriParam
    private SolrConfiguration solrConfiguration;

    private final Map<String, SolrConfiguration> solrConfigurationsMap = new HashMap<>();

    public SolrEndpoint(String endpointUri, SolrComponent component, SolrConfiguration solrConfiguration) {
        super(endpointUri, component);
        solrConfiguration.setSolrEndpoint(this);
        this.solrConfiguration = solrConfiguration;
        this.solrConfigurationsMap.put(SolrConstants.OPERATION, solrConfiguration);
    }

    public void setZkHost(String zkHost) {
        try {
            String decoded = URLDecoder.decode(zkHost, "UTF-8");
            this.solrConfiguration.setZkHost(decoded);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeCamelException(e);
        }
    }

    public SolrConfiguration getSolrConfiguration() {
        return getSolrConfiguration(null);
    }

    public SolrConfiguration getSolrConfiguration(String solrOperation) {
        if (solrConfigurationsMap.containsKey(solrOperation)) {
            return solrConfigurationsMap.get(solrOperation);
        }
        SolrConfiguration newSolrConfiguration = SolrClientHandler.initializeFor(solrOperation, solrConfiguration);
        solrConfigurationsMap.put(solrOperation, newSolrConfiguration);
        return newSolrConfiguration;
    }

    public void setRequestHandler(String requestHandler) {
        solrConfiguration.setRequestHandler(requestHandler);
    }

    @Override
    public SolrComponent getComponent() {
        return (SolrComponent) super.getComponent();
    }

    @Override
    public Producer createProducer() throws Exception {
        return new SolrProducer(this);
    }

    protected void onProducerShutdown(SolrProducer producer) {
        getComponent().closeSolrClient(producer);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer not supported for Solr endpoint.");
    }

}

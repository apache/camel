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
package org.apache.camel.component.elasticsearch5;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The elasticsearch component is used for interfacing with ElasticSearch server.
 */
@UriEndpoint(scheme = "elasticsearch5", title = "Elasticsearch5", syntax = "elasticsearch5:clusterName", producerOnly = true, label = "monitoring,search")
public class ElasticsearchEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchEndpoint.class);

    private TransportClient client;
    @UriParam
    private ElasticsearchConfiguration configuration;

    public ElasticsearchEndpoint(String uri, ElasticsearchComponent component, ElasticsearchConfiguration config, TransportClient client) throws Exception {
        super(uri, component);
        this.configuration = config;
        this.client = client;
    }

    public Producer createProducer() throws Exception {
        return new ElasticsearchProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Cannot consume from an ElasticsearchEndpoint: " + getEndpointUri());
    }
    
    public boolean isSingleton() {
        return false;
    }
    
    @Override
    @SuppressWarnings("unchecked")
    protected void doStart() throws Exception {
        super.doStart();

        if (client == null) {
            LOG.info("Connecting to the ElasticSearch cluster: " + configuration.getClusterName());
            
            if (configuration.getIp() != null) {
                client = new PreBuiltTransportClient(getSettings())
                    .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(configuration.getIp()), configuration.getPort()));
            } else if (configuration.getTransportAddressesList() != null
                    && !configuration.getTransportAddressesList().isEmpty()) {
                List<TransportAddress> addresses = new ArrayList<TransportAddress>(configuration.getTransportAddressesList().size());
                for (TransportAddress address : configuration.getTransportAddressesList()) {
                    addresses.add(address);
                }
                client = new PreBuiltTransportClient(getSettings()).addTransportAddresses(addresses.toArray(new TransportAddress[addresses.size()]));
            } else {
                LOG.info("Incorrect ip address and port parameters settings for ElasticSearch cluster");
            }
        }
    }

    private Settings getSettings() {
        return Settings.builder()
                .put("cluster.name", configuration.getClusterName())
                .put("client.transport.ignore_cluster_name", false)
                .put("client.transport.sniff", configuration.getClientTransportSniff())
                .build();
    }

    @Override
    protected void doStop() throws Exception {
        if (client != null) {
            LOG.info("Disconnecting from ElasticSearch cluster: " + configuration.getClusterName());
            client.close();
            client = null;
        }
        super.doStop();
    }

    public TransportClient getClient() {
        return client;
    }

    public ElasticsearchConfiguration getConfig() {
        return configuration;
    }

    public void setOperation(String operation) {
        configuration.setOperation(operation);
    }
}
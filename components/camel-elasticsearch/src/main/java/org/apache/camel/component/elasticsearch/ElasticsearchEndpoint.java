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
package org.apache.camel.component.elasticsearch;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.elasticsearch.node.NodeBuilder.nodeBuilder;

/**
 * The elasticsearch component is used for interfacing with ElasticSearch server.
 */
@UriEndpoint(firstVersion = "2.11.0", scheme = "elasticsearch", title = "Elasticsearch", syntax = "elasticsearch:clusterName", producerOnly = true, label = "monitoring,search")
public class ElasticsearchEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchEndpoint.class);

    private Node node;
    private Client client;
    private volatile boolean closeClient;
    @UriParam
    private ElasticsearchConfiguration configuration;

    public ElasticsearchEndpoint(String uri, ElasticsearchComponent component, ElasticsearchConfiguration config, Client client) throws Exception {
        super(uri, component);
        this.configuration = config;
        this.client = client;
        this.closeClient = client == null;
    }

    public Producer createProducer() throws Exception {
        return new ElasticsearchProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Cannot consume from an ElasticsearchEndpoint: " + getEndpointUri());
    }

    public boolean isSingleton() {
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doStart() throws Exception {
        super.doStart();

        if (client == null) {
            if (configuration.isLocal()) {
                LOG.info("Starting local ElasticSearch server");
            } else {
                LOG.info("Joining ElasticSearch cluster " + configuration.getClusterName());
            }
            
            if (configuration.getIp() != null) {
                this.client = TransportClient.builder().settings(getSettings()).build()
                    .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(configuration.getIp()), configuration.getPort()));
            } else if (configuration.getTransportAddressesList() != null
                    && !configuration.getTransportAddressesList().isEmpty()) {
                List<TransportAddress> addresses = new ArrayList(configuration.getTransportAddressesList().size());
                for (TransportAddress address : configuration.getTransportAddressesList()) {
                    addresses.add(address);
                }
                this.client = TransportClient.builder().settings(getSettings()).build().addTransportAddresses(addresses.toArray(new TransportAddress[addresses.size()]));
            } else {
                NodeBuilder builder = nodeBuilder().local(configuration.isLocal()).data(configuration.getData());
                if (configuration.isLocal()) {
                    builder.getSettings().put("http.enabled", false);
                }
                if (!configuration.isLocal() && configuration.getClusterName() != null) {
                    builder.clusterName(configuration.getClusterName());
                }
                builder.getSettings().put("path.home", configuration.getPathHome());
                node = builder.node();
                client = node.client();
            }
        }
    }

    private Settings getSettings() {
        return Settings.settingsBuilder()
                .put("cluster.name", configuration.getClusterName())
                .put("client.transport.ignore_cluster_name", false)
                .put("node.client", true)
                .put("client.transport.sniff", configuration.getClientTransportSniff())
                .put("http.enabled", false)
                .put("path.home", configuration.getPathHome())
                .build();
    }

    @Override
    protected void doStop() throws Exception {
        if (closeClient) {
            if (configuration.isLocal()) {
                LOG.info("Stopping local ElasticSearch server");
            } else {
                LOG.info("Leaving ElasticSearch cluster " + configuration.getClusterName());
            }
            client.close();
            if (node != null) {
                node.close();
            }
            client = null;
            node = null;
        }
        super.doStop();
    }

    public Client getClient() {
        return client;
    }

    public ElasticsearchConfiguration getConfig() {
        return configuration;
    }

    public void setOperation(String operation) {
        configuration.setOperation(operation);
    }

}

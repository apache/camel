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

import java.net.URI;
import java.util.Map;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.elasticsearch.node.NodeBuilder.nodeBuilder;

/**
 * Represents an Elasticsearch endpoint.
 */
@UriEndpoint(scheme = "elasticsearch", title = "Elasticsearch", syntax = "elasticsearch:clusterName", producerOnly = true, label = "monitoring,search")
public class ElasticsearchEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchEndpoint.class);

    private Node node;
    private Client client;
    @UriParam
    private ElasticsearchConfiguration configuration;

    public ElasticsearchEndpoint(String uri, ElasticsearchComponent component, Map<String, Object> parameters) throws Exception {
        super(uri, component);
        this.configuration = new ElasticsearchConfiguration(new URI(uri), parameters);
    }

    public Producer createProducer() throws Exception {
        return new ElasticsearchProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new RuntimeCamelException("Cannot consume to a ElasticsearchEndpoint: " + getEndpointUri());
    }

    public boolean isSingleton() {
        return true;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (configuration.isLocal()) {
            LOG.info("Starting local ElasticSearch server");
        } else {
            LOG.info("Joining ElasticSearch cluster " + configuration.getClusterName());
        }
        if (configuration.getIp() != null) {
            LOG.info("REMOTE ELASTICSEARCH: {}", configuration.getIp());
            Settings settings = ImmutableSettings.settingsBuilder()
                    // setting the classloader here will allow the underlying elasticsearch-java
                    // class to find its names.txt in an OSGi environment (otherwise the thread
                    // classloader is used, which won't be able to see the file causing a startup
                    // exception).
                    .classLoader(Settings.class.getClassLoader())
                    .put("cluster.name", configuration.getClusterName())
                    .put("client.transport.ignore_cluster_name", false)
                    .put("node.client", true)
                    .put("client.transport.sniff", true)
                    .build();
            Client client = new TransportClient(settings)
                    .addTransportAddress(new InetSocketTransportAddress(configuration.getIp(), configuration.getPort()));
            this.client = client;
        } else {
            NodeBuilder builder = nodeBuilder().local(configuration.isLocal()).data(configuration.isData());
            if (!configuration.isLocal() && configuration.getClusterName() != null) {
                builder.clusterName(configuration.getClusterName());
            }
            builder.getSettings().classLoader(Settings.class.getClassLoader());
            node = builder.node();
            client = node.client();
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (configuration.isLocal()) {
            LOG.info("Stopping local ElasticSearch server");
        } else {
            LOG.info("Leaving ElasticSearch cluster " + configuration.getClusterName());
        }
        client.close();
        if (node != null) {
            node.close();
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

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

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultEndpoint;
import org.elasticsearch.client.Client;
import org.elasticsearch.node.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents an Elasticsearch endpoint.
 */
public class ElasticsearchEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchEndpoint.class);

    private Node node;
    private Client client;
    private ElasticsearchConfiguration config;

    public ElasticsearchEndpoint(String uri, ElasticsearchComponent component, ElasticsearchConfiguration config) {
        super(uri, component);
        this.config = config;
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
        if (config.isLocal()) {
            LOG.info("Starting local ElasticSearch server");
        } else {
            LOG.info("Joining ElasticSearch cluster " + config.getClusterName());
        }
        node = config.buildNode();
        client = node.client();
    }

    @Override
    protected void doStop() throws Exception {
        if (config.isLocal()) {
            LOG.info("Stopping local ElasticSearch server");
        } else {
            LOG.info("Leaving ElasticSearch cluster " + config.getClusterName());
        }
        node.close();
        super.doStop();
    }

    @Override
    protected void doShutdown() throws Exception {
        // TODO Auto-generated method stub
        super.doShutdown();
    }

    public Client getClient() {
        return client;
    }

    public ElasticsearchConfiguration getConfig() {
        return config;
    }

}

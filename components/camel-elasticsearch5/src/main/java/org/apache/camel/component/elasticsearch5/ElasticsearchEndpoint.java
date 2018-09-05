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

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.elasticsearch.client.transport.TransportClient;

/**
 * The elasticsearch component is used for interfacing with ElasticSearch server using 5.x API.
 */
@UriEndpoint(firstVersion = "2.19.0", scheme = "elasticsearch5", title = "Elasticsearch5", syntax = "elasticsearch5:clusterName", producerOnly = true, label = "monitoring,search")
public class ElasticsearchEndpoint extends DefaultEndpoint {

    @UriParam
    protected final ElasticsearchConfiguration configuration;

    private TransportClient client;

    public ElasticsearchEndpoint(String uri, ElasticsearchComponent component, ElasticsearchConfiguration config, TransportClient client) throws Exception {
        super(uri, component);
        this.configuration = config;
        this.client = client;
    }

    public Producer createProducer() throws Exception {
        return new ElasticsearchProducer(this, configuration);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Cannot consume from an ElasticsearchEndpoint: " + getEndpointUri());
    }
    
    public boolean isSingleton() {
        return true;
    }

    public TransportClient getClient() {
        return client;
    }
}
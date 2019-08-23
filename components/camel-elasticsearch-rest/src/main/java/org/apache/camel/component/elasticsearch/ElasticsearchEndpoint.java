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
package org.apache.camel.component.elasticsearch;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.elasticsearch.client.RestClient;

/**
 * The elasticsearch component is used for interfacing with ElasticSearch server using REST API.
 */
@UriEndpoint(firstVersion = "2.21.0", scheme = "elasticsearch-rest", title = "Elastichsearch Rest",
    syntax = "elasticsearch-rest:clusterName", producerOnly = true, label = "monitoring,search")
public class ElasticsearchEndpoint extends DefaultEndpoint {

    @UriParam
    private final ElasticsearchConfiguration configuration;

    private RestClient client;

    public ElasticsearchEndpoint(String uri, ElasticsearchComponent component, ElasticsearchConfiguration config, RestClient client) throws Exception {
        super(uri, component);
        this.configuration = config;
        this.client = client;
    }

    public ElasticsearchConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new ElasticsearchProducer(this, configuration);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Cannot consume from an ElasticsearchEndpoint: " + getEndpointUri());
    }
    
    public RestClient getClient() {
        return client;
    }
}

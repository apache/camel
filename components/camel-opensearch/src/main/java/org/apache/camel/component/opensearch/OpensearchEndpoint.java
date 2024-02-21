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
package org.apache.camel.component.opensearch;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.opensearch.client.RestClient;

/**
 * Send requests to OpenSearch via Java Client API.
 */
@UriEndpoint(firstVersion = "4.0.0", scheme = "opensearch", title = "OpenSearch",
             syntax = "opensearch:clusterName", producerOnly = true,
             category = { Category.SEARCH, Category.MONITORING }, headersClass = OpensearchConstants.class)
public class OpensearchEndpoint extends DefaultEndpoint {

    @UriParam
    private final OpensearchConfiguration configuration;

    private final RestClient client;

    public OpensearchEndpoint(String uri, OpensearchComponent component, OpensearchConfiguration config,
                              RestClient client) {
        super(uri, component);
        this.configuration = config;
        this.client = client;
    }

    public OpensearchConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public Producer createProducer() {
        return new OpensearchProducer(this, configuration);
    }

    @Override
    public Consumer createConsumer(Processor processor) {
        throw new UnsupportedOperationException("Cannot consume from an OpenSearch: " + getEndpointUri());
    }

    public RestClient getClient() {
        return client;
    }
}

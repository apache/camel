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
package org.apache.camel.component.dapr;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.component.dapr.operations.DaprOperationManager;
import org.apache.camel.component.dapr.operations.DaprOperationResponse;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DaprProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(DaprProducer.class);
    private final DaprConfigurationOptionsProxy configurationOptionsProxy;
    private final DaprOperationManager manager;

    public DaprProducer(final Endpoint endpoint, DaprConfiguration configuration) {
        super(endpoint);
        this.configurationOptionsProxy = new DaprConfigurationOptionsProxy(configuration);
        this.manager = new DaprOperationManager(configurationOptionsProxy);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        LOG.info("Processing operation: {}", configurationOptionsProxy.getOperation());
        setResponse(exchange, manager.process(exchange, getEndpoint().getClient()));
    }

    private void setResponse(Exchange exchange, DaprOperationResponse response) {
        exchange.getMessage().setBody(response.getBody(), String.class);
        exchange.getMessage().setHeaders(response.getHeaders());
    }

    @Override
    public DaprEndpoint getEndpoint() {
        return (DaprEndpoint) super.getEndpoint();
    }
}

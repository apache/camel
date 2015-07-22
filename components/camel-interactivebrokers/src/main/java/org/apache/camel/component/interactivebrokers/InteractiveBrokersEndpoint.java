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
package org.apache.camel.component.interactivebrokers;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@UriEndpoint(scheme = "interactivebrokers", syntax = "interactivebrokers:host:port", label = "finance")
public class InteractiveBrokersEndpoint extends DefaultEndpoint {

    private final Logger logger = LoggerFactory.getLogger(InteractiveBrokersEndpoint.class);

    @UriParam
    private InteractiveBrokersConfiguration configuration;

    public InteractiveBrokersEndpoint() {
    }

    public InteractiveBrokersEndpoint(String endpointUri, Component component) throws URISyntaxException {
        super(endpointUri, component);
        configuration = new InteractiveBrokersConfiguration();
        configuration.configure(new URI(endpointUri));
        logger.trace("endpoint URI: " + endpointUri);
        logger.trace("tuple: " + configuration.getTransportTuple());
    }
    
    public Producer createProducer() throws Exception {
        ObjectHelper.notNull(configuration, "configuration");
        InteractiveBrokersComponent c = (InteractiveBrokersComponent)getComponent();
        InteractiveBrokersTransportTuple tuple = configuration.getTransportTuple();
        switch (configuration.getProducerType()) {
        case ORDERS:
            return new InteractiveBrokersProducer(this, c.getBinding(tuple));
        default:
            throw new RuntimeCamelException("invalid/unspecified producerType");
        }
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        ObjectHelper.notNull(configuration, "configuration");
        InteractiveBrokersComponent c = (InteractiveBrokersComponent)getComponent();
        InteractiveBrokersTransportTuple tuple = configuration.getTransportTuple();
        switch (configuration.getConsumerType()) {
        case MARKET_DATA_TOP:
            return new InteractiveBrokersMarketDataTopRealTimeConsumer(this, processor, c.getBinding(tuple));
        case TRADE_REPORTS:
            return new InteractiveBrokersTradeReportRealTimeConsumer(this, processor, c.getBinding(tuple));
        default:
            throw new RuntimeCamelException("invalid/unspecified consumerType");
        }
    }

    public boolean isSingleton() {
        return true;
    }

    public InteractiveBrokersConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(InteractiveBrokersConfiguration configuration) {
        this.configuration = configuration;
    }



}

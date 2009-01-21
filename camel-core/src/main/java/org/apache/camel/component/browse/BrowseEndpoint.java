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
package org.apache.camel.component.browse;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.Service;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.spi.BrowsableEndpoint;

/**
 * An endpoint which maintains a {@link List} of {@link Exchange} instances
 * which can be useful for tooling, debugging and visualising routes.
 *
 * @version $Revision$
 */
public class BrowseEndpoint extends DefaultEndpoint implements BrowsableEndpoint, Service {
    private List<Exchange> exchanges;
    private Processor processor;

    public BrowseEndpoint() {
    }

    public BrowseEndpoint(String uri, CamelContext camelContext) {
        super(uri, camelContext);
    }

    public BrowseEndpoint(String uri, Component component) {
        super(uri, component);
    }

    public BrowseEndpoint(String endpointUri) {
        super(endpointUri);
    }

    public boolean isSingleton() {
        return true;
    }

    public List<Exchange> getExchanges() {
        return exchanges;
    }

    public Producer createProducer() throws Exception {
        return new DefaultProducer(this) {
            public void process(Exchange exchange) throws Exception {
                onExchange(exchange);
            }
        };
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        this.processor = processor;
        return new DefaultConsumer(this, processor);
    }

    protected List<Exchange> createExchangeList() {
        return new CopyOnWriteArrayList<Exchange>();
    }

    /**
     * Invoked on a message exchange being sent by a producer
     *
     * @param exchange the exchange
     * @throws Exception is thrown if failed to process the exchange
     */
    protected void onExchange(Exchange exchange) throws Exception {
        // add a copy of the Exchange at the given time
        exchanges.add(exchange.copy());

        // lets continue processing if there are any consumers
        if (processor != null) {
            processor.process(exchange);
        }
    }

    public void start() throws Exception {
        exchanges = createExchangeList();
    }

    public void stop() throws Exception {
        if (exchanges != null) {
            exchanges.clear();
            exchanges = null;
        }
    }
}

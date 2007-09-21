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
package org.apache.camel.component.seda;

import java.util.concurrent.BlockingQueue;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.ExchangePattern;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultProducer;

/**
 * An implementation of the <a
 * href="http://activemq.apache.org/camel/queue.html">Queue components</a> for
 * asynchronous SEDA exchanges on a {@link BlockingQueue} within a CamelContext
 * 
 * @version $Revision: 519973 $
 */
public class SedaEndpoint extends DefaultEndpoint<Exchange> {
        
    private final class SedaProducer extends DefaultProducer implements AsyncProcessor {
        private SedaProducer(Endpoint endpoint) {
            super(endpoint);
        }
        public void process(Exchange exchange) {
            queue.add(exchange.copy());
        }
        public boolean process(Exchange exchange, AsyncCallback callback) {
            queue.add(exchange.copy());
            callback.done(true);
            return true;
        }
    }

    private BlockingQueue<Exchange> queue;

    public SedaEndpoint(String endpointUri, Component component, BlockingQueue<Exchange> queue) {
        super(endpointUri, component);
        this.queue = queue;
    }

    public SedaEndpoint(String uri, SedaComponent component) {
        this(uri, component, component.createQueue());
    }

    public Producer createProducer() throws Exception {
        return new SedaProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        return new SedaConsumer(this, processor);
    }

    public BlockingQueue<Exchange> getQueue() {
        return queue;
    }

    public boolean isSingleton() {
        return true;
    }

}

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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.BrowsableEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * An implementation of the <a
 * href="http://activemq.apache.org/camel/queue.html">Queue components</a> for
 * asynchronous SEDA exchanges on a {@link BlockingQueue} within a CamelContext
 *
 * @version $Revision$
 */
public class SedaEndpoint extends DefaultEndpoint<Exchange> implements BrowsableEndpoint<Exchange> {
    private BlockingQueue<Exchange> queue;
    private int concurrentConsumers = 1;

    public SedaEndpoint(String endpointUri, Component component, BlockingQueue<Exchange> queue) {
        this(endpointUri, component, queue, 1);
    }

    public SedaEndpoint(String endpointUri, Component component, BlockingQueue<Exchange> queue, int concurrentConsumers) {
        super(endpointUri, component);
        this.queue = queue;
        this.concurrentConsumers = concurrentConsumers;
    }

    public SedaEndpoint(String endpointUri, BlockingQueue<Exchange> queue) {
        this(endpointUri, queue, 1);
    }

    public SedaEndpoint(String endpointUri, BlockingQueue<Exchange> queue, int concurrentConsumers) {
        super(endpointUri);
        ObjectHelper.notNull(queue, "queue");
        this.queue = queue;
        this.concurrentConsumers = concurrentConsumers;
    }
    
    public Producer createProducer() throws Exception {
        return new CollectionProducer(this, getQueue());
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        return new SedaConsumer(this, processor);
    }

    public BlockingQueue<Exchange> getQueue() {
        return queue;
    }
    
    public void setConcurrentConsumers(int concurrentConsumers) {
        this.concurrentConsumers = concurrentConsumers;
    }
    
    public int getConcurrentConsumers() {
        return concurrentConsumers;
    }
    
    public boolean isSingleton() {
        return true;
    }

    public List<Exchange> getExchanges() {
        return new ArrayList<Exchange>(getQueue());
    }
}

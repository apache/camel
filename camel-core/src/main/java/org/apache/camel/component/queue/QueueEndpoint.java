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
package org.apache.camel.component.queue;

import java.util.concurrent.BlockingQueue;

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultProducer;

/**
 * Represents a queue endpoint that uses a {@link BlockingQueue}
 * object to process inbound exchanges.
 *
 * @org.apache.xbean.XBean
 * @version $Revision: 519973 $
 */
public class QueueEndpoint<E extends Exchange> extends DefaultEndpoint<E> {
    private BlockingQueue<E> queue;

    public QueueEndpoint(String uri, QueueComponent<E> component) {
        super(uri, component);
        this.queue = component.createQueue();
    }

    public Producer<E> createProducer() throws Exception {
        return new DefaultProducer(this) {
            public void process(Exchange exchange) {
                queue.add(toExchangeType(exchange));
            }
        };
    }

    public Consumer<E> createConsumer(Processor processor) throws Exception {
        return new QueueEndpointConsumer<E>(this, processor);
    }

    public E createExchange() {
    	// How can we create a specific Exchange if we are generic??
    	// perhaps it would be better if we did not implement this. 
        return (E) new DefaultExchange(getContext());
    }

    public BlockingQueue<E> getQueue() {
        return queue;
    }
    
	public boolean isSingleton() {
		return true;
	}


}

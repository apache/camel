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

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;

/**
 * @version 
 */
public class SedaEndpointTest extends ContextTestSupport {

    private BlockingQueue<Exchange> queue = new ArrayBlockingQueue<Exchange>(1000);

    public void testSedaEndpointUnboundedQueue() throws Exception {
        BlockingQueue<Exchange> unbounded = new LinkedBlockingQueue<Exchange>();
        SedaEndpoint seda = new SedaEndpoint("seda://foo", context.getComponent("seda"), unbounded);        
        assertNotNull(seda);

        assertEquals(Integer.MAX_VALUE, seda.getSize());
        assertSame(unbounded, seda.getQueue());
        assertEquals(1, seda.getConcurrentConsumers());

        Producer prod = seda.createProducer();
        seda.onStarted((SedaProducer) prod);
        assertEquals(1, seda.getProducers().size());

        Consumer cons = seda.createConsumer(new Processor() {
            public void process(Exchange exchange) throws Exception {
                // do nothing
            }
        });
        seda.onStarted((SedaConsumer) cons);
        assertEquals(1, seda.getConsumers().size());

        assertEquals(0, seda.getExchanges().size());
    }

    public void testSedaEndpoint() throws Exception {
        SedaEndpoint seda = new SedaEndpoint("seda://foo", context.getComponent("seda"), queue);
        assertNotNull(seda);

        assertEquals(1000, seda.getSize());
        assertSame(queue, seda.getQueue());
        assertEquals(1, seda.getConcurrentConsumers());

        Producer prod = seda.createProducer();
        seda.onStarted((SedaProducer) prod);
        assertEquals(1, seda.getProducers().size());

        Consumer cons = seda.createConsumer(new Processor() {
            public void process(Exchange exchange) throws Exception {
                // do nothing
            }
        });
        seda.onStarted((SedaConsumer) cons);
        assertEquals(1, seda.getConsumers().size());

        assertEquals(0, seda.getExchanges().size());
    }

    public void testSedaEndpointTwo() throws Exception {
        SedaEndpoint seda = new SedaEndpoint("seda://foo", context.getComponent("seda"), queue, 2);
        assertNotNull(seda);

        assertEquals(1000, seda.getSize());
        assertSame(queue, seda.getQueue());
        assertEquals(2, seda.getConcurrentConsumers());

        Producer prod = seda.createProducer();
        seda.onStarted((SedaProducer) prod);
        assertEquals(1, seda.getProducers().size());

        Consumer cons = seda.createConsumer(new Processor() {
            public void process(Exchange exchange) throws Exception {
                // do nothing
            }
        });
        seda.onStarted((SedaConsumer) cons);
        assertEquals(1, seda.getConsumers().size());

        assertEquals(0, seda.getExchanges().size());
    }

    public void testSedaEndpointSetQueue() throws Exception {
        SedaEndpoint seda = new SedaEndpoint();
        assertNotNull(seda);
        seda.setCamelContext(context);
        seda.setEndpointUriIfNotSpecified("seda://bar");
        assertNotNull(seda.getQueue());
        // overwrite with a new queue
        seda.setQueue(new ArrayBlockingQueue<Exchange>(1000));
        seda.setConcurrentConsumers(2);

        assertEquals(1000, seda.getSize());
        assertNotSame(queue, seda.getQueue());
        assertEquals(2, seda.getConcurrentConsumers());

        Producer prod = seda.createProducer();
        seda.onStarted((SedaProducer) prod);
        assertEquals(1, seda.getProducers().size());

        Consumer cons = seda.createConsumer(new Processor() {
            public void process(Exchange exchange) throws Exception {
                // do nothing
            }
        });
        seda.onStarted((SedaConsumer) cons);
        assertEquals(1, seda.getConsumers().size());

        assertEquals(0, seda.getExchanges().size());
    }

    public void testSedaConsumer() throws Exception {
        SedaEndpoint seda = context.getEndpoint("seda://foo", SedaEndpoint.class);
        Consumer consumer = seda.createConsumer(new Processor() {
            public void process(Exchange exchange) throws Exception {
                // do nothing
            }
        });

        assertSame(seda, consumer.getEndpoint());
        assertNotNull(consumer.toString());
    }
    
    public void testSedaDefaultValue() throws Exception {
        SedaComponent sedaComponent = new SedaComponent();
        sedaComponent.setQueueSize(300);
        sedaComponent.setConcurrentConsumers(3);
        context.addComponent("seda", sedaComponent);
        SedaEndpoint seda = context.getEndpoint("seda://foo", SedaEndpoint.class);
        
        assertEquals(300, seda.getSize());
        assertEquals(3, seda.getConcurrentConsumers());
    }

}

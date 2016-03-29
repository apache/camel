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

import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultExchange;

/**
 * @version 
 */
@Deprecated
public class CollectionProducerTest extends ContextTestSupport {

    private static class MyProducer extends CollectionProducer {

        MyProducer(Endpoint endpoint, Collection<Exchange> queue) {
            super(endpoint, queue);
        }
    }

    public void testCollectionProducer() throws Exception {
        Queue<Exchange> queue = new ArrayBlockingQueue<Exchange>(10);

        Endpoint endpoint = context.getEndpoint("seda://foo");
        MyProducer my = new MyProducer(endpoint, queue);

        my.start();
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("Hello World");
        my.process(exchange);

        Exchange top = queue.poll();
        assertNotNull(top);
        assertEquals("Hello World", top.getIn().getBody());
    }

}

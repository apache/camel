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
package org.apache.camel.component.direct;

import java.util.concurrent.atomic.AtomicBoolean;

import junit.framework.TestCase;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;

/**
 * @version 
 */
public class DirectRouteTest extends TestCase {

    public void testSedaQueue() throws Exception {
        CamelContext container = new DefaultCamelContext();

        final AtomicBoolean invoked = new AtomicBoolean();

        // lets add some routes
        container.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:test.a").to("direct:test.b");
                from("direct:test.b").process(new Processor() {
                    public void process(Exchange e) {
                        invoked.set(true);
                    }
                });
            }
        });

        container.start();

        // now lets fire in a message
        Endpoint endpoint = container.getEndpoint("direct:test.a");
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setHeader("cheese", 123);

        Producer producer = endpoint.createProducer();
        producer.process(exchange);

        // now lets sleep for a while
        assertTrue("Did not receive the message!", invoked.get());

        container.stop();
    }
}

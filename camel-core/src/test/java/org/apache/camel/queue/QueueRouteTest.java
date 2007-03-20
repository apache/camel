/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.queue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;

import org.apache.camel.CamelContainer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultExchange;

/**
 * @version $Revision: 520220 $
 */
public class QueueRouteTest extends TestCase {
	
	static class StringExchange extends DefaultExchange<String, String, String> {		
	}
	
    public void testJmsRoute() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);

        CamelContainer container = new CamelContainer();

        // lets add some routes
        container.routes(new RouteBuilder() {
            public void configure() {
                from("queue:test.a").to("queue:test.b");
                from("queue:test.b").process(new Processor<StringExchange>() {
                    public void onExchange(StringExchange exchange) {
                        System.out.println("Received exchange: " + exchange.getRequest());
                        latch.countDown();
                    }
                });
            }
        });

        
        container.activateEndpoints();
        
        // now lets fire in a message
        Endpoint<StringExchange> endpoint = container.endpoint("queue:test.a");
        StringExchange exchange = new StringExchange();
        exchange.setHeader("cheese", 123);
        endpoint.send(exchange);

        // now lets sleep for a while
        boolean received = latch.await(5, TimeUnit.SECONDS);
        assertTrue("Did not recieve the message!", received);

        container.deactivateEndpoints();
    }
}

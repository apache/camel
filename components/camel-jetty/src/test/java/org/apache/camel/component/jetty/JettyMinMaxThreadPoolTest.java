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
package org.apache.camel.component.jetty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.SynchronizationAdapter;
import org.junit.Test;

public class JettyMinMaxThreadPoolTest extends BaseJettyTest {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        JettyHttpComponent jetty = context.getComponent("jetty", JettyHttpComponent.class);
        jetty.setMinThreads(1);
        jetty.setMaxThreads(5);

        return context;
    }

    @Test
    public void testJettyMinMax() throws Exception {
        final List<String> replies = new ArrayList<String>();

        final CountDownLatch latch = new CountDownLatch(10);

        log.info("Sending 10 messages");
        for (int i = 0; i < 10; i++) {
            template.asyncCallbackRequestBody("http://localhost:{{port}}/myapp/myservice", "" + i, new SynchronizationAdapter() {
                @Override
                public void onDone(Exchange exchange) {
                    String body = exchange.getOut().getBody(String.class);
                    log.info("Got reply " + body);
                    replies.add(body);
                    latch.countDown();
                }
            });
        }

        log.info("Waiting for all messages to be done");
        latch.await(30, TimeUnit.SECONDS);
        log.info("All messages done");

        assertEquals(10, replies.size());

        // sort replies
        Collections.sort(replies);

        log.info("Sorted replies: " + replies.toArray());

        for (int i = 0; i < 10; i++) {
            assertEquals("Bye " + i, replies.get(i));
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("jetty:http://localhost:{{port}}/myapp/myservice")
                        .to("log:input")
                        .delay(1000)
                        .transform(body().prepend("Bye "))
                        .to("log:output");
            }
        };
    }


}
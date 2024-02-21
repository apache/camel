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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.SynchronizationAdapter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class EnrichWithUnitOfWorkTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testEnrichWith() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:routeA").enrichWith("direct:routeB", true, false).body((a, b) -> b);

                from("direct:routeB").enrichWith("direct:routeC", true, false).body((a, b) -> b);

                from("direct:routeC").setBody(constant("Bye World"));
            }
        });
        context.start();

        Exchange out = template.request("direct:routeA", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getMessage().setBody("Hello World");
                exchange.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
                    @Override
                    public void onDone(Exchange exchange) {
                        exchange.getMessage().setBody("Done " + exchange.getMessage().getBody());
                    }
                });
            }
        });
        Assertions.assertFalse(out.isFailed());
        Assertions.assertEquals("Done Bye World", out.getMessage().getBody());
    }

    @Test
    public void testEnrichWithShareUnitOfWork() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:routeA").enrichWith("direct:routeB", true, true).body((a, b) -> b);

                from("direct:routeB").enrichWith("direct:routeC", true, true).body((a, b) -> b);

                from("direct:routeC").setBody(constant("Bye World"));
            }
        });
        context.start();

        Exchange out = template.request("direct:routeA", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getMessage().setBody("Hello World");
                exchange.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
                    @Override
                    public void onDone(Exchange exchange) {
                        exchange.getMessage().setBody("Done " + exchange.getMessage().getBody());
                    }
                });
            }
        });
        Assertions.assertFalse(out.isFailed());
        Assertions.assertEquals("Done Bye World", out.getMessage().getBody());
    }

}

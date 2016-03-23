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
package org.apache.camel.issues;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;

/**
 * Based on user forum request how to do failover with Camel 1.x
 *
 * @version 
 */
public class CustomFailveOverProcessor extends ContextTestSupport {

    public void testFailOver() throws Exception {
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:b").expectedMessageCount(1);

        String out = template.requestBody("direct:start", "Hello World", String.class);

        assertMockEndpointsSatisfied();

        assertEquals("Bye World", out);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .process(new MyFailOverProcessor(template, "direct:a", "direct:b"));

                // always fail
                from("direct:a").to("mock:a").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        throw new IOException("Forced");
                    }
                });

                // return a constant response
                from("direct:b").to("mock:b").transform(constant("Bye World"));
            }
        };
    }

    private static class MyFailOverProcessor implements Processor {

        private List<String> uris;
        private ProducerTemplate template;

        MyFailOverProcessor(ProducerTemplate template, String... uris) {
            this.template = template;
            this.uris = Arrays.asList(uris);
        }

        public void process(Exchange exchange) throws Exception {
            for (String uri : uris) {
                // reset exception to avoid it being a problem if/when we retry
                exchange.setException(null);

                // send the exchange to the next uri in the failover list
                template.send(uri, exchange);

                if (exchange.getException() == null) {
                    // processed okay so we are finished
                    return;
                }
            }
        }

    }
    
}

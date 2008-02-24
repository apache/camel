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
package org.apache.camel.builder;

import java.util.ArrayList;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.TestSupport;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.processor.DelegateProcessor;

/**
 * @version $Revision$
 */
public class InterceptorBuilderWithEndTest extends TestSupport {

    /**
     * Validates that interceptors are executed in the right order.
     *
     * @throws Exception
     */
    public void testRouteWithInterceptor() throws Exception {

        CamelContext container = new DefaultCamelContext();
        final ArrayList<String> order = new ArrayList<String>();

        final DelegateProcessor interceptor1 = new DelegateProcessor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                order.add("START:1");
                super.process(exchange);
                order.add("END:1");
            }
        };
        final DelegateProcessor interceptor2 = new DelegateProcessor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                order.add("START:2");
                super.process(exchange);
                order.add("END:2");
            }
        };

        final Processor orderProcessor = new Processor() {
            public void process(Exchange exchange) {
                order.add("INVOKED");
            }
        };

        final Processor toProcessor = new Processor() {
            public void process(Exchange exchange) {
                order.add("TO");
            }
        };

        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                //from("direct:a").intercept(interceptor1).intercept(interceptor2).to("direct:d");
                from("direct:a").intercept(interceptor1).process(orderProcessor).end().intercept(interceptor2).process(toProcessor);
                /*
                 * TODO keep old DSL? .intercept() .add(interceptor1)
                 * .add(interceptor2) .target().to("direct:d");
                 */
            }
        };
        container.addRoutes(builder);
        container.start();

        Endpoint endpoint = container.getEndpoint("direct:a");
        Exchange exchange = endpoint.createExchange();
        Producer producer = endpoint.createProducer();
        producer.process(exchange);

        ArrayList<String> expected = new ArrayList<String>();
        expected.add("START:1");
        expected.add("INVOKED");
        expected.add("END:1");
        expected.add("START:2");
        expected.add("TO");
        expected.add("END:2");

        log.debug("Interceptor invocation order:" + order);
        assertEquals(expected, order);

    }

}
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
package org.apache.camel.component.stream;

import java.io.OutputStream;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;

/**
 * Unit test for System.out
 */
public class StreamSystemOutTest extends ContextTestSupport {

    public void testStringContent() throws Exception {
        Endpoint endpoint = context.getEndpoint("direct:in");
        Exchange exchange = endpoint.createExchange(ExchangePattern.InOnly);
        exchange.getIn().setBody("Hello World\n");
        Producer producer = endpoint.createProducer();
        producer.start();
        producer.process(exchange);
        producer.stop();

        //template.sendBody("direct:in", "Hello");
        System.out.println("End of test");
    }

/*    public void testBinaryContent() {
        template.sendBody("direct:in", "World".getBytes());
    }*/

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:in").to("stream:out");
            }
        };
    }
    
}

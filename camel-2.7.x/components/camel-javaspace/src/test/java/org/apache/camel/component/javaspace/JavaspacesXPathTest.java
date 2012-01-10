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
package org.apache.camel.component.javaspace;

import java.util.concurrent.CountDownLatch;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * @version 
 */
public class JavaspacesXPathTest extends CamelTestSupport {

    private CountDownLatch latch;

    @Test
    public void testXPath() throws Exception {
        Endpoint directEndpoint = context.getEndpoint("direct:input");
        Exchange exchange = directEndpoint.createExchange(ExchangePattern.InOnly);
        Message message = exchange.getIn();
        String str1 = "<person name='David' city='Rome'/>";
        message.setBody(str1, byte[].class);
        Producer producer = directEndpoint.createProducer();
        producer.start();
        producer.process(exchange);
        String str2 = "<person name='James' city='London'/>";
        message.setBody(str2, byte[].class);
        producer.process(exchange);
        latch = new CountDownLatch(1);
        latch.await();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:input").to("javaspace:jini://localhost?spaceName=mySpace");

                from("javaspace:jini://localhost?spaceName=mySpace&verb=take&concurrentConsumers=1&transactional=false")
                        .filter().xpath("/person[@name='James']").process(new Processor() {
                            public void process(Exchange exc) throws Exception {
                                String body = exc.getIn().getBody(String.class);
                                assertEquals("<person name='James' city='London'/>", body);
                                latch.countDown();
                            }
                        });
            }
        };
    }
}

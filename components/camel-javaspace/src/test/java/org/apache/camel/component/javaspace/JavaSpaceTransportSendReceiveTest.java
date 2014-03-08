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

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version 
 */
public class JavaSpaceTransportSendReceiveTest extends CamelTestSupport {

    private ClassPathXmlApplicationContext spring;

    private CountDownLatch countLatch;

    @Test
    public void testJavaSpaceTransportSendReceive() throws Exception {
        Endpoint directEndpoint = context.getEndpoint("direct:input");
        Exchange exchange = directEndpoint.createExchange(ExchangePattern.InOnly);
        Message message = exchange.getIn();
        message.setBody("DAVID".getBytes(), byte[].class);
        Producer producer = directEndpoint.createProducer();
        int nummsg = 1;
        countLatch = new CountDownLatch(nummsg);
        long start = System.currentTimeMillis();
        producer.start();
        for (int i = 0; i < nummsg; ++i) {
            producer.process(exchange);
        }
        countLatch.await();
        long stop = System.currentTimeMillis();
        log.info("{} took {} milliseconds", getTestMethodName(), stop - start);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        spring = new ClassPathXmlApplicationContext("org/apache/camel/component/javaspace/spring.xml");
        SpringCamelContext ctx = SpringCamelContext.springCamelContext(spring);
        return ctx;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:input").to("javaspace:jini://localhost?spaceName=mySpace");
                from("javaspace:jini://localhost?spaceName=mySpace&verb=take&concurrentConsumers=2&transactional=false")
                        .process(new Processor() {
                            public void process(Exchange exc) throws Exception {
                                byte[] body = exc.getIn().getBody(byte[].class);
                                assertTrue(new String(body).equals("DAVID"));
                                countLatch.countDown();
                            }
                        });
            }
        };
    }

}

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
package org.apache.camel.processor.jpa;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.examples.SendEmail;
import org.junit.Test;

/**
 * @version 
 */
public class JpaTXRollbackTest extends AbstractJpaTest {

    protected static final String SELECT_ALL_STRING = "select x from " + SendEmail.class.getName() + " x";
    private static AtomicInteger foo = new AtomicInteger();
    private static AtomicInteger bar = new AtomicInteger();

    @Test
    public void testTXRollback() throws Exception {
        // first create three records
        template.sendBody("jpa://" + SendEmail.class.getName(), new SendEmail("foo@beer.org"));
        template.sendBody("jpa://" + SendEmail.class.getName(), new SendEmail("bar@beer.org"));
        template.sendBody("jpa://" + SendEmail.class.getName(), new SendEmail("kaboom@beer.org"));

        // should rollback the entire
        MockEndpoint mock = getMockEndpoint("mock:result");
        // we should retry and try again
        mock.expectedMinimumMessageCount(4);

        // start route
        context.startRoute("foo");

        assertMockEndpointsSatisfied();

        assertTrue("Should be >= 2, was: " + foo.intValue(), foo.intValue() >= 2);
        assertTrue("Should be >= 2, was: " + bar.intValue(), bar.intValue() >= 2);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("jpa://" + SendEmail.class.getName() + "?consumer.transacted=true&delay=1000").routeId("foo").noAutoStartup()
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                SendEmail send = exchange.getIn().getBody(SendEmail.class);
                                if ("kaboom@beer.org".equals(send.getAddress())) {
                                    throw new IllegalArgumentException("Forced");
                                }

                                if ("foo@beer.org".equals(send.getAddress())) {
                                    foo.incrementAndGet();
                                } else if ("bar@beer.org".equals(send.getAddress())) {
                                    bar.incrementAndGet();
                                }
                            }
                        })
                        .to("mock:result");
            }
        };
    }

    @Override
    protected String routeXml() {
        return "org/apache/camel/processor/jpa/springJpaRouteTest.xml";
    }

    @Override
    protected String selectAllString() {
        return SELECT_ALL_STRING;
    }
}

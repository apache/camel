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
package org.apache.camel.processor.jpa;

import java.util.Arrays;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jpa.JpaHelper;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.examples.SendEmail;
import org.junit.jupiter.api.Test;

public class JpaTransactedTest extends AbstractJpaTest {
    protected static final String SELECT_ALL_STRING = "select x from " + SendEmail.class.getName() + " x";

    @Test
    public void testTransactedSplit() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(2);
        template.sendBody("direct:split", Arrays.asList(
                new SendEmail("test1@example.org"), new SendEmail("test2@example.org")));
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testTransactedMulticast() throws Exception {
        template.sendBody("direct:multicast", new SendEmail("test@example.org"));
    }

    @Test
    public void testTransactedRecipientList() throws Exception {
        template.sendBody("direct:recipient", new SendEmail("test@example.org"));
    }

    @Test
    public void testTransactedEnrich() throws Exception {
        template.sendBody("direct:enrich", new SendEmail("test@example.org"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:split")
                        .transacted().split().body()
                        .to("jpa://" + SendEmail.class.getName())
                        .to("mock:result");

                from("direct:multicast")
                        .transacted().multicast()
                        .to("jpa://" + SendEmail.class.getName(), "jpa://" + SendEmail.class.getName());

                from("direct:recipient")
                        .transacted().recipientList(
                                constant("jpa://" + SendEmail.class.getName() + "," + "jpa://" + SendEmail.class.getName()));

                from("direct:enrich")
                        .transacted().enrich("jpa://" + SendEmail.class.getName(), new AggregationStrategy() {
                            @Override
                            public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
                                JpaHelper.copyEntityManagers(oldExchange, newExchange);
                                return oldExchange;
                            }
                        })
                        .to("jpa://" + SendEmail.class.getName());
            }
        };
    }

    @Override
    protected String routeXml() {
        return "org/apache/camel/processor/jpa/springJpaRoutePoolingTest.xml";
    }

    @Override
    protected String selectAllString() {
        return SELECT_ALL_STRING;
    }
}

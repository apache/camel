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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.examples.SendEmail;
import org.apache.camel.spring.SpringRouteBuilder;
import org.junit.Test;

/**
 * @version 
 */
public class JpaRoutemaxMessagesPerPollTest extends AbstractJpaTest {
    protected static final String SELECT_ALL_STRING = "select x from " + SendEmail.class.getName() + " x";

    @Test
    public void testRouteJpa() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);

        template.sendBody("direct:start", new SendEmail("one@somewhere.org"));
        template.sendBody("direct:start", new SendEmail("two@somewhere.org"));
        template.sendBody("direct:start", new SendEmail("three@somewhere.org"));

        assertMockEndpointsSatisfied();
        assertEntityInDB(3);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new SpringRouteBuilder() {
            public void configure() {
                from("direct:start").to("jpa://" + SendEmail.class.getName());

                from("jpa://" + SendEmail.class.getName() + "?maxMessagesPerPoll=2&consumeDelete=false&delay=5000").to("mock:result");
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
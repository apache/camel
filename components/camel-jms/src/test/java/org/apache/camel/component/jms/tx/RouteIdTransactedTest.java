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
package org.apache.camel.component.jms.tx;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.AbstractSpringJMSTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tags({ @Tag("not-parallel"), @Tag("spring"), @Tag("tx") })
public class RouteIdTransactedTest extends AbstractSpringJMSTestSupport {

    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(
                "/org/apache/camel/component/jms/tx/RouteIdTransactedTest.xml");
    }

    @Test
    public void testRouteId() throws Exception {
        getMockEndpoint("mock:error").expectedMessageCount(0);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("activemq:queue:RouteIdTransactedTest", "Hello World");

        MockEndpoint.assertIsSatisfied(context);

        String id = context.getRouteDefinitions().get(0).getId();
        assertEquals("myCoolRoute", id);
    }

    @Test
    public void testRouteIdFailed() throws Exception {
        getMockEndpoint("mock:error").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        template.sendBody("activemq:queue:RouteIdTransactedTest", "Kaboom");

        MockEndpoint.assertIsSatisfied(context);

        String id = context.getRouteDefinitions().get(0).getId();
        assertEquals("myCoolRoute", id);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("activemq:queue:RouteIdTransactedTest?transacted=true").id("myCoolRoute")
                        .onException(IllegalArgumentException.class).handled(true).to("log:bar").to("mock:error").end()
                        .transacted()
                        .choice()
                        .when(body().contains("Kaboom")).throwException(new IllegalArgumentException("Damn"))
                        .otherwise()
                        .to("mock:result")
                        .end();

            }
        };
    }
}

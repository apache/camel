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
package org.apache.camel.component.jms;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Session;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.springframework.jms.support.destination.DestinationResolutionException;
import org.springframework.jms.support.destination.DestinationResolver;

/**
 *
 */
public class JmsInOnlyInvalidDestinationTest extends AbstractJMSTest {

    @BindToRegistry("myResolver")
    private final MyDestinationResolver resolver = new MyDestinationResolver();

    @Test
    public void testInvalidDestination() throws Exception {
        getMockEndpoint("mock:dead").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:foo", "Hello World", "foo",
                "activemq:queue:JmsInOnlyInvalidDestinationTest?destinationResolver=#myResolver");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("mock:dead").maximumRedeliveries(1));

                from("direct:foo").recipientList(header("foo"));
            }
        };
    }

    private static class MyDestinationResolver implements DestinationResolver {

        @Override
        public Destination resolveDestinationName(Session session, String destinationName, boolean pubSubDomain)
                throws JMSException {
            throw new DestinationResolutionException("Forced");
        }
    }
}

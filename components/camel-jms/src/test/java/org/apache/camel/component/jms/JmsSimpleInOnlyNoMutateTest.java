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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

/**
 * A simple in only test that does not mutate the message
 */
public class JmsSimpleInOnlyNoMutateTest extends AbstractJMSTest {

    protected final String componentName = "activemq";

    @Test
    public void testRequestReplyNoMutate() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);
        result.expectedBodiesReceived("Hello World");
        result.expectedHeaderReceived("foo", 123);

        template.send("activemq:queue:helloJmsSimpleInOnlyNoMutateTest", exchange -> {
            exchange.getIn().setBody("Hello World");
            exchange.getIn().setHeader("foo", 123);
        });

        result.assertIsSatisfied();
    }

    @Override
    public String getComponentName() {
        return componentName;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("activemq:queue:helloJmsSimpleInOnlyNoMutateTest").to("log:foo").to("mock:result");
            }
        };
    }
}

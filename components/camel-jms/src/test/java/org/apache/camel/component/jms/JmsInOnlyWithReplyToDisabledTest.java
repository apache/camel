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
import org.junit.jupiter.api.Test;

public class JmsInOnlyWithReplyToDisabledTest extends AbstractJMSTest {

    @Test
    public void testSendInOnlyWithReplyTo() throws Exception {
        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:bar").expectedMessageCount(0);
        getMockEndpoint("mock:done").expectedMessageCount(1);

        template.sendBody("direct:start", "World");

        assertMockEndpointsSatisfied();
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
                from("direct:start")
                        .to("activemq:queue:JmsInOnlyWithReplyToDisabledTestRequest?replyTo=queue:JmsInOnlyWithReplyToDisabledTestReply&disableReplyTo=true")
                        .to("mock:done");

                from("activemq:queue:JmsInOnlyWithReplyToDisabledTestRequest")
                        .to("mock:foo")
                        .transform(body().prepend("Bye "));

                from("activemq:queue:JmsInOnlyWithReplyToDisabledTestReply")
                        .to("mock:bar");
            }
        };
    }
}

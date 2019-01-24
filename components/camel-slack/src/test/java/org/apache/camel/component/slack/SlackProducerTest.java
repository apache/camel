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
package org.apache.camel.component.slack;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.direct.DirectEndpoint;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;

import org.junit.Test;

public class SlackProducerTest extends CamelTestSupport {

    @EndpointInject(uri = "mock:errors")
    MockEndpoint errors;

    @EndpointInject(uri = "direct:test")
    DirectEndpoint test;

    @EndpointInject(uri = "direct:error")
    DirectEndpoint error;

    @Test
    public void testSlackMessage() throws Exception {
        errors.expectedMessageCount(0);

        template.sendBody(test, "Hello from Camel!");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSlackError() throws Exception {
        errors.expectedMessageCount(1);

        template.sendBody(error, "Error from Camel!");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                SlackComponent slack = new SlackComponent();
                slack.setWebhookUrl(System.getProperty("SLACK_HOOK", "https://hooks.slack.com/services/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"));
                context.addComponent("slack", slack);

                onException(Exception.class).handled(true).to(errors);

                final String slacUser =  System.getProperty("SLACK_USER", "CamelTest");
                from(test).to(String.format("slack:#general?iconEmoji=:camel:&username=%s", slacUser));

                from(error).to(String.format("slack:#badchannel?iconEmoji=:camel:&username=%s", slack));
            }
        };
    }
}

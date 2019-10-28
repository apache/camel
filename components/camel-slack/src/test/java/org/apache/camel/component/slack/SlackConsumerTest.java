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
package org.apache.camel.component.slack;

import java.io.IOException;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

public class SlackConsumerTest extends CamelTestSupport {

    private String token;
    private String hook;

    @Override
    @Before
    public void setUp() throws Exception {
        token = System.getProperty("SLACK_TOKEN");
        hook = System.getProperty("SLACK_HOOK", "https://hooks.slack.com/services/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");

        assumeCredentials();
        super.setUp();
    }

    @Test
    public void testConsumePrefixedMessages() throws Exception {
        final String message = "Hi camel";
        sendMessage(message);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).simple("${body.getText()}").isEqualTo(message);

        assertMockEndpointsSatisfied();
    }

    private void assumeCredentials() {
        Assume.assumeThat("Please specify a Slack access token", token, CoreMatchers.notNullValue());
        Assume.assumeThat("Please specify a Slack application webhook URL", hook, CoreMatchers.notNullValue());
    }

    private void sendMessage(String message) throws IOException {
        HttpClient client = HttpClients.createDefault();
        HttpPost post = new HttpPost(hook);
        post.setHeader("Content-type", "application/json");
        post.setEntity(new StringEntity(String.format("{ 'text': '%s'}", message)));
        HttpResponse response = client.execute(post);
        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(String.format("slack://general?token=RAW(%s)&maxResults=1", token))
                    .to("mock:result");
            }
        };
    }
}

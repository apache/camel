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

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class SlackConsumerTest extends CamelTestSupport {

    private String token;
    private String hook;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        token = System.getProperty("SLACK_TOKEN");
        hook = System.getProperty("SLACK_HOOK");

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

        MockEndpoint.assertIsSatisfied(context);
    }

    private void assumeCredentials() {
        assumeTrue(token != null, "Please specify a Slack access token");
        assumeTrue(hook != null, "Please specify a Slack application webhook URL");
    }

    private void sendMessage(String message) throws IOException {
        RequestBody requestBody
                = RequestBody.create(MediaType.parse("application/json"), String.format("{ 'text': '%s'}", message));

        Request request = new Request.Builder()
                .url(hook)
                .post(requestBody)
                .build();

        OkHttpClient client = new OkHttpClient();
        Call call = client.newCall(request);
        Response response = call.execute();

        assertEquals(200, response.code());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(String.format("slack://general?token=RAW(%s)&maxResults=1", token))
                        .to("mock:result");
            }
        };
    }
}

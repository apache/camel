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
package org.apache.camel.component.pubnub;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import static org.apache.camel.component.pubnub.PubNubConstants.TIMETOKEN;

public class PubNubPublishTest extends PubNubTestBase {
    private String endpoint = "pubnub:someChannel?pubnub=#pubnub";

    @EndpointInject(uri = "mock:result")
    private MockEndpoint mockResult;

    @Test
    public void testPubNub() throws Exception {
        stubFor(post(urlPathEqualTo("/publish/myPublishKey/mySubscribeKey/0/someChannel/0")).willReturn(aResponse().withStatus(200).withBody("[1,\"Sent\",\"14598111595318003\"]")));
        mockResult.expectedMessageCount(1);
        mockResult.expectedHeaderReceived(TIMETOKEN, "14598111595318003");
        template.sendBody("direct:publish", new Hello("Hi"));
        assertMockEndpointsSatisfied();
    }

    @Test(expected = CamelExecutionException.class)
    public void testPublishEmptyBody() throws Exception {
        template.sendBody("direct:publish", null);
    }

    @Test
    public void testFireWithOperationHeader() throws Exception {
        stubFor(get(urlPathEqualTo("/publish/myPublishKey/mySubscribeKey/0/someChannel/0/%22Hi%22"))
            .willReturn(aResponse().withBody("[1,\"Sent\",\"14598111595318003\"]")));
        mockResult.expectedMessageCount(1);
        mockResult.expectedHeaderReceived(TIMETOKEN, "14598111595318003");

        template.sendBodyAndHeader("direct:publish", "Hi", PubNubConstants.OPERATION, "FIRE");
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:publish").to(endpoint).to("mock:result");
            }
        };
    }

    static class Hello {
        private String message;

        Hello(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

}

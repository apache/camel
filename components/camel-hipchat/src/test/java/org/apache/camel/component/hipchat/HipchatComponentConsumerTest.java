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
package org.apache.camel.component.hipchat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.http.HttpEntity;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HipchatComponentConsumerTest extends CamelTestSupport {
    private CloseableHttpResponse closeableHttpResponse = mock(CloseableHttpResponse.class);

    @EndpointInject(uri = "hipchat:http:api.hipchat.com?authToken=anything&consumeUsers=@AUser")
    private ProducerTemplate template;

    @EndpointInject(uri = "mock:result")
    private MockEndpoint result;

    @Test
    public void sendInOnly() throws Exception {
        result.expectedMessageCount(1);
        String expectedResponse = "{\n"
                +
                "  \"items\" : [\n"
                +
                "    {\n"
                +
                "      \"date\" : \"2015-01-19T22:07:11.030740+00:00\",\n"
                +
                "      \"from\" : {\n"
                +
                "        \"id\" : 1647095,\n"
                +
                "        \"links\" : {\n"
                +
                "          \"self\" : \"https://api.hipchat.com/v2/user/1647095\"\n"
                +
                "        },\n"
                +
                "        \"mention_name\" : \"notifier\",\n"
                +
                "        \"name\" : \"Message Notifier\"\n"
                +
                "      },\n"
                +
                "      \"id\" : \"6567c6f7-7c1b-43cf-bed0-792b1d092919\",\n"
                +
                "      \"mentions\" : [ ],\n"
                +
                "      \"message\" : \"Unit test Alert\",\n"
                +
                "      \"type\" : \"message\"\n"
                +
                "    }\n"
                +
                "  ],\n"
                +
                "  \"links\" : {\n"
                +
                "    \"self\" : \"https://api.hipchat.com/v2/user/%40ShreyasPurohit/history/latest\"\n"
                +
                "  },\n"
                +
                "  \"maxResults\" : 1,\n"
                +
                "  \"startIndex\" : 0\n"
                +
                "}";
        HttpEntity mockHttpEntity = mock(HttpEntity.class);
        when(mockHttpEntity.getContent()).thenReturn(new ByteArrayInputStream(expectedResponse.getBytes(StandardCharsets.UTF_8)));
        when(closeableHttpResponse.getEntity()).thenReturn(mockHttpEntity);
        when(closeableHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, ""));

        assertMockEndpointsSatisfied();

        assertCommonResultExchange(result.getExchanges().get(0));
    }

    //TODO
    @Test
    public void sendInOnlyMultipleUsers() throws Exception {
        result.expectedMessageCount(1);
        String expectedResponse = "{\n"
                +
                "  \"items\" : [\n"
                +
                "    {\n"
                +
                "      \"date\" : \"2015-01-19T22:07:11.030740+00:00\",\n"
                +
                "      \"from\" : {\n"
                +
                "        \"id\" : 1647095,\n"
                +
                "        \"links\" : {\n"
                +
                "          \"self\" : \"https://api.hipchat.com/v2/user/1647095\"\n"
                +
                "        },\n"
                +
                "        \"mention_name\" : \"notifier\",\n"
                +
                "        \"name\" : \"Message Notifier\"\n"
                +
                "      },\n"
                +
                "      \"id\" : \"6567c6f7-7c1b-43cf-bed0-792b1d092919\",\n"
                +
                "      \"mentions\" : [ ],\n"
                +
                "      \"message\" : \"Unit test Alert\",\n"
                +
                "      \"type\" : \"message\"\n"
                +
                "    }\n"
                +
                "  ],\n"
                +
                "  \"links\" : {\n"
                +
                "    \"self\" : \"https://api.hipchat.com/v2/user/%40ShreyasPurohit/history/latest\"\n"
                +
                "  },\n"
                +
                "  \"maxResults\" : 1,\n"
                +
                "  \"startIndex\" : 0\n"
                +
                "}";
        HttpEntity mockHttpEntity = mock(HttpEntity.class);
        when(mockHttpEntity.getContent()).thenReturn(new ByteArrayInputStream(expectedResponse.getBytes(StandardCharsets.UTF_8)));
        when(closeableHttpResponse.getEntity()).thenReturn(mockHttpEntity);
        when(closeableHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, ""));

        assertMockEndpointsSatisfied();

        assertCommonResultExchange(result.getExchanges().get(0));
    }

    @Test
    public void sendInOnlyNoResponse() throws Exception {
        result.expectedMessageCount(0);
        HttpEntity mockHttpEntity = mock(HttpEntity.class);
        when(mockHttpEntity.getContent()).thenReturn(null);
        when(closeableHttpResponse.getEntity()).thenReturn(mockHttpEntity);
        when(closeableHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, ""));

        assertMockEndpointsSatisfied();
    }

    private void assertCommonResultExchange(Exchange resultExchange) {
        assertIsInstanceOf(String.class, resultExchange.getIn().getBody());
        assertEquals("Unit test Alert", resultExchange.getIn().getBody(String.class));
        assertEquals("@AUser", resultExchange.getIn().getHeader(HipchatConstants.FROM_USER));
        assertEquals("2015-01-19T22:07:11.030740+00:00", resultExchange.getIn().getHeader(HipchatConstants.MESSAGE_DATE));
        assertNotNull(resultExchange.getIn().getHeader(HipchatConstants.FROM_USER_RESPONSE_STATUS));
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        final CamelContext context = super.createCamelContext();
        HipchatComponent component = new HipchatComponent(context) {
            @Override
            protected HipchatEndpoint getHipchatEndpoint(String uri) {
                return new HipchatEPSuccessTestSupport(uri, this, null, closeableHttpResponse);
            }
        };
        context.addComponent("hipchat", component);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("hipchat:http://api.hipchat.com?authToken=anything&consumeUsers=@AUser")
                        .to("mock:result");
            }
        };
    }

}

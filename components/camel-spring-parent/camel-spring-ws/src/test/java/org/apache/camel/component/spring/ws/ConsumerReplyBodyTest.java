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
package org.apache.camel.component.spring.ws;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.xml.transform.stream.StreamSource;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.spring.ws.bean.CamelEndpointMapping;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.test.spring.junit6.CamelSpringTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.ws.test.server.MockWebServiceClient;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.ws.test.server.RequestCreators.withPayload;
import static org.springframework.ws.test.server.ResponseMatchers.noFault;
import static org.springframework.ws.test.server.ResponseMatchers.payload;
import static org.springframework.ws.test.server.ResponseMatchers.serverOrReceiverFault;

/**
 * The reply of a spring-ws consumer route: a null body, a body that is not XML and an InputStream body.
 */
@ContextConfiguration
@CamelSpringTest
public class ConsumerReplyBodyTest extends CamelTestSupport {

    private static final String REPLY = "<Reply xmlns=\"http://www.webserviceX.NET/\"><text>Hello</text></Reply>";

    @Autowired
    private CamelEndpointMapping endpointMapping;

    @Autowired
    private ApplicationContext applicationContext;

    private MockWebServiceClient mockClient;

    @BeforeEach
    public void createClient() {
        mockClient = MockWebServiceClient.createClient(applicationContext);
    }

    @Override
    protected void bindToRegistry(Registry registry) throws Exception {
        registry.bind("endpointMapping", this.endpointMapping);
    }

    @Test
    public void nullBodyGivesEmptyResponse() throws Exception {
        mockClient.sendRequest(withPayload(request("NullReply")))
                .andExpect(noFault())
                .andExpect((request, response) -> assertNull(response.getPayloadSource(),
                        "A null reply body should give an empty SOAP body, not the request payload"));
    }

    @Test
    public void nullBodyFromSetBodyGivesEmptyResponse() throws Exception {
        mockClient.sendRequest(withPayload(request("SetBodyNullReply")))
                .andExpect(noFault())
                .andExpect((request, response) -> assertNull(response.getPayloadSource(),
                        "A null reply body should give an empty SOAP body, not the request payload"));
    }

    @Test
    public void outMessageWithoutBodyGivesEmptyResponse() throws Exception {
        mockClient.sendRequest(withPayload(request("OutNoBodyReply")))
                .andExpect(noFault())
                .andExpect((request, response) -> assertNull(response.getPayloadSource(),
                        "An OUT message without a body should give an empty SOAP body, not the request payload"));
    }

    @Test
    public void bodyNotConvertibleToSourceGivesFault() throws Exception {
        mockClient.sendRequest(withPayload(request("MapReply")))
                .andExpect(serverOrReceiverFault());
    }

    @Test
    public void inputStreamBodyIsSentAsReply() throws Exception {
        mockClient.sendRequest(withPayload(request("StreamReply")))
                .andExpect(noFault())
                .andExpect(payload(new StreamSource(new StringReader(REPLY))));
    }

    private static StreamSource request(String name) {
        return new StreamSource(
                new StringReader("<" + name + " xmlns=\"http://www.webserviceX.NET/\"><symbol>GOOG</symbol></" + name + ">"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                from("spring-ws:rootqname:{http://www.webserviceX.NET/}NullReply?endpointMapping=#endpointMapping")
                        .process(e -> e.getMessage().setBody(null));

                from("spring-ws:rootqname:{http://www.webserviceX.NET/}SetBodyNullReply?endpointMapping=#endpointMapping")
                        .setBody(constant(null));

                from("spring-ws:rootqname:{http://www.webserviceX.NET/}OutNoBodyReply?endpointMapping=#endpointMapping")
                        .process(e -> e.getOut().setHeader("foo", "bar"));

                from("spring-ws:rootqname:{http://www.webserviceX.NET/}MapReply?endpointMapping=#endpointMapping")
                        .setBody(constant(Map.of("text", "Hello")));

                from("spring-ws:rootqname:{http://www.webserviceX.NET/}StreamReply?endpointMapping=#endpointMapping")
                        .process(e -> e.getMessage()
                                .setBody(new ByteArrayInputStream(REPLY.getBytes(StandardCharsets.UTF_8))));
            }
        };
    }
}

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
package org.apache.camel.coap;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A body that cannot be converted to the CoAP payload must fail, instead of being sent as an empty payload.
 */
public class CoAPUnconvertibleBodyTest extends CoAPTestSupport {

    @Test
    void producerFailsPostOfABodyThatCannotBeConverted() throws Exception {
        MockEndpoint sink = getMockEndpoint("mock:sink");
        sink.expectedMessageCount(0);

        Exchange out = template.request(sinkUri(), e -> {
            e.getIn().setHeader(CoAPConstants.COAP_METHOD, CoAPConstants.METHOD_POST);
            e.getIn().setBody(Map.of("temperature", 21));
        });

        assertInstanceOf(InvalidPayloadException.class, out.getException());
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void producerFailsPutOfABodyThatCannotBeConverted() throws Exception {
        MockEndpoint sink = getMockEndpoint("mock:sink");
        sink.expectedMessageCount(0);

        Exchange out = template.request(sinkUri(), e -> {
            e.getIn().setHeader(CoAPConstants.COAP_METHOD, CoAPConstants.METHOD_PUT);
            e.getIn().setBody(new Reading(21));
        });

        assertInstanceOf(InvalidPayloadException.class, out.getException());
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void producerSendsAnEmptyPayloadForAPostWithoutBody() throws Exception {
        MockEndpoint sink = getMockEndpoint("mock:sink");
        sink.expectedMessageCount(1);

        Exchange out = template.request(sinkUri(), e -> e.getIn().setHeader(CoAPConstants.COAP_METHOD,
                CoAPConstants.METHOD_POST));

        assertNull(out.getException());
        MockEndpoint.assertIsSatisfied(context);
        assertEquals(0, sink.getReceivedExchanges().get(0).getIn().getBody(byte[].class).length);
    }

    @Test
    void consumerAnswersAReplyBodyThatCannotBeConvertedWithServerError() throws Exception {
        CoapResponse response = createClient("/mapReply").get();

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, response.getCode());
        assertEquals(0, response.getPayload().length, "the response must not carry the exception's message");
    }

    @Test
    void consumerAnswersAPojoReplyBodyWithServerError() throws Exception {
        CoapResponse response = createClient("/pojoReply").get();

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, response.getCode());
    }

    @Test
    void consumerAnswersANullReplyBodyWithAnEmptyContent() throws Exception {
        CoapResponse response = createClient("/nullReply").get();

        assertEquals(ResponseCode.CONTENT, response.getCode());
        assertEquals(0, response.getPayload().length);
    }

    private String sinkUri() {
        return "coap://localhost:" + PORT.getPort() + "/sink";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("coap://localhost:%d/sink", PORT.getPort())
                        .to("mock:sink");

                fromF("coap://localhost:%d/mapReply", PORT.getPort())
                        .process(e -> e.getMessage().setBody(Map.of("temperature", 21)));

                fromF("coap://localhost:%d/pojoReply", PORT.getPort())
                        .process(e -> e.getMessage().setBody(new Reading(21)));

                fromF("coap://localhost:%d/nullReply", PORT.getPort())
                        .setBody(constant(null));
            }
        };
    }

    private record Reading(int temperature) {
    }
}

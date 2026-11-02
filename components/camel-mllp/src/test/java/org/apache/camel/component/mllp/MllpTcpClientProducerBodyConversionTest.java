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
package org.apache.camel.component.mllp;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.builder.ExchangeBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.test.mllp.Hl7TestMessageGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The producer converts a body that is not a String or a byte array, instead of sending an empty message.
 */
public class MllpTcpClientProducerBodyConversionTest extends CamelTestSupport {

    @RegisterExtension
    AvailablePortFinder.Port mllpPort = AvailablePortFinder.find();

    @EndpointInject("mock://received")
    MockEndpoint received;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("mllp://localhost:%d?autoAck=true&stringPayload=false", mllpPort.getPort())
                        .to(received);
            }
        };
    }

    private String producerUri() {
        return String.format("mllp://localhost:%d?receiveTimeout=5000&readTimeout=5000&charsetName=UTF-8",
                mllpPort.getPort());
    }

    @Test
    public void testInputStreamBody() throws Exception {
        String testMessage = Hl7TestMessageGenerator.generateMessage();
        byte[] testBytes = testMessage.getBytes(StandardCharsets.ISO_8859_1);
        received.expectedMessageCount(1);

        Exchange exchange = template.send(producerUri(),
                e -> e.getMessage().setBody(new ByteArrayInputStream(testBytes)));

        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
        assertNull(exchange.getException());
        assertArrayEquals(testBytes, received.getExchanges().get(0).getMessage().getBody(byte[].class));
        assertTrue(exchange.getMessage().getHeader(MllpConstants.MLLP_ACKNOWLEDGEMENT_STRING, String.class)
                .contains("MSA|AA|00001"));
    }

    @Test
    public void testTextBodyUsesCharsetName() throws Exception {
        String testMessage = Hl7TestMessageGenerator.generateMessage().replace("PID|", "PID|Ærø|");
        received.expectedMessageCount(1);

        Exchange exchange = template.send(producerUri(),
                e -> e.getMessage().setBody(new StringBuilder(testMessage)));

        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
        assertNull(exchange.getException());
        assertArrayEquals(testMessage.getBytes(StandardCharsets.UTF_8),
                received.getExchanges().get(0).getMessage().getBody(byte[].class));
    }

    @Test
    public void testStringBodyCharsetPropertyIsTheCharsetUsed() throws Exception {
        String testMessage = Hl7TestMessageGenerator.generateMessage().replace("PID|", "PID|Ærø|");
        received.expectedMessageCount(1);

        // an exchange from another endpoint: its charset wins over charsetName=ISO-8859-1 of the endpoint
        Exchange exchange = template.send(producerUri().replace("UTF-8", "ISO-8859-1"),
                ExchangeBuilder.anExchange(context).withProperty(Exchange.CHARSET_NAME, "UTF-8").withBody(testMessage)
                        .build());

        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
        assertNull(exchange.getException());
        assertArrayEquals(testMessage.getBytes(StandardCharsets.UTF_8),
                received.getExchanges().get(0).getMessage().getBody(byte[].class));
        assertEquals("UTF-8", exchange.getProperty(Exchange.CHARSET_NAME));
    }

    @Test
    public void testConvertedBodyLeavesNoCharsetProperty() throws Exception {
        String testMessage = Hl7TestMessageGenerator.generateMessage().replace("PID|", "PID|Ærø|");
        received.expectedMessageCount(1);

        // an exchange from another endpoint, with no charset of its own
        Exchange exchange = template.send(producerUri(),
                ExchangeBuilder.anExchange(context).withBody(new StringReader(testMessage)).build());

        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
        assertNull(exchange.getException());
        assertArrayEquals(testMessage.getBytes(StandardCharsets.UTF_8),
                received.getExchanges().get(0).getMessage().getBody(byte[].class));
        assertNull(exchange.getProperty(Exchange.CHARSET_NAME));
    }

    @Test
    public void testBodyThatCannotBeConvertedFails() throws Exception {
        received.expectedMessageCount(0);

        Exchange exchange = template.send(producerUri(), e -> e.getMessage().setBody(Map.of("key", "value")));

        assertInstanceOf(MllpInvalidMessageException.class, exchange.getException());
        assertInstanceOf(NoTypeConversionAvailableException.class, exchange.getException().getCause());
        MockEndpoint.assertIsSatisfied(context);
    }
}

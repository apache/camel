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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.ExchangeBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The charset of a message: MSH-18 first, then charsetName of the endpoint, and not the CamelCharsetName exchange
 * property, which may come from another endpoint.
 */
public class MllpCharsetTest extends CamelTestSupport {

    static final String PATIENT = "Sørensen^Łucja";
    static final String ACKNOWLEDGEMENT
            = "MSH|^~\\&|RAPP|RFAC|APP|FAC|20261122120000||ACK^A04|00002|D|2.6||||||UNICODE UTF-8" + '\r'
              + "MSA|AA|00001|Received for Łucja Sørensen";

    @RegisterExtension
    AvailablePortFinder.Port relayPort = AvailablePortFinder.find();

    @RegisterExtension
    AvailablePortFinder.Port sinkPort = AvailablePortFinder.find();

    @RegisterExtension
    AvailablePortFinder.Port textPort = AvailablePortFinder.find();

    @RegisterExtension
    AvailablePortFinder.Port acknowledgementPort = AvailablePortFinder.find();

    @EndpointInject("mock://relayed")
    MockEndpoint relayed;

    @EndpointInject("mock://sink")
    MockEndpoint sink;

    @EndpointInject("mock://text")
    MockEndpoint text;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // relays from an mllp consumer with the default charset to an mllp producer with charsetName=UTF-8
                fromF("mllp://localhost:%d?autoAck=true", relayPort.getPort())
                        .toF("mllp://localhost:%d?receiveTimeout=5000&readTimeout=5000&charsetName=UTF-8",
                                sinkPort.getPort())
                        .to(relayed);

                fromF("mllp://localhost:%d?autoAck=true&stringPayload=false", sinkPort.getPort())
                        .to(sink);

                fromF("mllp://localhost:%d?autoAck=true", textPort.getPort())
                        .to(text);

                fromF("mllp://localhost:%d?autoAck=false", acknowledgementPort.getPort())
                        .setProperty(MllpConstants.MLLP_ACKNOWLEDGEMENT_STRING, constant(ACKNOWLEDGEMENT));
            }
        };
    }

    private static String message(String msh18, String patientName) {
        return "MSH|^~\\&|APP|FAC|RAPP|RFAC|20261122120000||ADT^A04|00001|D|2.6||||||" + msh18 + '\r'
               + "PID|1||1234||" + patientName + '\r';
    }

    private String producerUri(int port, String charsetName) {
        String uri = String.format("mllp://localhost:%d?receiveTimeout=5000&readTimeout=5000", port);
        return charsetName != null ? uri + "&charsetName=" + charsetName : uri;
    }

    private byte[] sendToSink(String producerCharsetName, Object body, String exchangeCharsetName) throws Exception {
        sink.expectedMessageCount(1);

        ExchangeBuilder builder = ExchangeBuilder.anExchange(context).withBody(body);
        if (exchangeCharsetName != null) {
            builder.withProperty(Exchange.CHARSET_NAME, exchangeCharsetName);
        }
        Exchange exchange = template.send(producerUri(sinkPort.getPort(), producerCharsetName), builder.build());

        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
        assertNull(exchange.getException());
        assertEquals(exchangeCharsetName, exchange.getProperty(Exchange.CHARSET_NAME),
                "the producer leaves the charset of the exchange as it was");
        return sink.getExchanges().get(0).getMessage().getBody(byte[].class);
    }

    @Test
    public void testProducerEncodesInCharsetOfMsh18() throws Exception {
        String testMessage = message("UNICODE UTF-8", PATIENT);

        // the producer has the default charset (ISO-8859-1), which has no Ł
        assertArrayEquals(testMessage.getBytes(StandardCharsets.UTF_8), sendToSink(null, testMessage, null));
        assertEquals("UTF-8", sink.getExchanges().get(0).getProperty(Exchange.CHARSET_NAME),
                "the consumer gives the charset of a byte array body");
    }

    @Test
    public void testProducerEncodesInCharsetOfMsh18OverCharsetName() throws Exception {
        String testMessage = message("8859/2", PATIENT);

        assertArrayEquals(testMessage.getBytes(Charset.forName("ISO-8859-2")), sendToSink("UTF-8", testMessage, null));
    }

    @Test
    public void testProducerEncodesInCharsetNameWithoutMsh18() throws Exception {
        String testMessage = message("", PATIENT);

        // the charset of the exchange, such as one set by another endpoint, does not win over charsetName
        assertArrayEquals(testMessage.getBytes(StandardCharsets.UTF_8), sendToSink("UTF-8", testMessage, "ISO-8859-1"));
    }

    @Test
    public void testProducerEncodesTextBodyInCharsetName() throws Exception {
        String testMessage = message("", PATIENT);

        assertArrayEquals(testMessage.getBytes(StandardCharsets.UTF_8),
                sendToSink("UTF-8", new StringBuilder(testMessage), "ISO-8859-1"));
    }

    @Test
    public void testConsumerDecodesInCharsetOfMsh18() throws Exception {
        String testMessage = message("UNICODE UTF-8", PATIENT);
        text.expectedMessageCount(1);

        // a byte array is sent as it is
        template.sendBody(producerUri(textPort.getPort(), null), testMessage.getBytes(StandardCharsets.UTF_8));

        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
        Exchange received = text.getExchanges().get(0);
        assertEquals(testMessage, received.getMessage().getBody(String.class));
        assertEquals("UNICODE UTF-8", received.getMessage().getHeader(MllpConstants.MLLP_CHARSET));
        assertNull(received.getProperty(Exchange.CHARSET_NAME),
                "the consumer does not give the charset of a String body to the rest of the route");
    }

    @Test
    public void testRelayKeepsCharsetOfMsh18() throws Exception {
        String testMessage = message("UNICODE UTF-8", PATIENT);
        relayed.expectedMessageCount(1);
        sink.expectedMessageCount(1);

        template.sendBody(producerUri(relayPort.getPort(), null), testMessage.getBytes(StandardCharsets.UTF_8));

        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
        assertArrayEquals(testMessage.getBytes(StandardCharsets.UTF_8),
                sink.getExchanges().get(0).getMessage().getBody(byte[].class));
        assertNull(relayed.getExchanges().get(0).getProperty(Exchange.CHARSET_NAME));
    }

    @Test
    public void testRelayUsesCharsetNameOfProducer() throws Exception {
        String testMessage = message("", "Sørensen^Anne");
        relayed.expectedMessageCount(1);
        sink.expectedMessageCount(1);

        // the relay consumer decodes in its default charset ISO-8859-1, the producer encodes in its charsetName UTF-8
        template.sendBody(producerUri(relayPort.getPort(), null), testMessage.getBytes(StandardCharsets.ISO_8859_1));

        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
        assertArrayEquals(testMessage.getBytes(StandardCharsets.UTF_8),
                sink.getExchanges().get(0).getMessage().getBody(byte[].class));
        assertNull(relayed.getExchanges().get(0).getProperty(Exchange.CHARSET_NAME));
    }

    @Test
    public void testConsumerEncodesAcknowledgementInCharsetOfMsh18() throws Exception {
        Exchange exchange = template.send(producerUri(acknowledgementPort.getPort(), null),
                e -> e.getMessage().setBody(message("", "Sørensen^Anne")));

        assertNull(exchange.getException());
        assertArrayEquals(ACKNOWLEDGEMENT.getBytes(StandardCharsets.UTF_8),
                exchange.getMessage().getHeader(MllpConstants.MLLP_ACKNOWLEDGEMENT, byte[].class));
        assertEquals(ACKNOWLEDGEMENT, exchange.getMessage().getHeader(MllpConstants.MLLP_ACKNOWLEDGEMENT_STRING));
    }
}

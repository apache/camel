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
package org.apache.camel.component.smpp;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.support.DefaultExchange;
import org.jsmpp.bean.DataSm;
import org.jsmpp.bean.DeliverSm;
import org.jsmpp.extra.ProcessRequestException;
import org.jsmpp.session.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * An exchange a consumer received a message for has to be built through {@link org.apache.camel.Consumer} rather than
 * on the endpoint, so that the configured {@code ExchangeFactory} sees it, and it has to be released afterwards.
 */
class MessageReceiverListenerImplTest {

    private SmppConsumer consumer;
    private SmppEndpoint endpoint;
    private CamelContext camelContext;
    private Exchange exchange;

    @BeforeEach
    void setUp() {
        camelContext = new DefaultCamelContext();
        exchange = new DefaultExchange(camelContext);
        consumer = mock(SmppConsumer.class);
        endpoint = mock(SmppEndpoint.class);

        when(consumer.createExchange(false)).thenReturn(exchange);
        when(endpoint.getCamelContext()).thenReturn(camelContext);
        when(endpoint.getBinding()).thenReturn(new SmppBinding());
        when(endpoint.getExchangePattern()).thenReturn(ExchangePattern.InOnly);
    }

    private MessageReceiverListenerImpl listener(Processor processor) {
        return new MessageReceiverListenerImpl(consumer, endpoint, processor, mock(ExceptionHandler.class));
    }

    private static DeliverSm deliverSm() {
        DeliverSm deliverSm = new DeliverSm();
        deliverSm.setShortMessage("Hello SMPP world!".getBytes());
        return deliverSm;
    }

    @Test
    void deliverSmTakesItsExchangeFromTheConsumerAndReleasesIt() throws Exception {
        listener(received -> assertEquals("Hello SMPP world!", received.getIn().getBody(String.class)))
                .onAcceptDeliverSm(deliverSm());

        verify(consumer).createExchange(false);
        verify(consumer).releaseExchange(exchange, false);
    }

    /**
     * The failure path throws a {@link ProcessRequestException} so the SMSC gets a NACK, and must still release.
     */
    @Test
    void deliverSmReleasesItsExchangeWhenTheRouteFailed() {
        MessageReceiverListenerImpl listener
                = listener(received -> received.setException(new IllegalStateException("the route blew up")));

        ProcessRequestException thrown
                = assertThrows(ProcessRequestException.class, () -> listener.onAcceptDeliverSm(deliverSm()));
        assertEquals("the route blew up", thrown.getMessage());

        verify(consumer).releaseExchange(exchange, false);
    }

    @Test
    void dataSmTakesItsExchangeFromTheConsumerAndReleasesIt() throws Exception {
        DataSm dataSm = new DataSm();

        listener(received -> {
        }).onAcceptDataSm(dataSm, mock(Session.class));

        verify(consumer).createExchange(false);
        verify(consumer).releaseExchange(exchange, false);
    }

    /**
     * The exchange is taken from the factory before the message is decoded, and the caller's own {@code finally} cannot
     * release it because its variable is still unassigned when creation throws. A malformed delivery receipt would
     * otherwise cost a pooled exchange on every occurrence.
     */
    @Test
    void deliverSmReleasesItsExchangeWhenTheMessageCannotBeDecoded() throws Exception {
        SmppBinding failing = mock(SmppBinding.class);
        when(failing.createSmppMessage(any(CamelContext.class), any(DeliverSm.class)))
                .thenThrow(new IOException("malformed delivery receipt"));
        when(endpoint.getBinding()).thenReturn(failing);

        ExceptionHandler handler = mock(ExceptionHandler.class);
        new MessageReceiverListenerImpl(consumer, endpoint, received -> {
        }, handler).onAcceptDeliverSm(deliverSm());

        verify(consumer).releaseExchange(exchange, false);
        verify(handler).handleException(anyString(), any(Exception.class));
    }

    /**
     * In transceiver mode - {@code SmppProducer} with {@code messageReceiverRouteId} - the consumer belongs to the
     * receiver route rather than to the SMPP endpoint, so a received message now reports that route's endpoint as
     * {@code fromEndpoint}, the way alert notifications already did. The SMPP endpoint's exchange pattern still has to
     * win over the receiver endpoint's, which is why the pattern is set explicitly.
     */
    @Test
    void transceiverModeUsesTheReceiverRouteConsumerAndKeepsTheSmppPattern() throws Exception {
        AtomicReference<Exchange> received = new AtomicReference<>();
        DefaultCamelContext trxContext = new DefaultCamelContext();
        trxContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:messageReceiver").routeId("messageReceiver").process(received::set);
            }
        });

        when(endpoint.getCamelContext()).thenReturn(trxContext);
        when(endpoint.getExchangePattern()).thenReturn(ExchangePattern.InOut);

        // constructed before the context starts, so its StartupListener resolves the receiver route's consumer
        MessageReceiverListenerImpl listener = new MessageReceiverListenerImpl(endpoint, "messageReceiver");
        trxContext.start();
        try {
            listener.onAcceptDeliverSm(deliverSm());

            assertNotNull(received.get(), "the receiver route should have been given the message");
            assertEquals("direct://messageReceiver", received.get().getFromEndpoint().getEndpointUri());
            assertEquals(ExchangePattern.InOut, received.get().getPattern(),
                    "the SMPP endpoint's pattern must win over the receiver endpoint's");
        } finally {
            trxContext.stop();
        }
    }
}

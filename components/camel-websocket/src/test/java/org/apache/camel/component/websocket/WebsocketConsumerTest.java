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
package org.apache.camel.component.websocket;

import java.net.InetSocketAddress;

import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.ExtendedExchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.spi.ExchangeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@MockitoSettings
public class WebsocketConsumerTest {

    private static final String CONNECTION_KEY = "random-connection-key";
    private static final String MESSAGE = "message";
    private static final InetSocketAddress ADDRESS = InetSocketAddress.createUnresolved("127.0.0.1", 12345);

    @Mock
    private WebsocketEndpoint endpoint;
    @Mock
    private ExceptionHandler exceptionHandler;
    @Mock
    private Processor processor;
    @Mock
    private ExtendedExchange exchange;
    @Mock
    private Message outMessage;
    @Mock
    private ExtendedCamelContext context;
    @Mock
    private ExchangeFactory exchangeFactory;

    private Exception exception = new Exception("BAD NEWS EVERYONE!");
    private WebsocketConsumer websocketConsumer;

    @BeforeEach
    public void setUp() throws Exception {
        when(endpoint.getCamelContext()).thenReturn(context);
        when(context.adapt(ExtendedCamelContext.class)).thenReturn(context);
        when(context.getExchangeFactory()).thenReturn(exchangeFactory);
        when(exchangeFactory.newExchangeFactory(any())).thenReturn(exchangeFactory);
        when(exchangeFactory.create(endpoint, true)).thenReturn(exchange);
        when(exchange.adapt(ExtendedExchange.class)).thenReturn(exchange);

        websocketConsumer = new WebsocketConsumer(endpoint, processor);
        websocketConsumer.setExceptionHandler(exceptionHandler);
    }

    @Test
    public void testSendExchange() throws Exception {
        when(exchange.getIn()).thenReturn(outMessage);

        websocketConsumer.sendMessage(CONNECTION_KEY, MESSAGE, ADDRESS, null, null);

        InOrder inOrder = inOrder(endpoint, exceptionHandler, processor, exchange, outMessage);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(outMessage, times(1)).setHeader(WebsocketConstants.CONNECTION_KEY, CONNECTION_KEY);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(outMessage, times(1)).setBody(MESSAGE);
        inOrder.verify(processor, times(1)).process(exchange);
        inOrder.verify(exchange, times(1)).getException();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testSendExchangeWithException() throws Exception {
        when(exchange.getIn()).thenReturn(outMessage);
        doThrow(exception).when(processor).process(exchange);
        when(exchange.getException()).thenReturn(exception);

        websocketConsumer.sendMessage(CONNECTION_KEY, MESSAGE, ADDRESS, null, null);

        InOrder inOrder = inOrder(endpoint, exceptionHandler, processor, exchange, outMessage);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(outMessage, times(1)).setHeader(WebsocketConstants.CONNECTION_KEY, CONNECTION_KEY);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(outMessage, times(1)).setBody(MESSAGE);
        inOrder.verify(processor, times(1)).process(exchange);
        inOrder.verify(exchange, times(2)).getException();
        inOrder.verify(exceptionHandler, times(1)).handleException(any(), eq(exchange), eq(exception));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testSendExchangeWithExchangeExceptionIsNull() throws Exception {
        when(exchange.getIn()).thenReturn(outMessage);
        doThrow(exception).when(processor).process(exchange);
        when(exchange.getException()).thenReturn(null);

        websocketConsumer.sendMessage(CONNECTION_KEY, MESSAGE, ADDRESS, null, null);

        InOrder inOrder = inOrder(endpoint, exceptionHandler, processor, exchange, outMessage);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(outMessage, times(1)).setHeader(WebsocketConstants.CONNECTION_KEY, CONNECTION_KEY);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(outMessage, times(1)).setBody(MESSAGE);
        inOrder.verify(processor, times(1)).process(exchange);
        inOrder.verify(exchange, times(1)).getException();
        inOrder.verifyNoMoreInteractions();
    }

}

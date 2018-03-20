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
package org.apache.camel.component.websocket;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.spi.ExceptionHandler;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class WebsocketConsumerTest {

    private static final String CONNECTION_KEY = "random-connection-key";
    private static final String MESSAGE = "message";

    @Mock
    private WebsocketEndpoint endpoint;
    @Mock
    private ExceptionHandler exceptionHandler;
    @Mock
    private Processor processor;
    @Mock
    private Exchange exchange;
    @Mock
    private Message outMessage;

    private Exception exception = new Exception("BAD NEWS EVERYONE!");
    private WebsocketConsumer websocketConsumer;

    @Before
    public void setUp() throws Exception {
        websocketConsumer = new WebsocketConsumer(endpoint, processor);
        websocketConsumer.setExceptionHandler(exceptionHandler);
    }

    @Test
    public void testSendExchange() throws Exception {
        when(endpoint.createExchange()).thenReturn(exchange);
        when(exchange.getIn()).thenReturn(outMessage);

        websocketConsumer.sendMessage(CONNECTION_KEY, MESSAGE);

        InOrder inOrder = inOrder(endpoint, exceptionHandler, processor, exchange, outMessage);
        inOrder.verify(endpoint, times(1)).createExchange();
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
        when(endpoint.createExchange()).thenReturn(exchange);
        when(exchange.getIn()).thenReturn(outMessage);
        doThrow(exception).when(processor).process(exchange);
        when(exchange.getException()).thenReturn(exception);

        websocketConsumer.sendMessage(CONNECTION_KEY, MESSAGE);

        InOrder inOrder = inOrder(endpoint, exceptionHandler, processor, exchange, outMessage);
        inOrder.verify(endpoint, times(1)).createExchange();
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
        when(endpoint.createExchange()).thenReturn(exchange);
        when(exchange.getIn()).thenReturn(outMessage);
        doThrow(exception).when(processor).process(exchange);
        when(exchange.getException()).thenReturn(null);

        websocketConsumer.sendMessage(CONNECTION_KEY, MESSAGE);

        InOrder inOrder = inOrder(endpoint, exceptionHandler, processor, exchange, outMessage);
        inOrder.verify(endpoint, times(1)).createExchange();
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(outMessage, times(1)).setHeader(WebsocketConstants.CONNECTION_KEY, CONNECTION_KEY);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(outMessage, times(1)).setBody(MESSAGE);
        inOrder.verify(processor, times(1)).process(exchange);
        inOrder.verify(exchange, times(1)).getException();
        inOrder.verifyNoMoreInteractions();
    }

}

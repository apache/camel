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
package org.apache.camel.component.plc4x;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.PlcDriver;
import org.apache.plc4x.java.api.authentication.PlcAuthentication;
import org.apache.plc4x.java.api.exceptions.PlcConnectionException;
import org.apache.plc4x.java.api.messages.PlcReadRequest;
import org.apache.plc4x.java.api.messages.PlcSubscriptionRequest;
import org.apache.plc4x.java.api.messages.PlcSubscriptionResponse;
import org.apache.plc4x.java.api.messages.PlcUnsubscriptionRequest;
import org.apache.plc4x.java.api.messages.PlcWriteRequest;
import org.apache.plc4x.java.spi.messages.DefaultPlcSubscriptionResponse;
import org.apache.plc4x.java.spi.messages.PlcSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.mockito.Mockito.*;

public class MockDriver implements PlcDriver {

    public static final Logger LOGGER = LoggerFactory.getLogger(MockDriver.class);

    ExecutorService executorService = Executors.newFixedThreadPool(10);

    @Override
    public String getProtocolCode() {
        return "mock";
    }

    @Override
    public String getProtocolName() {
        return "Mock Protocol Implementation";
    }

    @Override
    public PlcConnection getConnection(String url) throws PlcConnectionException {
        // Mock a connection.
        PlcConnection plcConnectionMock = mock(PlcConnection.class, RETURNS_DEEP_STUBS);
        when(plcConnectionMock.getMetadata().isReadSupported()).thenReturn(true);
        when(plcConnectionMock.getMetadata().isWriteSupported()).thenReturn(true);
        when(plcConnectionMock.readRequestBuilder()).thenReturn(mock(PlcReadRequest.Builder.class, RETURNS_DEEP_STUBS));
        when(plcConnectionMock.writeRequestBuilder()).thenReturn(mock(PlcWriteRequest.Builder.class, RETURNS_DEEP_STUBS));
        when(plcConnectionMock.subscriptionRequestBuilder())
                .thenReturn(mock(PlcSubscriptionRequest.Builder.class, RETURNS_DEEP_STUBS));
        when(plcConnectionMock.unsubscriptionRequestBuilder())
                .thenReturn(mock(PlcUnsubscriptionRequest.Builder.class, RETURNS_DEEP_STUBS));

        // Mock a typical subscriber.
        PlcSubscriber plcSubscriber = mock(PlcSubscriber.class, RETURNS_DEEP_STUBS);
        when(plcSubscriber.subscribe(any(PlcSubscriptionRequest.class))).thenAnswer(invocation -> {
            LOGGER.info("Received {}", invocation);
            // TODO: Translate this so it actually does something ...
            /*PlcSubscriptionRequest subscriptionRequest = invocation.getArgument(0);
            List<PlcSubscriptionResponse> responseItems =
                subscriptionRequest.getFieldNames().stream().map(
                    fieldName -> subscriptionRequest.getField(fieldName)).map(field -> {
                    Consumer consumer = subscriptionRequestItem.getConsumer();
                    executorService.submit(() -> {
                        while (!Thread.currentThread().isInterrupted()) {
                            consumer.accept(new SubscriptionEventItem<>(null, Calendar.getInstance(), Collections.singletonList("HelloWorld")));
                            try {
                                TimeUnit.MILLISECONDS.sleep(100);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException(e);
                            }
                        }
                    });
                    return new SubscriptionResponseItem<>(subscriptionRequestItem,
                        mock(PlcSubscriptionHandle.class, RETURNS_DEEP_STUBS), PlcResponseCode.OK);
                }).collect(Collectors.toList());
            PlcSubscriptionResponse response = new PlcSubscriptionResponse(subscriptionRequest, responseItems);*/
            PlcSubscriptionResponse response
                    = new DefaultPlcSubscriptionResponse(mock(PlcSubscriptionRequest.class), new HashMap<>());
            return CompletableFuture.completedFuture(response);
        });
        return plcConnectionMock;
    }

    @Override
    public PlcConnection getConnection(String url, PlcAuthentication authentication) throws PlcConnectionException {
        return getConnection(null);
    }

}

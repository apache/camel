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

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.PlcDriver;
import org.apache.plc4x.java.api.authentication.PlcAuthentication;
import org.apache.plc4x.java.api.exceptions.PlcConnectionException;
import org.apache.plc4x.java.api.messages.PlcReadRequest;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.apache.plc4x.java.api.messages.PlcSubscriptionRequest;
import org.apache.plc4x.java.api.messages.PlcSubscriptionResponse;
import org.apache.plc4x.java.api.messages.PlcUnsubscriptionRequest;
import org.apache.plc4x.java.api.messages.PlcWriteRequest;
import org.apache.plc4x.java.api.model.PlcTag;
import org.apache.plc4x.java.api.types.PlcResponseCode;
import org.apache.plc4x.java.api.types.PlcValueType;
import org.apache.plc4x.java.spi.messages.DefaultPlcSubscriptionResponse;
import org.apache.plc4x.java.spi.messages.PlcSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.mockito.Mockito.*;

/**
 * Test driver for the {@code mock} protocol. A read returns the values a test programmed with
 * {@link #programRead(String, Object...)} when every tag in the request is programmed, and Mockito deep stubs
 * otherwise. The programming is static because every driver manager loads its own driver instance; tests call
 * {@link #reset()}.
 */
public class MockDriver implements PlcDriver {

    public static final Logger LOGGER = LoggerFactory.getLogger(MockDriver.class);

    private static final Map<String, ProgrammedTag> PROGRAMMED_TAGS = new ConcurrentHashMap<>();
    private static final AtomicInteger REFUSED_CONNECTIONS = new AtomicInteger();
    private static volatile boolean refuseConnections;

    ExecutorService executorService = Executors.newFixedThreadPool(10);

    /**
     * Programs the values that successive reads of a tag address return; the last value repeats once they are used up.
     */
    public static void programRead(String tagAddress, Object... values) {
        if (values.length == 0) {
            throw new IllegalArgumentException("At least one value is required for " + tagAddress);
        }
        PROGRAMMED_TAGS.put(tagAddress, new ProgrammedTag(Arrays.asList(values)));
    }

    /**
     * Makes every following connection attempt fail.
     */
    public static void refuseConnections() {
        refuseConnections = true;
    }

    /**
     * @return the number of connection attempts refused since the last {@link #reset()}
     */
    public static int refusedConnections() {
        return REFUSED_CONNECTIONS.get();
    }

    /**
     * Forgets all programmed tag values and accepts connections again.
     */
    public static void reset() {
        PROGRAMMED_TAGS.clear();
        refuseConnections = false;
        REFUSED_CONNECTIONS.set(0);
    }

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
        if (refuseConnections) {
            REFUSED_CONNECTIONS.incrementAndGet();
            throw new PlcConnectionException("Connection refused by the mock driver");
        }
        // Mock a connection.
        PlcConnection plcConnectionMock = mock(PlcConnection.class, RETURNS_DEEP_STUBS);
        when(plcConnectionMock.getMetadata().isReadSupported()).thenReturn(true);
        when(plcConnectionMock.getMetadata().isWriteSupported()).thenReturn(true);
        when(plcConnectionMock.readRequestBuilder()).thenAnswer(invocation -> readRequestBuilder());
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

    /**
     * Every tag is a boolean tag. Only the scraper prepares tags, to validate its trigger.
     */
    @Override
    public PlcTag prepareTag(String tagAddress) {
        return mock(PlcTag.class, invocation -> switch (invocation.getMethod().getName()) {
            case "getAddressString" -> tagAddress;
            case "getPlcValueType" -> PlcValueType.BOOL;
            default -> RETURNS_DEFAULTS.answer(invocation);
        });
    }

    private static PlcReadRequest.Builder readRequestBuilder() {
        PlcReadRequest.Builder unprogrammed = mock(PlcReadRequest.Builder.class, RETURNS_DEEP_STUBS);
        Map<String, String> tags = new LinkedHashMap<>();
        return mock(PlcReadRequest.Builder.class, invocation -> switch (invocation.getMethod().getName()) {
            case "addTagAddress" -> {
                tags.put(invocation.getArgument(0), invocation.getArgument(1));
                unprogrammed.addTagAddress(invocation.getArgument(0), invocation.getArgument(1));
                yield invocation.getMock();
            }
            case "build" -> !tags.isEmpty() && PROGRAMMED_TAGS.keySet().containsAll(tags.values())
                    ? programmedRequest(tags) : unprogrammed.build();
            default -> RETURNS_DEFAULTS.answer(invocation);
        });
    }

    private static PlcReadRequest programmedRequest(Map<String, String> tags) {
        return mock(PlcReadRequest.class, invocation -> switch (invocation.getMethod().getName()) {
            case "execute" -> CompletableFuture.completedFuture(read(tags));
            case "getTagNames" -> new LinkedHashSet<>(tags.keySet());
            default -> RETURNS_DEFAULTS.answer(invocation);
        });
    }

    private static PlcReadResponse read(Map<String, String> tags) {
        Map<String, Object> values = new LinkedHashMap<>();
        tags.forEach((name, address) -> values.put(name, PROGRAMMED_TAGS.get(address).next()));
        return mock(PlcReadResponse.class, invocation -> switch (invocation.getMethod().getName()) {
            case "getTagNames" -> List.copyOf(values.keySet());
            case "getObject" -> values.get(invocation.<String> getArgument(0));
            case "getResponseCode" -> PlcResponseCode.OK;
            default -> RETURNS_DEFAULTS.answer(invocation);
        });
    }

    private static final class ProgrammedTag {

        private final List<Object> values;
        private int next;

        ProgrammedTag(List<Object> values) {
            this.values = values;
        }

        synchronized Object next() {
            Object value = values.get(next);
            if (next < values.size() - 1) {
                next++;
            }
            return value;
        }
    }

}

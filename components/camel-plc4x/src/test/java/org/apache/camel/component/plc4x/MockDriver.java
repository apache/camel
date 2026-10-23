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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.PlcDriver;
import org.apache.plc4x.java.api.authentication.PlcAuthentication;
import org.apache.plc4x.java.api.exceptions.PlcConnectionException;
import org.apache.plc4x.java.api.exceptions.PlcException;
import org.apache.plc4x.java.api.exceptions.PlcRuntimeException;
import org.apache.plc4x.java.api.messages.PlcReadRequest;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.apache.plc4x.java.api.messages.PlcSubscriptionRequest;
import org.apache.plc4x.java.api.messages.PlcUnsubscriptionRequest;
import org.apache.plc4x.java.api.messages.PlcWriteRequest;
import org.apache.plc4x.java.api.types.PlcResponseCode;

import static org.mockito.Mockito.*;

/**
 * Test driver for the {@code mock} protocol. A read returns the values a test programmed with
 * {@link #programRead(String, Object...)} when every tag in the request is programmed, and Mockito deep stubs
 * otherwise. A programmed {@link #failedRead()} fails the whole read, a programmed
 * {@link #responseCode(PlcResponseCode)} answers that tag with an error code. The programming is static because every
 * driver manager loads its own driver instance; tests call {@link #reset()}.
 */
public class MockDriver implements PlcDriver {

    private static final Map<String, ProgrammedTag> PROGRAMMED_TAGS = new ConcurrentHashMap<>();
    private static volatile CompletableFuture<Void> heldReads = CompletableFuture.completedFuture(null);
    private static volatile CompletableFuture<Void> heldRead = new CompletableFuture<>();
    private static final AtomicInteger REFUSED_CONNECTIONS = new AtomicInteger();
    private static final List<String> CONNECTION_STRINGS = new CopyOnWriteArrayList<>();
    private static final Object FAILED_READ = new Object();
    private static volatile boolean refuseConnections;

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
     * A programmed value that makes the whole read in which it is due fail.
     */
    public static Object failedRead() {
        return FAILED_READ;
    }

    /**
     * A programmed value that makes the read in which it is due answer the tag with the given response code and no
     * value.
     */
    public static Object responseCode(PlcResponseCode responseCode) {
        return new FailedTag(responseCode);
    }

    /**
     * Holds the following reads until {@link #releaseReads()}; a held read ignores interrupts, as a read that a PLC has
     * already answered does.
     */
    public static void holdReads() {
        heldRead = new CompletableFuture<>();
        heldReads = new CompletableFuture<>();
    }

    /**
     * Waits until a read is held by {@link #holdReads()}.
     */
    public static void awaitHeldRead() throws Exception {
        heldRead.get(10, TimeUnit.SECONDS);
    }

    /**
     * Lets the reads held by {@link #holdReads()} complete.
     */
    public static void releaseReads() {
        heldReads.complete(null);
    }

    /**
     * Makes every following connection attempt fail.
     */
    public static void refuseConnections() {
        refuseConnections = true;
    }

    /**
     * Makes the following connection attempts succeed again.
     */
    public static void acceptConnections() {
        refuseConnections = false;
    }

    /**
     * @return the number of connection attempts refused since the last {@link #reset()}
     */
    public static int refusedConnections() {
        return REFUSED_CONNECTIONS.get();
    }

    /**
     * @return the connection strings the driver was asked to connect with since the last {@link #reset()}
     */
    public static List<String> connectionStrings() {
        return CONNECTION_STRINGS;
    }

    /**
     * Forgets all programmed tag values and accepts connections again.
     */
    public static void reset() {
        releaseReads();
        PROGRAMMED_TAGS.clear();
        refuseConnections = false;
        REFUSED_CONNECTIONS.set(0);
        CONNECTION_STRINGS.clear();
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
        CONNECTION_STRINGS.add(url);
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
        return plcConnectionMock;
    }

    @Override
    public PlcConnection getConnection(String url, PlcAuthentication authentication) throws PlcConnectionException {
        return getConnection(url);
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
            case "execute" -> read(tags);
            case "getTagNames" -> new LinkedHashSet<>(tags.keySet());
            default -> RETURNS_DEFAULTS.answer(invocation);
        });
    }

    private static CompletableFuture<PlcReadResponse> read(Map<String, String> tags) {
        heldRead.complete(null);
        // join() does not return early on an interrupt
        heldReads.join();
        Map<String, Object> values = new LinkedHashMap<>();
        tags.forEach((name, address) -> values.put(name, PROGRAMMED_TAGS.get(address).next()));
        if (values.containsValue(FAILED_READ)) {
            return CompletableFuture.failedFuture(new PlcException("Read failed in the mock driver"));
        }
        return CompletableFuture.completedFuture(mock(PlcReadResponse.class,
                invocation -> switch (invocation.getMethod().getName()) {
                    case "getTagNames" -> List.copyOf(values.keySet());
                    case "getObject" -> value(values.get(invocation.<String> getArgument(0)));
                    case "getResponseCode" -> values.get(invocation.<String> getArgument(0)) instanceof FailedTag failed
                            ? failed.responseCode() : PlcResponseCode.OK;
                    default -> RETURNS_DEFAULTS.answer(invocation);
                }));
    }

    private static Object value(Object programmed) {
        if (programmed instanceof FailedTag failed) {
            throw new PlcRuntimeException("Tag read failed with response code " + failed.responseCode());
        }
        return programmed;
    }

    private record FailedTag(PlcResponseCode responseCode) {
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

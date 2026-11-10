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
package org.apache.camel.component.cxf.jaxws;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.component.cxf.common.CxfBinding;
import org.apache.camel.http.base.cookie.CookieHandler;
import org.apache.cxf.message.Message;
import org.apache.cxf.service.model.BindingOperationInfo;
import org.apache.cxf.service.model.OperationInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CxfClientCallback} verifying that {@code camelAsyncCallback.done(false)} is always called
 * exactly once, and that exception handling preserves the root cause.
 *
 * CAMEL-26696
 */
class CxfClientCallbackTest {

    private Exchange camelExchange;
    private org.apache.cxf.message.Exchange cxfExchange;
    private BindingOperationInfo boi;
    private CxfEndpoint endpoint;
    private CxfBinding cxfBinding;

    @BeforeEach
    void setUp() {
        camelExchange = mock(Exchange.class);
        cxfExchange = mock(org.apache.cxf.message.Exchange.class);
        boi = mock(BindingOperationInfo.class);
        endpoint = mock(CxfEndpoint.class);
        cxfBinding = mock(CxfBinding.class);

        OperationInfo operationInfo = mock(OperationInfo.class);
        when(boi.getOperationInfo()).thenReturn(operationInfo);
        when(operationInfo.isOneWay()).thenReturn(false);

        when(endpoint.getCxfBinding()).thenReturn(cxfBinding);
        when(endpoint.getCookieHandler()).thenReturn(null); // no cookie handler
    }

    // --- handleResponse tests ---

    @Test
    void handleResponse_normalPath_callsDoneExactlyOnce() {
        AtomicInteger doneCount = new AtomicInteger();
        AsyncCallback callback = sync -> doneCount.incrementAndGet();
        Map<String, Object> ctx = new HashMap<>();

        CxfClientCallback cb = new CxfClientCallback(callback, camelExchange, cxfExchange, boi, endpoint);
        cb.handleResponse(ctx, new Object[0]);

        assertThat(doneCount.get()).as("done(false) must be called exactly once").isEqualTo(1);
    }

    @Test
    void handleResponse_bindingThrows_callsDoneAndSetsException() {
        RuntimeException bindingError = new RuntimeException("binding failure");
        doThrow(bindingError).when(cxfBinding).populateExchangeFromCxfResponse(any(), any(), any());

        AtomicInteger doneCount = new AtomicInteger();
        AsyncCallback callback = sync -> doneCount.incrementAndGet();

        // Capture the exception set on the exchange
        RuntimeException[] captured = new RuntimeException[1];
        camelExchange = mock(Exchange.class);
        doAnswer(inv -> {
            captured[0] = (RuntimeException) inv.getArgument(0);
            return null;
        }).when(camelExchange).setException(any(Throwable.class));

        Map<String, Object> ctx = new HashMap<>();
        CxfClientCallback cb = new CxfClientCallback(callback, camelExchange, cxfExchange, boi, endpoint);
        cb.handleResponse(ctx, new Object[0]);

        assertThat(doneCount.get()).as("done(false) must be called even when binding throws").isEqualTo(1);
        assertThat(captured[0]).isSameAs(bindingError);
    }

    @Test
    void handleResponse_bindingThrowsError_callsDoneAndSetsException() {
        OutOfMemoryError bindingError = new OutOfMemoryError("simulated OOM in binding");
        doThrow(bindingError).when(cxfBinding).populateExchangeFromCxfResponse(any(), any(), any());

        AtomicInteger doneCount = new AtomicInteger();
        AsyncCallback callback = sync -> doneCount.incrementAndGet();

        Throwable[] captured = new Throwable[1];
        camelExchange = mock(Exchange.class);
        doAnswer(inv -> {
            captured[0] = (Throwable) inv.getArgument(0);
            return null;
        }).when(camelExchange).setException(any(Throwable.class));

        Map<String, Object> ctx = new HashMap<>();
        CxfClientCallback cb = new CxfClientCallback(callback, camelExchange, cxfExchange, boi, endpoint);
        cb.handleResponse(ctx, new Object[0]);

        assertThat(doneCount.get()).as("done(false) must be called even when binding throws an Error").isEqualTo(1);
        assertThat(captured[0]).isSameAs(bindingError);
    }

    // --- handleException tests ---

    @Test
    void handleException_normalPath_callsDoneExactlyOnce() {
        AtomicInteger doneCount = new AtomicInteger();
        AsyncCallback callback = sync -> doneCount.incrementAndGet();

        RuntimeException transportEx = new RuntimeException("connection refused");
        camelExchange = mock(Exchange.class);

        Map<String, Object> ctx = new HashMap<>();
        CxfClientCallback cb = new CxfClientCallback(callback, camelExchange, cxfExchange, boi, endpoint);
        cb.handleException(ctx, transportEx);

        assertThat(doneCount.get()).as("done(false) must be called exactly once").isEqualTo(1);
    }

    @Test
    void handleException_bindingThrows_preservesRootCauseAndCallsDone() {
        RuntimeException transportEx = new RuntimeException("connection refused");
        RuntimeException bindingEx = new RuntimeException("binding failure");
        doThrow(bindingEx).when(cxfBinding).populateExchangeFromCxfResponse(any(), any(), any());

        AtomicInteger doneCount = new AtomicInteger();
        AsyncCallback callback = sync -> doneCount.incrementAndGet();

        // Simulate exchange already has the transport exception set
        when(camelExchange.getException()).thenReturn(transportEx);

        Map<String, Object> ctx = new HashMap<>();
        CxfClientCallback cb = new CxfClientCallback(callback, camelExchange, cxfExchange, boi, endpoint);
        cb.handleException(ctx, transportEx);

        assertThat(doneCount.get()).as("done(false) must be called even when binding throws").isEqualTo(1);
        // binding error should be suppressed on the root cause, not replace it
        assertThat(transportEx.getSuppressed()).containsExactly(bindingEx);
    }

    @Test
    void handleException_bindingThrows_noRootCause_setsBindingException() {
        RuntimeException bindingEx = new RuntimeException("binding failure");
        doThrow(bindingEx).when(cxfBinding).populateExchangeFromCxfResponse(any(), any(), any());

        AtomicInteger doneCount = new AtomicInteger();
        AsyncCallback callback = sync -> doneCount.incrementAndGet();

        // No prior exception on the exchange
        when(camelExchange.getException()).thenReturn(null);
        Throwable[] captured = new Throwable[1];
        doAnswer(inv -> {
            captured[0] = (Throwable) inv.getArgument(0);
            return null;
        }).when(camelExchange).setException(any(Throwable.class));

        RuntimeException transportEx = new RuntimeException("connection refused");
        Map<String, Object> ctx = new HashMap<>();
        CxfClientCallback cb = new CxfClientCallback(callback, camelExchange, cxfExchange, boi, endpoint);
        cb.handleException(ctx, transportEx);

        assertThat(doneCount.get()).isEqualTo(1);
        // when there's no root cause, the binding exception should be set
        assertThat(captured[0]).isSameAs(bindingEx);
    }

    @Test
    void handleException_bindingThrowsError_callsDoneAndPreservesRootCause() {
        RuntimeException transportEx = new RuntimeException("connection refused");
        OutOfMemoryError bindingError = new OutOfMemoryError("simulated OOM in binding");
        doThrow(bindingError).when(cxfBinding).populateExchangeFromCxfResponse(any(), any(), any());

        AtomicInteger doneCount = new AtomicInteger();
        AsyncCallback callback = sync -> doneCount.incrementAndGet();

        when(camelExchange.getException()).thenReturn(transportEx);

        Map<String, Object> ctx = new HashMap<>();
        CxfClientCallback cb = new CxfClientCallback(callback, camelExchange, cxfExchange, boi, endpoint);
        cb.handleException(ctx, transportEx);

        assertThat(doneCount.get()).as("done(false) must be called even when binding throws an Error").isEqualTo(1);
        assertThat(transportEx.getSuppressed()).containsExactly(bindingError);
    }

    // --- cookie handler tests ---

    @Test
    void handleResponse_cookieStoreThrows_callsDoneAndSetsException() throws Exception {
        // CookieHandler.storeCookies declares IOException but could throw RuntimeException too;
        // any Throwable from the cookie store must be recorded and must not prevent done() from firing.
        RuntimeException cookieError = new RuntimeException("cookie store failure");
        CookieHandler cookieHandler = mock(CookieHandler.class);
        doThrow(cookieError).when(cookieHandler).storeCookies(any(), any(URI.class), any());

        Message inMessage = mock(Message.class);
        when(inMessage.get(any())).thenReturn(Collections.emptyMap());
        when(cxfExchange.getInMessage()).thenReturn(inMessage);
        when(endpoint.getCookieHandler()).thenReturn(cookieHandler);
        when(endpoint.getRequestUri(any())).thenReturn(URI.create("http://wrong-server/"));

        AtomicInteger doneCount = new AtomicInteger();
        AsyncCallback callback = sync -> doneCount.incrementAndGet();

        Throwable[] captured = new Throwable[1];
        camelExchange = mock(Exchange.class);
        doAnswer(inv -> {
            captured[0] = (Throwable) inv.getArgument(0);
            return null;
        }).when(camelExchange).setException(any(Throwable.class));

        when(endpoint.getCxfBinding()).thenReturn(cxfBinding);

        Map<String, Object> ctx = new HashMap<>();
        CxfClientCallback cb = new CxfClientCallback(callback, camelExchange, cxfExchange, boi, endpoint);
        cb.handleResponse(ctx, new Object[0]);

        assertThat(doneCount.get()).as("done(false) must be called even when cookie store throws").isEqualTo(1);
        assertThat(captured[0]).isSameAs(cookieError);
    }

    @Test
    void handleException_cookieStoreThrows_suppressesOnRootCauseAndCallsDone() throws Exception {
        // Cookie store failure in the exception path: must be suppressed on the root transport exception.
        RuntimeException transportEx = new RuntimeException("connection refused");
        RuntimeException cookieError = new RuntimeException("cookie store failure");
        CookieHandler cookieHandler = mock(CookieHandler.class);
        doThrow(cookieError).when(cookieHandler).storeCookies(any(), any(URI.class), any());

        Message inMessage = mock(Message.class);
        when(inMessage.get(any())).thenReturn(Collections.emptyMap());
        when(cxfExchange.getInMessage()).thenReturn(inMessage);
        when(endpoint.getCookieHandler()).thenReturn(cookieHandler);
        when(endpoint.getRequestUri(any())).thenReturn(URI.create("http://wrong-server/"));

        AtomicInteger doneCount = new AtomicInteger();
        AsyncCallback callback = sync -> doneCount.incrementAndGet();

        when(camelExchange.getException()).thenReturn(transportEx);
        when(endpoint.getCxfBinding()).thenReturn(cxfBinding);

        Map<String, Object> ctx = new HashMap<>();
        CxfClientCallback cb = new CxfClientCallback(callback, camelExchange, cxfExchange, boi, endpoint);
        cb.handleException(ctx, transportEx);

        assertThat(doneCount.get()).as("done(false) must be called even when cookie store throws").isEqualTo(1);
        // cookie error must be suppressed on root transport exception, not replace it
        assertThat(transportEx.getSuppressed()).containsExactly(cookieError);
    }

    @Test
    void handleResponse_cookieAndBindingThrow_bindingErrorSuppressedOnCookieError() throws Exception {
        // Both cookie store and binding throw in the success path; the binding error must be suppressed
        // on the cookie error (not silently lost), and done(false) must still fire.
        RuntimeException cookieError = new RuntimeException("cookie store failure");
        RuntimeException bindingError = new RuntimeException("binding failure");
        CookieHandler cookieHandler = mock(CookieHandler.class);
        doThrow(cookieError).when(cookieHandler).storeCookies(any(), any(URI.class), any());
        doThrow(bindingError).when(cxfBinding).populateExchangeFromCxfResponse(any(), any(), any());

        Message inMessage = mock(Message.class);
        when(inMessage.get(any())).thenReturn(Collections.emptyMap());
        when(cxfExchange.getInMessage()).thenReturn(inMessage);
        when(endpoint.getCookieHandler()).thenReturn(cookieHandler);
        when(endpoint.getRequestUri(any())).thenReturn(URI.create("http://wrong-server/"));

        AtomicInteger doneCount = new AtomicInteger();
        AsyncCallback callback = sync -> doneCount.incrementAndGet();

        Throwable[] captured = new Throwable[1];
        camelExchange = mock(Exchange.class);
        doAnswer(inv -> {
            captured[0] = (Throwable) inv.getArgument(0);
            return null;
        }).when(camelExchange).setException(any(Throwable.class));
        when(camelExchange.getException()).thenAnswer(inv -> captured[0]);

        when(endpoint.getCxfBinding()).thenReturn(cxfBinding);

        Map<String, Object> ctx = new HashMap<>();
        CxfClientCallback cb = new CxfClientCallback(callback, camelExchange, cxfExchange, boi, endpoint);
        cb.handleResponse(ctx, new Object[0]);

        assertThat(doneCount.get()).as("done(false) must be called even when both cookie store and binding throw").isEqualTo(1);
        // cookie error is the primary exception; binding error must be suppressed on it, not silently lost
        assertThat(captured[0]).isSameAs(cookieError);
        assertThat(cookieError.getSuppressed()).containsExactly(bindingError);
    }
}

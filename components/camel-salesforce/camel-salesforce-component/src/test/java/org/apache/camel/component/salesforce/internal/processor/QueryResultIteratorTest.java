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
package org.apache.camel.component.salesforce.internal.processor;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.dto.generated.Account;
import org.apache.camel.component.salesforce.dto.generated.QueryRecordsAccount;
import org.apache.camel.component.salesforce.internal.client.RestClient;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class QueryResultIteratorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient = mock(RestClient.class);
    private final Map<String, List<String>> headers = Collections.emptyMap();

    @Test
    void testSinglePageIteration() {
        QueryRecordsAccount records = new QueryRecordsAccount();
        records.setDone(true);
        records.setTotalSize(2);
        records.setRecords(List.of(account("A1"), account("A2")));

        QueryResultIterator<Account> it
                = new QueryResultIterator<>(objectMapper, QueryRecordsAccount.class, restClient, headers, records);

        assertTrue(it.hasNext());
        assertEquals("A1", it.next().getName());
        assertTrue(it.hasNext());
        assertEquals("A2", it.next().getName());
        assertFalse(it.hasNext());
        assertThrows(NoSuchElementException.class, it::next);
    }

    @Test
    void testMultiPageIteration() {
        QueryRecordsAccount page1 = new QueryRecordsAccount();
        page1.setDone(false);
        page1.setTotalSize(3);
        page1.setNextRecordsUrl("/services/data/v50.0/query/next1");
        page1.setRecords(List.of(account("A1")));

        String page2Json = "{\"done\":true,\"totalSize\":3,\"records\":[{\"Name\":\"A2\"},{\"Name\":\"A3\"}]}";

        doAnswer(callbackWithResponse(page2Json))
                .when(restClient).queryMore(eq("/services/data/v50.0/query/next1"), any(), any());

        QueryResultIterator<Account> it
                = new QueryResultIterator<>(objectMapper, QueryRecordsAccount.class, restClient, headers, page1);

        assertTrue(it.hasNext());
        assertEquals("A1", it.next().getName());
        assertTrue(it.hasNext());
        assertEquals("A2", it.next().getName());
        assertTrue(it.hasNext());
        assertEquals("A3", it.next().getName());
        assertFalse(it.hasNext());
    }

    @Test
    void testQueryMoreExceptionDoesNotHang() {
        QueryRecordsAccount page1 = new QueryRecordsAccount();
        page1.setDone(false);
        page1.setTotalSize(2);
        page1.setNextRecordsUrl("/services/data/v50.0/query/next1");
        page1.setRecords(List.of(account("A1")));

        SalesforceException sfException = new SalesforceException("Session expired", 401);

        doAnswer(callbackWithException(sfException))
                .when(restClient).queryMore(eq("/services/data/v50.0/query/next1"), any(), any());

        QueryResultIterator<Account> it
                = new QueryResultIterator<>(objectMapper, QueryRecordsAccount.class, restClient, headers, page1);

        assertEquals("A1", it.next().getName());

        RuntimeCamelException thrown = assertThrows(RuntimeCamelException.class, it::next);
        assertInstanceOf(SalesforceException.class, thrown.getCause());
    }

    @Test
    void testQueryMoreExceptionFromSeparateThreadDoesNotHang() throws Exception {
        QueryRecordsAccount page1 = new QueryRecordsAccount();
        page1.setDone(false);
        page1.setTotalSize(2);
        page1.setNextRecordsUrl("/services/data/v50.0/query/next1");
        page1.setRecords(List.of(account("A1")));

        SalesforceException sfException = new SalesforceException("Network error", 0);

        doAnswer(invocation -> {
            RestClient.ResponseCallback callback = invocation.getArgument(2);
            Thread asyncThread = new Thread(() -> callback.onResponse(null, null, sfException));
            asyncThread.start();
            return null;
        }).when(restClient).queryMore(eq("/services/data/v50.0/query/next1"), any(), any());

        QueryResultIterator<Account> it
                = new QueryResultIterator<>(objectMapper, QueryRecordsAccount.class, restClient, headers, page1);

        assertEquals("A1", it.next().getName());

        ExecutionException thrown = assertThrows(ExecutionException.class, () -> {
            CompletableFuture<Account> future = CompletableFuture.supplyAsync(it::next);
            future.get(10, TimeUnit.SECONDS);
        });
        assertInstanceOf(RuntimeCamelException.class, thrown.getCause());
        assertInstanceOf(SalesforceException.class, thrown.getCause().getCause());
    }

    @Test
    void testQueryMoreMalformedResponseDoesNotHang() {
        QueryRecordsAccount page1 = new QueryRecordsAccount();
        page1.setDone(false);
        page1.setTotalSize(2);
        page1.setNextRecordsUrl("/services/data/v50.0/query/next1");
        page1.setRecords(List.of(account("A1")));

        doAnswer(callbackWithResponse("not valid json"))
                .when(restClient).queryMore(eq("/services/data/v50.0/query/next1"), any(), any());

        QueryResultIterator<Account> it
                = new QueryResultIterator<>(objectMapper, QueryRecordsAccount.class, restClient, headers, page1);

        assertEquals("A1", it.next().getName());
        assertThrows(RuntimeCamelException.class, it::next);
    }

    private static Account account(String name) {
        Account a = new Account();
        a.setName(name);
        return a;
    }

    private static Answer<Void> callbackWithResponse(String json) {
        return invocation -> {
            RestClient.ResponseCallback callback = invocation.getArgument(2);
            callback.onResponse(
                    new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)),
                    Collections.emptyMap(), null);
            return null;
        };
    }

    private static Answer<Void> callbackWithException(SalesforceException exception) {
        return invocation -> {
            RestClient.ResponseCallback callback = invocation.getArgument(2);
            callback.onResponse(null, null, exception);
            return null;
        };
    }
}

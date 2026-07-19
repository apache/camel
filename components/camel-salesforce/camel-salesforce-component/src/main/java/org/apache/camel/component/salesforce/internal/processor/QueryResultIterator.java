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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.salesforce.api.dto.AbstractQueryRecordsBase;
import org.apache.camel.component.salesforce.api.dto.AbstractSObjectBase;
import org.apache.camel.component.salesforce.internal.client.RestClient;

public class QueryResultIterator<T extends AbstractSObjectBase> implements Iterator<T> {

    private final RestClient restClient;
    private final Map<String, List<String>> requestHeaders;
    private final ObjectMapper objectMapper;
    private final Class<? extends AbstractQueryRecordsBase<T>> responseClass;
    private AbstractQueryRecordsBase<T> queryRecords;
    private Iterator<T> iterator;

    public QueryResultIterator(
                               ObjectMapper objectMapper, Class<? extends AbstractQueryRecordsBase<T>> responseClass,
                               RestClient restClient, Map<String, List<String>> headers,
                               AbstractQueryRecordsBase<T> queryRecords) {
        this.objectMapper = objectMapper;
        this.responseClass = responseClass;
        this.restClient = restClient;
        this.requestHeaders = headers;
        this.queryRecords = queryRecords;
        this.iterator = queryRecords.getRecords().iterator();
    }

    @Override
    public boolean hasNext() {
        return iterator.hasNext() || !queryRecords.getDone();
    }

    @Override
    public T next() {
        if (iterator.hasNext()) {
            return iterator.next();
        } else if (!queryRecords.getDone()) {
            final CountDownLatch latch = new CountDownLatch(1);
            List<T> valueHolder = new ArrayList<>();
            AtomicReference<Exception> errorHolder = new AtomicReference<>();

            restClient.queryMore(queryRecords.getNextRecordsUrl(), requestHeaders, (response, headers, exception) -> {
                try {
                    if (exception != null) {
                        errorHolder.set(exception);
                        return;
                    }
                    queryRecords = objectMapper.readValue(response, responseClass);
                    iterator = queryRecords.getRecords().iterator();
                    valueHolder.add(iterator.next());
                } catch (Exception e) {
                    errorHolder.set(e);
                } finally {
                    if (response != null) {
                        try {
                            response.close();
                        } catch (IOException ignored) {
                        }
                    }
                    latch.countDown();
                }
            });
            try {
                if (!latch.await(30, TimeUnit.SECONDS)) {
                    throw new RuntimeCamelException("Timeout waiting for Salesforce queryMore response");
                }
                Exception error = errorHolder.get();
                if (error != null) {
                    throw new RuntimeCamelException("Failed to fetch next page of Salesforce query results", error);
                }
                return valueHolder.get(0);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeCamelException(e);
            }
        } else {
            throw new NoSuchElementException();
        }
    }
}

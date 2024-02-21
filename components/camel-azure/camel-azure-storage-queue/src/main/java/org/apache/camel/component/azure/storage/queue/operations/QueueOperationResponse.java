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
package org.apache.camel.component.azure.storage.queue.operations;

import java.util.HashMap;
import java.util.Map;

import com.azure.core.http.rest.Response;
import com.azure.storage.queue.models.SendMessageResult;
import org.apache.camel.component.azure.storage.queue.QueueExchangeHeaders;

public final class QueueOperationResponse {

    private Object body;
    private Map<String, Object> headers = new HashMap<>();

    public QueueOperationResponse(final Object body, final Map<String, Object> headers) {
        this.body = body;
        this.headers = headers;
    }

    private QueueOperationResponse(final Object body) {
        setBody(body);
    }

    public static QueueOperationResponse create(final Object body) {
        return new QueueOperationResponse(body);
    }

    public static QueueOperationResponse create(final Object body, final Map<String, Object> headers) {
        return new QueueOperationResponse(body, headers);
    }

    @SuppressWarnings("rawtypes")
    public static QueueOperationResponse create(final Response response) {
        return buildResponse(response, false);
    }

    public static QueueOperationResponse createWithEmptyBody(final Map<String, Object> headers) {
        return new QueueOperationResponse(true, headers);
    }

    @SuppressWarnings("rawtypes")
    public static QueueOperationResponse createWithEmptyBody(final Response response) {
        return buildResponse(response, true);
    }

    @SuppressWarnings("rawtypes")
    private static QueueOperationResponse buildResponse(final Response response, final boolean emptyBody) {
        final Object body = emptyBody ? true : response.getValue();
        QueueExchangeHeaders exchangeHeaders;

        if (response.getValue() instanceof SendMessageResult) {
            exchangeHeaders = QueueExchangeHeaders
                    .createQueueExchangeHeadersFromSendMessageResult((SendMessageResult) response.getValue());
        } else {
            exchangeHeaders = new QueueExchangeHeaders();
        }

        exchangeHeaders.httpHeaders(response.getHeaders());

        return new QueueOperationResponse(body, exchangeHeaders.toMap());
    }

    public Object getBody() {
        return body;
    }

    private void setBody(Object body) {
        this.body = body;
    }

    public Map<String, Object> getHeaders() {
        return headers;
    }
}

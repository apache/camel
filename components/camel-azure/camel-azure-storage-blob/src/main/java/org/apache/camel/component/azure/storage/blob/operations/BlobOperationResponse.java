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
package org.apache.camel.component.azure.storage.blob.operations;

import java.util.HashMap;
import java.util.Map;

import com.azure.core.http.rest.Response;
import com.azure.storage.blob.models.AppendBlobItem;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.models.BlockBlobItem;
import com.azure.storage.blob.models.PageBlobItem;
import org.apache.camel.component.azure.storage.blob.BlobExchangeHeaders;

public final class BlobOperationResponse {

    private Object body;
    private Map<String, Object> headers = new HashMap<>();

    private BlobOperationResponse(final Object body, final Map<String, Object> headers) {
        this.body = body;
        this.headers = headers;
    }

    private BlobOperationResponse(final Object body) {
        setBody(body);
    }

    public static BlobOperationResponse create(final Object body) {
        return new BlobOperationResponse(body);
    }

    public static BlobOperationResponse create(final Object body, final Map<String, Object> headers) {
        return new BlobOperationResponse(body, headers);
    }

    public static BlobOperationResponse createWithEmptyBody(final Map<String, Object> headers) {
        return new BlobOperationResponse(true, headers);
    }

    public static BlobOperationResponse createWithEmptyBody() {
        return new BlobOperationResponse(true);
    }

    public static BlobOperationResponse create(final Response<?> response) {
        return buildResponse(response, false);
    }

    public static BlobOperationResponse createWithEmptyBody(final Response<?> response) {
        return buildResponse(response, true);
    }

    @SuppressWarnings("rawtypes")
    private static BlobOperationResponse buildResponse(final Response response, final boolean emptyBody) {
        final Object body = emptyBody ? true : response.getValue();
        BlobExchangeHeaders exchangeHeaders;

        if (response.getValue() instanceof BlockBlobItem) {
            exchangeHeaders
                    = BlobExchangeHeaders.createBlobExchangeHeadersFromBlockBlobItem((BlockBlobItem) response.getValue());
        } else if (response.getValue() instanceof AppendBlobItem) {
            exchangeHeaders
                    = BlobExchangeHeaders.createBlobExchangeHeadersFromAppendBlobItem((AppendBlobItem) response.getValue());
        } else if (response.getValue() instanceof PageBlobItem) {
            exchangeHeaders = BlobExchangeHeaders.createBlobExchangeHeadersFromPageBlobItem((PageBlobItem) response.getValue());
        } else if (response.getValue() instanceof BlobProperties) {
            exchangeHeaders
                    = BlobExchangeHeaders.createBlobExchangeHeadersFromBlobProperties((BlobProperties) response.getValue());
        } else {
            exchangeHeaders = BlobExchangeHeaders.create();
        }

        exchangeHeaders.httpHeaders(response.getHeaders());

        return new BlobOperationResponse(body, exchangeHeaders.toMap());
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

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

package org.apache.camel.openapi;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import io.swagger.v3.oas.models.OpenAPI;
import org.apache.camel.Exchange;

/**
 * A {@link RestApiResponseAdapter} that caches the response.
 */
public class DefaultRestApiResponseAdapter implements RestApiResponseAdapter {

    private final Map<String, String> headers = new LinkedHashMap<>();
    private byte[] body;
    private boolean noContent;
    private OpenAPI openApi;

    public OpenAPI getOpenApi() {
        return openApi;
    }

    public void setOpenApi(OpenAPI openApi) {
        this.openApi = openApi;
    }

    @Override
    public void setHeader(String name, String value) {
        headers.put(name, value);
    }

    @Override
    public void writeBytes(byte[] bytes) throws IOException {
        this.body = bytes;
    }

    @Override
    public void noContent() {
        this.noContent = true;
    }

    public void copyResult(Exchange exchange) {
        if (!headers.isEmpty()) {
            exchange.getMessage().getHeaders().putAll(headers);
        }
        if (body != null) {
            exchange.getMessage().setBody(body);
        }
        if (noContent) {
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 204);
        }
    }
}

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
package org.apache.camel.component.graphql;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.http.BaseHttpTest;
import org.apache.camel.component.http.interceptor.RequestBasicAuth;
import org.apache.camel.component.http.interceptor.ResponseBasicUnauthorized;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.protocol.DefaultHttpProcessor;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.RequestValidateHost;
import org.apache.hc.core5.http.protocol.ResponseContent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class BaseGraphqlTest extends BaseHttpTest {

    protected void assertUnauthorizedResponse(Exchange exchange) {
        assertNotNull(exchange);

        Exception ex = exchange.getException();
        assertNotNull(ex, "Should have thrown an exception");

        HttpOperationFailedException cause = assertInstanceOf(HttpOperationFailedException.class, ex);
        assertEquals(HttpStatus.SC_UNAUTHORIZED, cause.getStatusCode());

        Message in = exchange.getIn();
        assertNotNull(in);

        Map<String, Object> headers = in.getHeaders();
        assertEquals(HttpStatus.SC_UNAUTHORIZED, headers.get(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("Unauthorized", headers.get(Exchange.HTTP_RESPONSE_TEXT));
    }

    @Override
    protected HttpProcessor getBasicHttpProcessor() {
        List<HttpRequestInterceptor> requestInterceptors = new ArrayList<>();
        requestInterceptors.add(new RequestValidateHost());
        requestInterceptors.add(new RequestBasicAuth());

        List<HttpResponseInterceptor> responseInterceptors = new ArrayList<>();
        responseInterceptors.add(new ResponseContent());
        responseInterceptors.add(new ResponseBasicUnauthorized());

        return new DefaultHttpProcessor(requestInterceptors, responseInterceptors);
    }
}

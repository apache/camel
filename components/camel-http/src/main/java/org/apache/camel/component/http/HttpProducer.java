/**
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
package org.apache.camel.component.http;


import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Producer;
import org.apache.camel.component.http.helper.LoadingByteArrayOutputStream;
import org.apache.camel.impl.DefaultProducer;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.EntityEnclosingMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.io.IOUtils;


import static org.apache.camel.component.http.HttpMethods.HTTP_METHOD;

/**
 * @version $Revision$
 */
public class HttpProducer extends DefaultProducer<HttpExchange> implements Producer<HttpExchange> {
    public static final String HTTP_RESPONSE_CODE = "http.responseCode";
    public static final String QUERY = "org.apache.camel.component.http.query";

    // This should be a set of lower-case strings
    public static final Set<String> HEADERS_TO_SKIP = new HashSet<String>(Arrays.asList("content-length",
                                                                                        "content-type",
                                                                                        HTTP_RESPONSE_CODE
                                                                                            .toLowerCase()));
    private HttpClient httpClient;

    public HttpProducer(HttpEndpoint endpoint) {
        super(endpoint);
        httpClient = endpoint.createHttpClient();
    }

    public void process(Exchange exchange) throws Exception {
        HttpMethod method = createMethod(exchange);
        Message in = exchange.getIn();
        HttpBinding binding = ((HttpEndpoint)getEndpoint()).getBinding();
        // propagate headers as HTTP headers
        for (String headerName : in.getHeaders().keySet()) {
            String headerValue = in.getHeader(headerName, String.class);
            if (binding.shouldHeaderBePropagated(headerName, headerValue)) {
                method.addRequestHeader(headerName, headerValue);
            }
        }

        // lets store the result in the output message.
        Message out = exchange.getOut(true);
        try {
            int responseCode = httpClient.executeMethod(method);
            out.setHeaders(in.getHeaders());
            out.setHeader(HTTP_RESPONSE_CODE, responseCode);
            LoadingByteArrayOutputStream bos = new LoadingByteArrayOutputStream();
            InputStream is = method.getResponseBodyAsStream();
            IOUtils.copy(is, bos);
            bos.flush();
            is.close();
            out.setBody(bos.createInputStream());
        } finally {
            method.releaseConnection();
        }

        // lets set the headers
        Header[] headers = method.getResponseHeaders();
        for (Header header : headers) {
            String name = header.getName();
            String value = header.getValue();
            out.setHeader(name, value);
        }

    }

    public HttpClient getHttpClient() {
        return httpClient;
    }

    public void setHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    protected HttpMethod createMethod(Exchange exchange) {
        String uri = ((HttpEndpoint)getEndpoint()).getHttpUri().toString();

        RequestEntity requestEntity = createRequestEntity(exchange);
        Object m = exchange.getIn().getHeader(HTTP_METHOD);
        HttpMethods ms = m instanceof HttpMethods
            ? (HttpMethods)m : HttpMethods.valueOf(m == null
                                                       ? requestEntity == null
                                                           ? "GET" : "POST"
                                                               : m.toString());

        HttpMethod method = ms.createMethod(uri);

        if (exchange.getIn().getHeader(QUERY) != null) {
            method.setQueryString(exchange.getIn().getHeader(QUERY, String.class));
        }
        if (ms.isEntityEnclosing()) {
            ((EntityEnclosingMethod)method).setRequestEntity(requestEntity);
        }
        return method;
    }

    protected RequestEntity createRequestEntity(Exchange exchange) {
        Message in = exchange.getIn();
        if (in.getBody() == null) {
            return null;
        }
        RequestEntity entity = in.getBody(RequestEntity.class);
        if (entity == null) {

            String data = in.getBody(String.class);
            String contentType = in.getHeader("Content-Type", String.class);
            try {
                if (contentType != null) {
                    return new StringRequestEntity(data, contentType, null);
                }
                return new StringRequestEntity(data, null, null);
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }
        return entity;
    }

    protected boolean shouldHeaderBePropagated(String headerName, String headerValue) {
        if (headerValue == null) {
            return false;
        }
        if (HTTP_METHOD.equals(headerName)) {
            return false;
        }
        if (headerName.startsWith("org.apache.camel")) {
            return false;
        }
        if (HEADERS_TO_SKIP.contains(headerName.toLowerCase())) {
            return false;
        }
        return true;
    }
}

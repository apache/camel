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
package org.apache.camel.component.netty.http;

import java.util.Map;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultMessage;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;

/**
 * Netty HTTP based {@link org.apache.camel.Message}.
 * <p/>
 * This implementation allows direct access to the Netty {@link HttpRequest} using
 * the {@link #getHttpRequest()} method.
 */
public class NettyHttpMessage extends DefaultMessage {

    private final transient HttpRequest httpRequest;
    private final transient HttpResponse httpResponse;
    private final transient NettyHttpBinding httpBinding;

    public NettyHttpMessage(HttpRequest httpRequest, HttpResponse httpResponse, NettyHttpBinding httpBinding) {
        this.httpRequest = httpRequest;
        this.httpResponse = httpResponse;
        this.httpBinding = httpBinding;
    }

    public HttpRequest getHttpRequest() {
        return httpRequest;
    }

    public HttpResponse getHttpResponse() {
        return httpResponse;
    }

    @Override
    protected void populateInitialHeaders(Map<String, Object> map) {
        try {
            if (httpRequest != null) {
                httpBinding.populateCamelHeaders(httpRequest, map, getExchange());
            } else {
                httpBinding.populateCamelHeaders(httpResponse, map, getExchange());
            }
        } catch (Exception e) {
            throw new RuntimeCamelException("Error populating initial headers", e);
        }
    }

    @Override
    public DefaultMessage newInstance() {
        return new NettyHttpMessage(httpRequest, httpResponse, httpBinding);
    }
}

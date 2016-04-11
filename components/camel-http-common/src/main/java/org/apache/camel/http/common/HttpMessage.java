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
package org.apache.camel.http.common;

import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultMessage;
import org.apache.camel.util.ObjectHelper;

/**
 * @version 
 */
public class HttpMessage extends DefaultMessage {

    private HttpServletRequest request;
    private HttpServletResponse response;

    public HttpMessage(Exchange exchange, HttpServletRequest request, HttpServletResponse response) {
        setExchange(exchange);
        this.request = request;
        this.response = response;
        // Put the request and response into the message header
        this.setHeader(Exchange.HTTP_SERVLET_REQUEST, request);
        this.setHeader(Exchange.HTTP_SERVLET_RESPONSE, response);
        
        // Check the setting of exchange
        Boolean flag = exchange.getProperty(Exchange.SKIP_WWW_FORM_URLENCODED, Boolean.class);
        if (flag != null && flag) {
            this.setHeader(Exchange.SKIP_WWW_FORM_URLENCODED, Boolean.TRUE);
        }

        // use binding to read the request allowing end users to use their
        // implementation of the binding
        getEndpoint().getHttpBinding().readRequest(request, this);
    }

    private HttpMessage(HttpServletRequest request, HttpServletResponse response, Exchange exchange) {
        this.request = request;
        this.response = response;
        setExchange(getExchange());
    }

    public HttpServletRequest getRequest() {
        return request;
    }

    public HttpServletResponse getResponse() {
        return response;
    }

    @Override
    protected Object createBody() {
        try {
            return getEndpoint().getHttpBinding().parseBody(this);
        } catch (IOException e) {
            throw new RuntimeCamelException(e);
        }
    }

    @Override
    public HttpMessage newInstance() {
        return new HttpMessage(request, response, getExchange());
    }

    private HttpCommonEndpoint getEndpoint() {
        return (HttpCommonEndpoint) getExchange().getFromEndpoint();
    }

    @Override
    public String toString() {
        // do not use toString on HTTP message
        return "HttpMessage@" + ObjectHelper.getIdentityHashCode(this);
    }
}

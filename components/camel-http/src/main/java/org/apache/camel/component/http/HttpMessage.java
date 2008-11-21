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

import java.io.IOException;
import java.util.Enumeration;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultMessage;

/**
 * @version $Revision$
 */
public class HttpMessage extends DefaultMessage {
    private HttpServletRequest request;

    public HttpMessage(HttpExchange exchange, HttpServletRequest request) {
        setExchange(exchange);
        this.request = request;

        // use binding to read the request allowing end users to use their
        // implementation of the binding
        getExchange().getEndpoint().getBinding().readRequest(request, this);
    }

    @Override
    public HttpExchange getExchange() {
        return (HttpExchange)super.getExchange();
    }

    public HttpServletRequest getRequest() {
        return request;
    }

    @Override
    protected Object createBody() {
        try {
            return getExchange().getEndpoint().getBinding().parseBody(this);
        } catch (IOException e) {
            throw new RuntimeCamelException(e);
        }
    }

    @Override
    protected void populateInitialHeaders(Map<String, Object> map) {
        // populate the http request headers
        Enumeration names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = (String)names.nextElement();
            Object value = request.getHeader(name);
            map.put(name, value);
        }

        //if the request method is Get, we also populate the http request parameters
        if (request.getMethod().equalsIgnoreCase("GET")) {
            names = request.getParameterNames();
            while (names.hasMoreElements()) {
                String name = (String)names.nextElement();
                Object value = request.getParameter(name);
                map.put(name, value);
            }
        }

        // store the method and query as well
        map.put(HttpMethods.HTTP_METHOD, request.getMethod());
        map.put(HttpProducer.QUERY, request.getQueryString());
    }
}

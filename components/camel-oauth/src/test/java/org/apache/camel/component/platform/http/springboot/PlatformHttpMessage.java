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
package org.apache.camel.component.platform.http.springboot;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultMessage;

/**
 * Test stand-in for the message of platform-http on Spring Boot (camel-platform-http-starter, in camel-spring-boot),
 * which camel-oauth does not depend on. It has the same name and, like the real one, gives the servlet request from
 * {@link #getRequest()} and holds it in the {@link Exchange#HTTP_SERVLET_REQUEST} header.
 */
public class PlatformHttpMessage extends DefaultMessage {

    private final HttpServletRequest request;

    public PlatformHttpMessage(CamelContext camelContext, HttpServletRequest request) {
        super(camelContext);
        this.request = request;
        setHeader(Exchange.HTTP_SERVLET_REQUEST, request);
    }

    public HttpServletRequest getRequest() {
        return request;
    }
}

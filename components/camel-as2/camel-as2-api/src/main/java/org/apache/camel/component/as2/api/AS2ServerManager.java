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
package org.apache.camel.component.as2.api;

import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;

/**
 * Receives EDI Messages over HTTP
 */
public class AS2ServerManager {

    //
    // AS2 HTTP Context Attribute Keys
    //

    /**
     * Prefix for all AS2 HTTP Context Attributes used by the Http Server Manager.
     */
    public static final String CAMEL_AS2_SERVER_PREFIX = "camel-as2.server";

    /**
     * The HTTP Context Attribute containing the subject header sent in an AS2 response.
     */
    public static final String SUBJECT = CAMEL_AS2_SERVER_PREFIX + "subject";

    /**
     * The HTTP Context Attribute containing the internet e-mail address of responding system
     */
    public static final String FROM = CAMEL_AS2_SERVER_PREFIX + "from";

    private AS2ServerConnection as2ServerConnection;

    public AS2ServerManager(AS2ServerConnection as2ServerConnection) {
        this.as2ServerConnection = as2ServerConnection;
    }

    public void listen(String requestUriPattern, HttpRequestHandler handler) {
        as2ServerConnection.listen(requestUriPattern, handler);
    }

    public void stopListening(String requestUri) {
        as2ServerConnection.stopListening(requestUri);
    }

    public void handleMDNResponse(
            HttpContext httpContext, String subject, String from) {
        // Add Context attributes for Response
        httpContext.setAttribute(SUBJECT, subject);
        httpContext.setAttribute(FROM, from);
    }
}

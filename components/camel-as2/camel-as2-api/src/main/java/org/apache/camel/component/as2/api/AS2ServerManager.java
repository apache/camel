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
package org.apache.camel.component.as2.api;

import java.io.IOException;

import org.apache.camel.component.as2.api.util.HttpMessageUtils;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AS2 Server Manager 
 * 
 * <p>Receives EDI Messages over HTTP  
 *
 */
public class AS2ServerManager {
    
    private static final Logger LOG = LoggerFactory.getLogger(AS2ServerManager.class);
    
    //
    // AS2 HTTP Context Attribute Keys
    //

    /**
     * Prefix for all AS2 HTTP Context Attributes used by the Http Server
     * Manager.
     */
    public static final String CAMEL_AS2_SERVER_PREFIX = "camel-as2.server";

    /**
     * The HTTP Context Attribute indicating a Message Disposition Notification is to be sent.
     */
    public static final String MESSAGE_DISPOSITION_NOTIFICATION = CAMEL_AS2_SERVER_PREFIX + "message-disposition-notification";

    /**
     * The HTTP Context Attribute indicating a Message Disposition Notification is to be sent.
     */
    public static final String MESSAGE_DISPOSITION_OPTIONS = CAMEL_AS2_SERVER_PREFIX + "message-disposition-options";

    /**
     * The HTTP Context Attribute indicating the address the receipt is to be sent to.
     */
    public static final String RECEIPT_ADDRESS = CAMEL_AS2_SERVER_PREFIX + "receipt-address";

    /**
     * The HTTP Context Attribute containing the subject header sent in an AS2
     * response.
     */
    public static final String SUBJECT = CAMEL_AS2_SERVER_PREFIX + "subject";

    /**
     * The HTTP Context Attribute containing the internet e-mail address of
     * responding system
     */
    public static final String FROM = CAMEL_AS2_SERVER_PREFIX + "from";

    /**
     * The HTTP Context Attribute containing the AS2 System Identifier of the
     * responding system
     */
    public static final String AS2_FROM = CAMEL_AS2_SERVER_PREFIX + "as2-from";

    /**
     * The HTTP Context Attribute containing the AS2 System Identifier of the
     * responded to system
     */
    public static final String AS2_TO = CAMEL_AS2_SERVER_PREFIX + "as2-to";

    private AS2ServerConnection as2ServerConnection;
    
    public AS2ServerManager(AS2ServerConnection as2ServerConnection) {
        this.as2ServerConnection = as2ServerConnection;
    }
    
    public void listen(String requestUriPattern, HttpRequestHandler handler) {
        try {
            as2ServerConnection.listen(requestUriPattern, handler);
        } catch (IOException e) {
            LOG.error("Failed to listen for '" + requestUriPattern + "' requests: " + e.getMessage(), e);
            throw new RuntimeException("Failed to listen for '" + requestUriPattern + "' requests: " + e.getMessage(), e);
        }
                
    }
    
    public void stopListening(String requestUri) {
        as2ServerConnection.stopListening(requestUri);
    }
    
    public void processMDNRequest(HttpEntityEnclosingRequest request, HttpResponse response, HttpContext httpContext, String subject, String from) throws HttpException {
        String dispositionNotificationTo = HttpMessageUtils.getHeaderValue(request, AS2Header.DISPOSITION_NOTIFICATION_TO);
        if (dispositionNotificationTo != null) {

            httpContext.setAttribute(SUBJECT, subject);
            httpContext.setAttribute(FROM, from);
            httpContext.setAttribute(AS2_FROM, HttpMessageUtils.getHeaderValue(request, AS2Header.AS2_TO));
            httpContext.setAttribute(AS2_TO, HttpMessageUtils.getHeaderValue(request, AS2Header.AS2_FROM));
            httpContext.setAttribute(MESSAGE_DISPOSITION_NOTIFICATION, dispositionNotificationTo);
            httpContext.setAttribute(RECEIPT_ADDRESS, HttpMessageUtils.getHeaderValue(request, AS2Header.RECEIPT_DELIVERY_OPTION));
            httpContext.setAttribute(MESSAGE_DISPOSITION_OPTIONS, HttpMessageUtils.getHeaderValue(request, AS2Header.DISPOSITION_NOTIFICATION_OPTIONS));
            
        }
    }
}

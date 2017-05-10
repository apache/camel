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

import static org.apache.camel.component.as2.api.AS2Constants.APPLICATION_EDIFACT_MIME_TYPE;
import static org.apache.camel.component.as2.api.AS2Constants.AS2_FROM_HEADER;
import static org.apache.camel.component.as2.api.AS2Constants.AS2_TO_HEADER;
import static org.apache.camel.component.as2.api.AS2Constants.AS2_VERSION_HEADER;
import static org.apache.camel.component.as2.api.AS2Constants.CLIENT_FQDN;
import static org.apache.camel.component.as2.api.AS2Constants.CONTENT_TYPE_HEADER;
import static org.apache.camel.component.as2.api.AS2Constants.HTTP_CONNECTION;
import static org.apache.camel.component.as2.api.AS2Constants.HTTP_PROCESSOR;
import static org.apache.camel.component.as2.api.AS2Constants.MESSAGE_ID_HEADER;
import static org.apache.camel.component.as2.api.AS2Constants.SUBJECT_HEADER;

import java.io.IOException;

import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.DefaultBHttpClientConnection;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.protocol.HttpCoreContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestExecutor;

/**
 * AS2 Send Manager 
 * 
 * <p>Sends EDI Messages over HTTP  
 *
 */
public class AS2SendManager {

    private HttpCoreContext httpContext;
    
    public AS2SendManager(HttpCoreContext httpContext) {
        this.httpContext = httpContext;
    }

    /**
     * Send HTTP Request transporting unencrypted and unsigned EDI message.
     * 
     * @param ediMessage - EDI message to transport
     * @return - HTTP Request containing unencypted and unsigned EDI message 
     * @throws InvalidAS2NameException 
     * @throws IOException 
     * @throws HttpException 
     */
    public Result sendNoEncryptNoSign(String ediMessage, String subject,  String as2From, String as2To) throws InvalidAS2NameException, HttpException, IOException {
        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
 
        /* AS2-Version header */
        request.addHeader(AS2_VERSION_HEADER, "1.1");

        /* Content-Type header (Application/EDIFACT) */   
        request.addHeader(CONTENT_TYPE_HEADER, APPLICATION_EDIFACT_MIME_TYPE);

        /* AS2-From header */
        Util.validateAS2Name(as2From);
        request.addHeader(AS2_FROM_HEADER, as2From);

        /* AS2-To header */
        Util.validateAS2Name(as2To);
        request.addHeader(AS2_TO_HEADER, as2To);

        /* Subject header */
        // SHOULD be set to aid MDN in identifying the original messaged
        request.addHeader(SUBJECT_HEADER, subject);

        /* Message-Id header*/
        // SHOULD be set to aid in message reconciliation
        String clientFqdn = httpContext.getAttribute(CLIENT_FQDN, String.class);
        request.addHeader(MESSAGE_ID_HEADER, Util.createMessageId(clientFqdn));
        
         // Create Message Body
        /* EDI Message is Message Body */
        HttpEntity entity = new StringEntity(ediMessage, ContentType.create(APPLICATION_EDIFACT_MIME_TYPE, Consts.UTF_8));
        request.setEntity(entity);

        // Execute Request
        HttpProcessor httpProcessor = httpContext.getAttribute(HTTP_PROCESSOR, HttpProcessor.class);
        DefaultBHttpClientConnection httpConnection = httpContext.getAttribute(HTTP_CONNECTION, DefaultBHttpClientConnection.class);
        HttpRequestExecutor httpexecutor = new HttpRequestExecutor();
        httpexecutor.preProcess(request, httpProcessor, httpContext);
        HttpResponse response = httpexecutor.execute(request, httpConnection, httpContext);   
        httpexecutor.postProcess(response, httpProcessor, httpContext);
        
        // Process response
        Result result = new Result();
        result.statusCode = response.getStatusLine().getStatusCode();
        result.reasonPhrase = response.getStatusLine().getReasonPhrase();
        
        return result;
    }
    
    public static class Result {
        public int statusCode;
        public String reasonPhrase;
    }
    
}

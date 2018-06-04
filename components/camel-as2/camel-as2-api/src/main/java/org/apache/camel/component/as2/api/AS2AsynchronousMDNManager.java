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
import java.net.Socket;
import java.security.PrivateKey;
import java.security.cert.Certificate;

import org.apache.camel.component.as2.api.entity.DispositionNotificationMultipartReportEntity;
import org.apache.camel.component.as2.api.entity.EntityParser;
import org.apache.camel.component.as2.api.io.AS2BHttpClientConnection;
import org.apache.camel.component.as2.api.protocol.RequestAsynchronousMDN;
import org.apache.camel.component.as2.api.util.EntityUtils;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.impl.DefaultBHttpClientConnection;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.protocol.HttpCoreContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpProcessorBuilder;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.protocol.RequestConnControl;
import org.apache.http.protocol.RequestContent;
import org.apache.http.protocol.RequestDate;
import org.apache.http.protocol.RequestExpectContinue;
import org.apache.http.protocol.RequestTargetHost;
import org.apache.http.protocol.RequestUserAgent;

public class AS2AsynchronousMDNManager {

    //
    // AS2 HTTP Context Attribute Keys
    //

    /**
     * Prefix for all AS2 HTTP Context Attributes used by the AS2 Asynchronous MDN
     * Manager.
     */
    public static final String CAMEL_AS2_ASYNC_MDN_PREFIX = "camel-as2.async-mdn.";

    /**
     * The HTTP Context Attribute containing the HTTP request message
     * transporting the EDI message
     */
    public static final String HTTP_REQUEST = HttpCoreContext.HTTP_REQUEST;

    /**
     * The HTTP Context Attribute containing the HTTP response message
     * transporting the EDI message
     */
    public static final String HTTP_RESPONSE = HttpCoreContext.HTTP_RESPONSE;

    /**
     * The HTTP Context Attribute containing the AS2 Connection used to send
     * request message.
     */
    public static final String AS2_CONNECTION = CAMEL_AS2_ASYNC_MDN_PREFIX + "as2-connection";

    /**
     * The HTTP Context Attribute indicating the target host MDN sent to.
     */
    public static final String TARGET_HOST = CAMEL_AS2_ASYNC_MDN_PREFIX + "target-host";

    /**
     * The HTTP Context Attribute indicating the target port MDN sent to.
     */
    public static final String TARGET_PORT = CAMEL_AS2_ASYNC_MDN_PREFIX + "target-port";

    /**
     * The HTTP Context Attribute containing the subject header sent in MDN.
     */
    public static final String SUBJECT = CAMEL_AS2_ASYNC_MDN_PREFIX + "subject";

    /**
     * The HTTP Context Attribute containing the internet e-mail address of this
     * responding system
     */
    public static final String FROM = CAMEL_AS2_ASYNC_MDN_PREFIX + "from";

    /**
     * The HTTP Context Attribute indicating the AS2 name of MDN recipient.
     */
    public static final String AS2_TO = CAMEL_AS2_ASYNC_MDN_PREFIX + "as2-to";

    /**
     * The HTTP Context Attribute indicating the AS2 name of MDN sender.
     */
    public static final String AS2_FROM = CAMEL_AS2_ASYNC_MDN_PREFIX + "as2-from";

    /**
     * The HTTP Context Attribute indicating the AS2 name of MDN sender.
     */
    public static final String RECIPIENT_ADDRESS = CAMEL_AS2_ASYNC_MDN_PREFIX + "recipient-address";

    private HttpProcessor httpProcessor;
    @SuppressWarnings("unused")
    private Certificate[] signingCertificateChain;
    @SuppressWarnings("unused")
    private PrivateKey signingPrivateKey;

    public AS2AsynchronousMDNManager(String as2Version,
                                     String userAgent,
                                     String senderFQDN,
                                     Certificate[] signingCertificateChain,
                                     PrivateKey signingPrivateKey) {
        this.signingCertificateChain = signingCertificateChain;
        this.signingPrivateKey = signingPrivateKey;

        // Build Processor
        httpProcessor = HttpProcessorBuilder.create().add(new RequestAsynchronousMDN(as2Version, senderFQDN))
                .add(new RequestTargetHost()).add(new RequestUserAgent(userAgent)).add(new RequestDate())
                .add(new RequestContent(true)).add(new RequestConnControl()).add(new RequestExpectContinue(true))
                .build();
    }

    public HttpCoreContext send(DispositionNotificationMultipartReportEntity mdn,
                                String targetHostName,
                                Integer targetPortNumber,
                                String requestUri,
                                String subject,
                                String from,
                                String as2From,
                                String as2To)
            throws HttpException {
        if (targetHostName == null || targetHostName.length() == 0) {
            targetHostName = "localhost";
        }
        if (targetPortNumber == null || targetPortNumber < 0) {
            targetPortNumber = 80;
        }
        
        AS2BHttpClientConnection httpConnection = new AS2BHttpClientConnection(8 * 1024);
        
        try {
            HttpHost targetHost = new HttpHost(targetHostName, targetPortNumber);

            // Create socket and bind to connection;
            Socket socket = new Socket(targetHost.getHostName(), targetHost.getPort());
            httpConnection.bind(socket);

            // Add Context attributes
            HttpCoreContext httpContext = HttpCoreContext.create();
            httpContext.setTargetHost(targetHost);
            httpContext.setAttribute(AS2AsynchronousMDNManager.SUBJECT, subject);
            httpContext.setAttribute(AS2AsynchronousMDNManager.FROM, from);
            httpContext.setAttribute(AS2AsynchronousMDNManager.AS2_FROM, as2From);
            httpContext.setAttribute(AS2AsynchronousMDNManager.AS2_TO, as2To);

            BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", requestUri);
            request.setHeader(AS2Header.CONTENT_TYPE, mdn.getMainMessageContentType());
            httpContext.setAttribute(HttpCoreContext.HTTP_REQUEST, request);
            mdn.setMainBody(true);
            EntityUtils.setMessageEntity(request, mdn);
            
            HttpResponse response;
            try {
                httpContext.setAttribute(AS2_CONNECTION, httpConnection);
                response = send(httpConnection, request, httpContext);
                EntityParser.parseAS2MessageEntity(response);
            } catch (IOException e) {
                throw new HttpException("Failed to send http request message", e);
            }
            httpContext.setAttribute(HTTP_RESPONSE, response);

            return httpContext;
        } catch (Exception e) {
            throw new HttpException("failed to send MDN", e);
        } finally {
            try {
                httpConnection.flush();
                httpConnection.close();
            } catch (IOException e) {
                throw new HttpException("Failed to flush and close connection", e);
            }
        }
    }

    private HttpResponse send(DefaultBHttpClientConnection httpConnection, HttpRequest request, HttpCoreContext httpContext) throws HttpException, IOException {

        // Execute Request
        HttpRequestExecutor httpexecutor = new HttpRequestExecutor();
        httpexecutor.preProcess(request, httpProcessor, httpContext);
        HttpResponse response = httpexecutor.execute(request, httpConnection, httpContext);
        httpexecutor.postProcess(response, httpProcessor, httpContext);

        return response;
    }
}

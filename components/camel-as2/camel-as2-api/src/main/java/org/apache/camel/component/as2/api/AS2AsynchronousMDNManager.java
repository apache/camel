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

import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.security.PrivateKey;
import java.security.cert.Certificate;

import org.apache.camel.component.as2.api.entity.MultipartMimeEntity;
import org.apache.camel.component.as2.api.protocol.RequestAsynchronousMDN;
import org.apache.camel.component.as2.api.util.AS2HeaderUtils;
import org.apache.camel.component.as2.api.util.EntityUtils;
import org.apache.camel.util.ObjectHelper;
import org.apache.hc.client5.http.impl.io.ManagedHttpClientConnectionFactory;
import org.apache.hc.client5.http.io.ManagedHttpClientConnection;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.http.io.HttpClientConnection;
import org.apache.hc.core5.http.io.HttpConnectionFactory;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.HttpProcessorBuilder;
import org.apache.hc.core5.http.protocol.RequestConnControl;
import org.apache.hc.core5.http.protocol.RequestContent;
import org.apache.hc.core5.http.protocol.RequestDate;
import org.apache.hc.core5.http.protocol.RequestExpectContinue;
import org.apache.hc.core5.http.protocol.RequestTargetHost;
import org.apache.hc.core5.http.protocol.RequestUserAgent;

public class AS2AsynchronousMDNManager {

    //
    // AS2 HTTP Context Attribute Keys
    //

    /**
     * Prefix for all AS2 HTTP Context Attributes used by the AS2 Asynchronous MDN Manager.
     */
    public static final String CAMEL_AS2_ASYNC_MDN_PREFIX = "camel-as2.async-mdn.";

    /**
     * The HTTP Context Attribute containing the HTTP request message transporting the EDI message
     */
    public static final String HTTP_REQUEST = HttpCoreContext.HTTP_REQUEST;

    /**
     * The HTTP Context Attribute containing the HTTP response message transporting the EDI message
     */
    public static final String HTTP_RESPONSE = HttpCoreContext.HTTP_RESPONSE;

    /**
     * The HTTP Context Attribute containing the AS2 Connection used to send request message.
     */
    public static final String AS2_CONNECTION = CAMEL_AS2_ASYNC_MDN_PREFIX + "as2-connection";

    /**
     * The HTTP Context Attribute indicating the AS2 name of MDN sender.
     */
    public static final String RECIPIENT_ADDRESS = CAMEL_AS2_ASYNC_MDN_PREFIX + "recipient-address";

    /**
     * The HTTP Context Attribute containing an asynchronous MDN receipt.
     */
    public static final String ASYNCHRONOUS_MDN = CAMEL_AS2_ASYNC_MDN_PREFIX + "asynchronous-mdn";

    private HttpProcessor httpProcessor;

    @SuppressWarnings("unused")
    private Certificate[] signingCertificateChain;
    @SuppressWarnings("unused")
    private PrivateKey signingPrivateKey;
    private String userName;
    private String password;
    private String accessToken;

    public AS2AsynchronousMDNManager(String as2Version,
                                     String userAgent,
                                     String senderFQDN,
                                     Certificate[] signingCertificateChain,
                                     PrivateKey signingPrivateKey,
                                     String userName,
                                     String password,
                                     String accessToken) {
        this.signingCertificateChain = signingCertificateChain;
        this.signingPrivateKey = signingPrivateKey;
        this.userName = userName;
        this.password = password;
        this.accessToken = accessToken;

        // Build Processor
        httpProcessor = HttpProcessorBuilder.create().add(new RequestAsynchronousMDN(as2Version, senderFQDN))
                .add(new RequestTargetHost()).add(new RequestUserAgent(userAgent)).add(new RequestDate())
                .add(new RequestContent(true)).add(new RequestConnControl()).add(new RequestExpectContinue())
                .build();
    }

    // Sends the signed or unsigned AS2-MDN to the URI requested by the sender of the AS2 message.
    public HttpCoreContext send(
            MultipartMimeEntity multipartMimeEntity,
            String contentType,
            String recipientDeliveryAddress)
            throws HttpException {
        ObjectHelper.notNull(multipartMimeEntity, "multipartMimeEntity");
        ObjectHelper.notNull(contentType, "contentType");
        ObjectHelper.notNull(recipientDeliveryAddress, "recipientDeliveryAddress");

        URI uri = URI.create(recipientDeliveryAddress);

        int buffSize = 8 * 1024;

        Http1Config h1Config = Http1Config.custom().setBufferSize(buffSize).build();
        HttpConnectionFactory<ManagedHttpClientConnection> connFactory
                = ManagedHttpClientConnectionFactory.builder().http1Config(h1Config).build();

        try (HttpClientConnection httpConnection = connFactory.createConnection(new Socket(uri.getHost(), uri.getPort()))) {

            // Add Context attributes
            HttpCoreContext httpContext = HttpCoreContext.create();
            httpContext.setAttribute(RECIPIENT_ADDRESS, recipientDeliveryAddress);

            ClassicHttpRequest request = new BasicClassicHttpRequest("POST", uri);
            request.setHeader(AS2Header.CONTENT_TYPE, contentType);
            AS2HeaderUtils.addAuthorizationHeader(request, userName, password, accessToken);
            httpContext.setAttribute(HttpCoreContext.HTTP_REQUEST, request);
            multipartMimeEntity.setMainBody(true);
            EntityUtils.setMessageEntity(request, multipartMimeEntity);

            HttpResponse response;
            try {
                httpContext.setAttribute(AS2_CONNECTION, httpConnection);
                response = send(httpConnection, request, httpContext);
            } catch (IOException e) {
                throw new HttpException("Failed to send http request message", e);
            }
            httpContext.setAttribute(HTTP_RESPONSE, response);

            return httpContext;
        } catch (Exception e) {
            throw new HttpException("failed to send MDN", e);
        }
    }

    private HttpResponse send(HttpClientConnection httpConnection, ClassicHttpRequest request, HttpCoreContext httpContext)
            throws HttpException, IOException {

        // Execute Request
        HttpRequestExecutor httpExecutor = new HttpRequestExecutor();
        httpExecutor.preProcess(request, httpProcessor, httpContext);
        ClassicHttpResponse response = httpExecutor.execute(request, httpConnection, httpContext);
        httpExecutor.postProcess(response, httpProcessor, httpContext);

        return response;
    }

}

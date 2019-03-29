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
import java.net.UnknownHostException;

import org.apache.camel.component.as2.api.io.AS2BHttpClientConnection;
import org.apache.camel.component.as2.api.protocol.RequestAS2;
import org.apache.camel.component.as2.api.protocol.RequestMDN;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.impl.DefaultBHttpClientConnection;
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
import org.apache.http.util.Args;

public class AS2ClientConnection {

    private HttpHost targetHost;
    private HttpProcessor httpProcessor;
    private DefaultBHttpClientConnection httpConnection;
    private String as2Version;
    private String userAgent;
    private String clientFqdn;

    public AS2ClientConnection(String as2Version, String userAgent, String clientFqdn, String targetHostName, Integer targetPortNumber) throws UnknownHostException, IOException {

        this.as2Version = Args.notNull(as2Version, "as2Version");
        this.userAgent = Args.notNull(userAgent, "userAgent");
        this.clientFqdn = Args.notNull(clientFqdn, "clientFqdn");
        this.targetHost = new HttpHost(Args.notNull(targetHostName, "targetHostName"), Args.notNull(targetPortNumber, "targetPortNumber"));

        // Build Processor
        httpProcessor = HttpProcessorBuilder.create()
                .add(new RequestAS2(as2Version, clientFqdn))
                .add(new RequestMDN())
                .add(new RequestTargetHost())
                .add(new RequestUserAgent(this.userAgent))
                .add(new RequestDate())
                .add(new RequestContent(true))
                .add(new RequestConnControl())
                .add(new RequestExpectContinue(true)).build();

        // Create Socket
        Socket socket = new Socket(targetHost.getHostName(), targetHost.getPort());

        // Create Connection
        httpConnection = new AS2BHttpClientConnection(8 * 1024);
        httpConnection.bind(socket);
    }

    public String getAs2Version() {
        return as2Version;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getClientFqdn() {
        return clientFqdn;
    }

    public HttpResponse send(HttpRequest request, HttpCoreContext httpContext) throws HttpException, IOException {

        httpContext.setTargetHost(targetHost);

        // Execute Request
        HttpRequestExecutor httpexecutor = new HttpRequestExecutor();
        httpexecutor.preProcess(request, httpProcessor, httpContext);
        HttpResponse response = httpexecutor.execute(request, httpConnection, httpContext);
        httpexecutor.postProcess(response, httpProcessor, httpContext);

        return response;
    }

}

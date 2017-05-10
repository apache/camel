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
package org.apache.camel.component.as2.internal;

import static org.apache.camel.component.as2.api.AS2Constants.CLIENT_FQDN;
import static org.apache.camel.component.as2.api.AS2Constants.HTTP_CONNECTION;
import static org.apache.camel.component.as2.api.AS2Constants.HTTP_PROCESSOR;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

import org.apache.camel.component.as2.AS2Configuration;
import org.apache.http.HttpHost;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.impl.DefaultBHttpClientConnection;
import org.apache.http.impl.DefaultBHttpClientConnectionFactory;
import org.apache.http.protocol.HttpCoreContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpProcessorBuilder;
import org.apache.http.protocol.RequestConnControl;
import org.apache.http.protocol.RequestContent;
import org.apache.http.protocol.RequestDate;
import org.apache.http.protocol.RequestExpectContinue;
import org.apache.http.protocol.RequestTargetHost;
import org.apache.http.protocol.RequestUserAgent;

/**
 * Utility class for creating and configuring HTTP contexts.
 */
public class AS2ContextHelper {
    
    /**
     * Prevent instantiation
     */
    private AS2ContextHelper() {
    }
    
    /**
     * Creates a HTTP Context containing an HTTP processor and connected HTTP Connection 
     * @param configuration - configuration used to configure context, processor and connection
     * @return The newly created context
     * @throws UnknownHostException If failed to create connection due to unknown host.
     * @throws IOException If failed to create connection.
     */
    public static HttpCoreContext createClientHttpContext(AS2Configuration configuration) throws UnknownHostException, IOException {
        HttpProcessor httpProcessor;
        HttpCoreContext httpContext;
        DefaultBHttpClientConnection httpConnection;

        // Build Context
        httpContext = HttpCoreContext.create();
        HttpHost targetHost = new HttpHost(configuration.getTargetHostname(), configuration.getTargetPortNumber());
        httpContext.setTargetHost(targetHost);
        
        // Add Client FQDN to Context
        httpContext.setAttribute(CLIENT_FQDN, configuration.getClientFqdn());

        // Build Processor
        httpProcessor = HttpProcessorBuilder.create()
                .add(new RequestTargetHost())
                .add(new RequestUserAgent(configuration.getUserAgent()))
                .add(new RequestDate())
                .add(new RequestContent())
                .add(new RequestConnControl())
                .add(new RequestExpectContinue(true)).build();
        httpContext.setAttribute(HTTP_PROCESSOR, httpProcessor);
        
        // Build and Configure Connection
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setBufferSize(8 * 1024)
                .build();
        DefaultBHttpClientConnectionFactory connectionFactory = new DefaultBHttpClientConnectionFactory(connectionConfig);

        // Create Socket
        Socket socket = new Socket(targetHost.getHostName(), targetHost.getPort());

        // Create Connection
        httpConnection = connectionFactory.createConnection(socket);
        httpContext.setAttribute(HTTP_CONNECTION, httpConnection);
        
        return httpContext;
    }
}

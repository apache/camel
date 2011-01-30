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
package org.apache.camel.component.http4;

import javax.net.ssl.SSLContext;

import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpResponseFactory;
import org.apache.http.localserver.LocalTestServer;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.HttpExpectationVerifier;
import org.junit.After;
import org.junit.Before;

/**
 * Abstract base class for unit testing using a http server.
 * The setUp method starts the server before the camel context is started and
 * the tearDown method stops the server after the camel context is stopped.
 *
 * @version $Revision$
 */
public abstract class HttpServerTestSupport extends CamelTestSupport {

    protected LocalTestServer localServer;

    @Before
    @Override
    public void setUp() throws Exception {
        localServer = new LocalTestServer(
                getBasicHttpProcessor(),
                getConnectionReuseStrategy(),
                getHttpResponseFactory(),
                getHttpExpectationVerifier(),
                getHttpParams(),
                getSSLContext());
        registerHandler(localServer);
        localServer.start();

        super.setUp();
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        if (localServer != null) {
            localServer.stop();
        }
    }

    /**
     * Returns the org.apache.http.protocol.BasicHttpProcessor which should be
     * used by the server.
     *
     * @return basicHttpProcessor
     */
    protected BasicHttpProcessor getBasicHttpProcessor() {
        return null;
    }

    /**
     * Returns the org.apache.http.ConnectionReuseStrategy which should be used
     * by the server.
     *
     * @return connectionReuseStrategy
     */
    protected ConnectionReuseStrategy getConnectionReuseStrategy() {
        return null;
    }
    
    /**
     * Returns the org.apache.http.HttpResponseFactory which should be used
     * by the server.
     *
     * @return httpResponseFactory
     */
    protected HttpResponseFactory getHttpResponseFactory() {
        return null;
    }
    
    /**
     * Returns the org.apache.http.protocol.HttpExpectationVerifier which should be used
     * by the server.
     *
     * @return httpExpectationVerifier
     */
    protected HttpExpectationVerifier getHttpExpectationVerifier() {
        return null;
    }

    /**
     * Returns the org.apache.http.params.HttpParams which should be used by
     * the server.
     *
     * @return httpParams
     */
    protected HttpParams getHttpParams() {
        return null;
    }

    /**
     * Returns the javax.net.ssl.SSLContext which should be used by the server.
     *
     * @return sslContext
     * @throws Exception
     */
    protected SSLContext getSSLContext() throws Exception {
        return null;
    }

    /**
     * Register the org.apache.http.protocol.HttpRequestHandler which handles
     * the request and set the proper response (headers and content).
     *
     * @param server
     */
    protected void registerHandler(LocalTestServer server) {
    }

    /**
     * Obtains the host name of the local test server.
     *
     * @return hostName
     */
    protected String getHostName() {
        return localServer.getServiceAddress().getHostName();
    }

    /**
     * Obtains the port of the local test server.
     *
     * @return port
     */
    protected int getPort() {
        return localServer.getServiceAddress().getPort();
    }
}
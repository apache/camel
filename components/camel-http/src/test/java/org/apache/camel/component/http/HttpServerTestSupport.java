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
package org.apache.camel.component.http;

import javax.net.ssl.SSLContext;

import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpResponseFactory;
import org.apache.http.protocol.HttpExpectationVerifier;
import org.apache.http.protocol.HttpProcessor;

/**
 * Abstract base class for unit testing using a http server.
 * This class contains an empty configuration to be used.
 */
public abstract class HttpServerTestSupport extends CamelTestSupport {

    /**
     * Returns the org.apache.http.protocol.BasicHttpProcessor which should be
     * used by the server.
     *
     * @return HttpProcessor
     */
    protected HttpProcessor getBasicHttpProcessor() {
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
     * Returns the javax.net.ssl.SSLContext which should be used by the server.
     *
     * @return sslContext
     * @throws Exception
     */
    protected SSLContext getSSLContext() throws Exception {
        return null;
    }
}

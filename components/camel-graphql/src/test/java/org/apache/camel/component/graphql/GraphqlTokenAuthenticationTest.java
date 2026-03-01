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
package org.apache.camel.component.graphql;

import java.util.Collections;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.component.graphql.handler.BearerTokenValidationHandler;
import org.apache.camel.component.http.BaseHttpTest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.protocol.DefaultHttpProcessor;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.RequestValidateHost;
import org.apache.hc.core5.http.protocol.ResponseContent;
import org.junit.jupiter.api.Test;

/**
 * Verify 'AuthClientConfigurer' instances registered with an accessToken.
 */
public class GraphqlTokenAuthenticationTest extends BaseHttpTest {

    private final String accessToken = "accessToken";
    private HttpServer localServer;

    @Override
    public void setupResources() throws Exception {
        localServer = ServerBootstrap.bootstrap()
                .setCanonicalHostName("localhost")
                .setHttpProcessor(getBasicHttpProcessor())
                .register("/graphql", new BearerTokenValidationHandler(accessToken, getExpectedContent()))
                .create();
        localServer.start();
    }

    @Override
    public void cleanupResources() {
        if (localServer != null) {
            localServer.stop();
        }
    }

    @Test
    public void bearerTokenAuthenticationShouldSucceed() {
        Exchange exchange = template.request(
                "graphql://http://localhost:" + localServer.getLocalPort()
                                             + "/graphql?query={books{id name}}"
                                             + "&accessToken=" + accessToken,
                exchange1 -> {
                });

        assertExchange(exchange);
    }

    @Override
    protected HttpProcessor getBasicHttpProcessor() {
        List<HttpRequestInterceptor> requestInterceptors
                = Collections.singletonList(new RequestValidateHost());
        List<HttpResponseInterceptor> responseInterceptors
                = Collections.singletonList(new ResponseContent());

        return new DefaultHttpProcessor(requestInterceptors, responseInterceptors);
    }
}

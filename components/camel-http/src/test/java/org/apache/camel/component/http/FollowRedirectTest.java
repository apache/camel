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

import org.apache.camel.Exchange;
import org.apache.camel.component.http.handler.BasicValidationHandler;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.http.HttpMethods.GET;

public class FollowRedirectTest extends BaseHttpTest {

    private HttpServer localServer;

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        localServer = ServerBootstrap.bootstrap().setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy()).setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .register("/someplaceelse", new BasicValidationHandler(GET.name(), null, null, "Bye World"))
                .register("/redirect", (request, response, context) -> {
                    response.setHeader("Location", "someplaceelse");
                    response.setCode(303);
                }).create();
        localServer.start();

        super.setUp();
    }

    @Test
    public void testFollowRedirect() {
        HttpComponent http = context.getComponent("http", HttpComponent.class);
        http.setFollowRedirects(true);

        String uri = "http://localhost:" + localServer.getLocalPort() + "/redirect";
        Exchange out = fluentTemplate.to(uri).send();

        Assertions.assertEquals(200, out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        String body = out.getMessage().getBody(String.class);
        Assertions.assertEquals("Bye World", body);
    }

    @Test
    public void testFollowRedirectDisabled() {
        HttpComponent http = context.getComponent("http", HttpComponent.class);
        http.setFollowRedirects(false);

        String uri = "http://localhost:" + localServer.getLocalPort()
                     + "/redirect?throwExceptionOnFailure=false";
        Exchange out = fluentTemplate.to(uri).send();

        Assertions.assertEquals(303, out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    public void testFollowRedirectHandlingDisabled() {
        HttpComponent http = context.getComponent("http", HttpComponent.class);
        http.setRedirectHandlingDisabled(false);

        String uri = "http://localhost:" + localServer.getLocalPort()
                     + "/redirect?throwExceptionOnFailure=false";
        Exchange out = fluentTemplate.to(uri).send();

        Assertions.assertEquals(303, out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

}

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

import static org.apache.camel.http.common.HttpMethods.GET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.handler.BasicValidationHandler;
import org.apache.camel.util.IOHelper;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.junit.jupiter.api.Test;

public class HttpDisableStreamCacheTest extends BaseHttpTest {

    private HttpServer localServer;

    @Override
    public void setupResources() throws Exception {
        localServer = ServerBootstrap.bootstrap()
                .setCanonicalHostName("localhost")
                .setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy())
                .setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .register("/test/", new BasicValidationHandler(GET.name(), null, null, getExpectedContent()))
                .create();
        localServer.start();
    }

    @Override
    public void cleanupResources() throws Exception {
        if (localServer != null) {
            localServer.stop();
        }
    }

    @Test
    public void httpDisableStreamCache() {
        Object out = template.requestBody("direct:start", (String) null);
        assertEquals("camel rocks!", context.getTypeConverter().convertTo(String.class, out));
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .streamCache("false")
                        .to("http://localhost:" + localServer.getLocalPort() + "/test/?disableStreamCache=true")
                        .process(e -> {
                            InputStream is = (InputStream) e.getMessage().getBody();
                            assertNotNull(is);

                            // we can only read the raw http stream once
                            ByteArrayOutputStream bos = new ByteArrayOutputStream();
                            IOHelper.copy(is, bos);

                            e.setVariable("newBody", bos.toString());
                        })
                        .setBody()
                        .variable("newBody");
            }
        };
    }
}

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

import java.io.File;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.handler.BasicValidationHandler;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.StreamCachingStrategy;
import org.apache.camel.util.FileUtil;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.junit.jupiter.api.Test;

public class HttpStreamCacheSpoolToDiskTest extends BaseHttpTest {

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

        FileUtil.removeDir(new File("target/camel-cache"));
    }

    @Override
    public void cleanupResources() throws Exception {
        if (localServer != null) {
            localServer.stop();
        }
    }

    @Test
    public void httpSpoolToDisk() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("camel rocks!");
        result.expectedFileExists("target/output/rock.txt");

        template.requestBody("direct:start", (String) null);

        result.assertIsSatisfied();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                StreamCachingStrategy streamCachingStrategy = context.getStreamCachingStrategy();
                streamCachingStrategy.setSpoolDirectory("target/camel-cache");
                streamCachingStrategy.setSpoolThreshold(1); // spool to disk
                streamCachingStrategy.setSpoolEnabled(true);
                streamCachingStrategy.setBufferSize(4096);

                from("direct:start")
                        .streamCache("true")
                        .to("http://localhost:" + localServer.getLocalPort() + "/test/?disableStreamCache=true")
                        .process(e -> {
                            // should be temp spool file
                            int files = new File("target/camel-cache").list().length;
                            assertEquals(1, files);
                        })
                        .to("file:target/output?fileName=rock.txt")
                        .to("mock:result");
            }
        };
    }
}

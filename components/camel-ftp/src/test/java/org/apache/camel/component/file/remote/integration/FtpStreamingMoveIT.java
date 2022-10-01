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
package org.apache.camel.component.file.remote.integration;

import java.io.File;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FtpStreamingMoveIT extends FtpServerTestSupport {

    private String getFtpUrl() {
        return "ftp://admin@localhost:{{ftp.server.port}}"
               + "/mymove?password=admin&delay=1000&streamDownload=true&move=done&stepwise=false";
    }

    @Test
    public void testStreamDownloadMove() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        sendFile(getFtpUrl(), "Hello World", "hello.txt");

        context.getRouteController().startRoute("foo");

        MockEndpoint.assertIsSatisfied(context);

        InputStream is = mock.getExchanges().get(0).getIn().getBody(InputStream.class);
        assertNotNull(is);

        // give time for consumer to rename file
        File file = service.ftpFile("mymove/done/hello.txt").toFile();
        await().atMost(1, TimeUnit.SECONDS)
                .untilAsserted(() -> assertTrue(file.exists(), "File should have been renamed"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(getFtpUrl()).routeId("foo").noAutoStartup().to("mock:result");
            }
        };
    }
}

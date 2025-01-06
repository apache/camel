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

import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class FtpStreamDownloadStreamCacheIT extends FtpServerTestSupport {

    private String getFtpUrl() {
        return "ftp://localhost:{{ftp.server.port}}/stream?username=admin&password=admin&stepwise=false&streamDownload=true";
    }

    @Override
    public void doPostSetup() throws Exception {
        prepareFtpServer();
    }

    private void prepareFtpServer() {
        template.sendBodyAndHeader(getFtpUrl(), "World", Exchange.FILE_NAME,
                "world.txt");
    }

    @Test
    public void testStreamDownload() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:received_send");
        mock.message(0).body().isInstanceOf(StreamCache.class);
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("World");

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(getFtpUrl())
                        .to("mock:received_send");
            }
        };
    }
}

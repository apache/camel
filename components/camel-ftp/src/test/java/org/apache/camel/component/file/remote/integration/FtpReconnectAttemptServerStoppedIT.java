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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class FtpReconnectAttemptServerStoppedIT extends FtpServerTestSupport {

    private String getFtpUrl() {
        return "ftp://admin@localhost:{{ftp.server.port}}"
               + "/reconnect?password=admin&maximumReconnectAttempts=5&reconnectDelay=1000&delete=true";
    }

    @Test
    @Timeout(15)
    public void testFromFileToFtp() throws Exception {
        // disconnect all the connections so that they have to reconnect again
        service.disconnectAllSessions();

        Awaitility.await().until(() -> service.countConnections() == 0);

        // put a file in the folder (do not use ftp as we then will connect)
        template.sendBodyAndHeader("file:{{ftp.root.dir}}/reconnect", "Hello World", Exchange.FILE_NAME,
                "hello.txt");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        MockEndpoint.assertIsSatisfied(context);

        mock.reset();
        mock.expectedMessageCount(1);

        // resume the server so we can connect
        service.resume();

        MockEndpoint.assertIsSatisfied(context);
        Awaitility.await().untilAsserted(() -> Assertions.assertEquals(1, service.countConnections()));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(getFtpUrl()).to("mock:result");
            }
        };
    }
}

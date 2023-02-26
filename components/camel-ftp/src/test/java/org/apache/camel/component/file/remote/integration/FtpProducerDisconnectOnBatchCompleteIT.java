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

import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.file.remote.FtpEndpoint;
import org.apache.camel.model.language.SimpleExpression;
import org.apache.commons.net.ftp.FTPClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FtpProducerDisconnectOnBatchCompleteIT extends FtpServerTestSupport {

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();

        // ask the singleton FtpEndpoint to make use of a custom FTPClient
        // so that we can hold a reference on it inside the test below
        FtpEndpoint<?> endpoint = context.getEndpoint(getFtpUrl(), FtpEndpoint.class);
        endpoint.setFtpClient(new FTPClient());
    }

    private String getFtpUrl() {
        return "ftp://admin@localhost:{{ftp.server.port}}/done?password=admin&disconnectOnBatchComplete=true";
    }

    @Test
    public void testDisconnectOnBatchComplete() {
        sendFile(getFtpUrl(), "Hello World", "claus.txt");

        FtpEndpoint<?> endpoint = context.getEndpoint(getFtpUrl(), FtpEndpoint.class);
        await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> assertFalse(endpoint.getFtpClient().isConnected(),
                        "The FTPClient should be already disconnected"));
        assertTrue(endpoint.isDisconnectOnBatchComplete(), "The FtpEndpoint should be configured to disconnect");
    }

    @Override
    public void sendFile(String url, Object body, String fileName) {
        template.send(url, new Processor() {

            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Exchange.FILE_NAME, new SimpleExpression(fileName));
                exchange.setProperty(Exchange.BATCH_COMPLETE, true);
            }
        });
    }

}

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
package org.apache.camel.component.file.remote;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.commons.net.ftp.FTPClient;
import org.junit.Test;

import static org.apache.camel.language.simple.SimpleLanguage.simple;

public class FtpProducerDisconnectOnBatchCompleteTest extends FtpServerTestSupport {

    @Override
    public void setUp() throws Exception {
        super.setUp();

        // ask the singleton FtpEndpoint to make use of a custom FTPClient
        // so that we can hold a reference on it inside the test below
        FtpEndpoint<?> endpoint = context.getEndpoint(getFtpUrl(), FtpEndpoint.class);
        endpoint.setFtpClient(new FTPClient());
    }

    private String getFtpUrl() {
        return "ftp://admin@localhost:" + getPort() + "/done?password=admin&disconnectOnBatchComplete=true";
    }

    @Test
    public void testDisconnectOnBatchComplete() throws Exception {
        sendFile(getFtpUrl(), "Hello World", "claus.txt");

        Thread.sleep(2000);
        FtpEndpoint<?> endpoint = context.getEndpoint(getFtpUrl(), FtpEndpoint.class);
        assertFalse("The FTPClient should be already disconnected", endpoint.getFtpClient().isConnected());
        assertTrue("The FtpEndpoint should be configured to disconnect", endpoint.isDisconnectOnBatchComplete());
    }
    
    @Override
    public void sendFile(String url, Object body, String fileName) {
        template.send(url, new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Exchange.FILE_NAME, simple(fileName));
                exchange.setProperty(Exchange.BATCH_COMPLETE, true);
            }
        });
    }

}
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
import org.apache.camel.PollingConsumer;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultUnitOfWork;
import org.apache.camel.spi.UnitOfWork;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test that ftp consumer will exclude pre and postfixes
 */
public class FtpPollingConsumerTest extends FtpServerTestSupport {

    private String getFtpUrl() {
        return "ftp://admin@localhost:" + getPort() + "/polling?password=admin&move=done";
    }

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory(FTP_ROOT_DIR + "polling");
        super.setUp();
        prepareFtpServer();
    }

    @Test
    public void testPollingConsumer() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived(5);
        result.expectedFileExists(FTP_ROOT_DIR + "polling/done/1.txt");
        result.expectedFileExists(FTP_ROOT_DIR + "polling/done/2.txt");
        result.expectedFileExists(FTP_ROOT_DIR + "polling/done/3.txt");
        result.expectedFileExists(FTP_ROOT_DIR + "polling/done/4.txt");
        result.expectedFileExists(FTP_ROOT_DIR + "polling/done/5.txt");

        PollingConsumer consumer = context.getEndpoint(getFtpUrl()).createPollingConsumer();
        consumer.start();

        boolean done = false;
        while (!done) {
            Exchange exchange = consumer.receive(5000);
            if (exchange == null) {
                done = true;
                break;
            }

            String body = exchange.getIn().getBody(String.class);
            template.sendBody("mock:result", body);

            // must done to move the files
            if (exchange.getUnitOfWork() == null) {
                UnitOfWork uow = new DefaultUnitOfWork(exchange);
                exchange.setUnitOfWork(uow);
            }
            exchange.getUnitOfWork().done(exchange);
        }

        consumer.stop();

        assertMockEndpointsSatisfied();
    }

    private void prepareFtpServer() throws Exception {
        sendFile(getFtpUrl(), "Message 1", "1.txt");
        sendFile(getFtpUrl(), "Message 2", "2.txt");
        sendFile(getFtpUrl(), "Message 3", "3.txt");
        sendFile(getFtpUrl(), "Message 4", "4.txt");
        sendFile(getFtpUrl(), "Message 5", "5.txt");
    }

}
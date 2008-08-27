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

import java.io.File;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.FileComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.IOConverter;

/**
 * Unit test to verify that using option setNames and having multi remote directories the files
 * are stored locally in the same directory layout.
 */
public class FromFtpSetNamesWithMultiDirectoriesTest extends FtpServerTestSupport {

    private int port = 20016;

    // must user "consumer." prefix on the parameters to the file component
    private String ftpUrl = "ftp://admin@localhost:" + port + "/incoming?password=admin&binary=true"
        + "&consumer.delay=2000&consumer.recursive=true&consumer.setNames=true";

    public void testFtpRoute() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedMessageCount(2);
        resultEndpoint.assertIsSatisfied();
        Exchange ex = resultEndpoint.getExchanges().get(0);
        byte[] bytes = ex.getIn().getBody(byte[].class);
        assertTrue("Logo size wrong", bytes.length > 10000);

        // wait until the file producer has written the file
        Thread.sleep(1000);

        // assert the file
        File file = new File("target/ftpsetnamestest/data1/logo1.jpeg");
        assertTrue("The binary file should exists", file.exists());
        assertTrue("Logo size wrong", file.length() > 10000);

        // assert the file
        file = new File("target/ftpsetnamestest/data2/logo2.png");
        assertTrue(" The binary file should exists", file.exists());
        assertTrue("Logo size wrong", file.length() > 50000);

        // let some time pass to let the consumer etc. properly do its business before closing
        Thread.sleep(1000);
    }

    public int getPort() {
        return port;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        prepareFtpServer();
    }

    private void prepareFtpServer() throws Exception {
        // prepares the FTP Server by creating a file on the server that we want to unit
        // test that we can pool and store as a local file
        String ftpUrl = "ftp://admin@localhost:" + port + "/incoming/data1/?password=admin&binary=true";
        Endpoint endpoint = context.getEndpoint(ftpUrl);
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody(IOConverter.toFile("src/test/data/ftpbinarytest/logo1.jpeg"));
        exchange.getIn().setHeader(FileComponent.HEADER_FILE_NAME, "logo1.jpeg");
        Producer producer = endpoint.createProducer();
        producer.start();
        producer.process(exchange);
        producer.stop();

        ftpUrl = "ftp://admin@localhost:" + port + "/incoming/data2/?password=admin&binary=true";
        endpoint = context.getEndpoint(ftpUrl);
        exchange = endpoint.createExchange();
        exchange.getIn().setBody(IOConverter.toFile("src/test/data/ftpbinarytest/logo2.png"));
        exchange.getIn().setHeader(FileComponent.HEADER_FILE_NAME, "logo2.png");
        producer = endpoint.createProducer();
        producer.start();
        producer.process(exchange);
        producer.stop();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                String fileUrl = "file:target/ftpsetnamestest/?noop=true";
                from(ftpUrl).to(fileUrl, "mock:result");
            }
        };
    }

}

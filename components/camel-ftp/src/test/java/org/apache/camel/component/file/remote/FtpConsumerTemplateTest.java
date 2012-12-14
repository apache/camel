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
import org.junit.Before;
import org.junit.Test;

public class FtpConsumerTemplateTest extends FtpServerTestSupport {

    protected String getFtpUrl() {
        return "ftp://admin@localhost:" + getPort() + "/template?password=admin";
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        prepareFtpServer();
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testConsumerTemplate() throws Exception {
        Exchange exchange = consumer.receive(getFtpUrl(), 5000);
        assertNotNull(exchange);
        assertEquals("hello.txt", exchange.getIn().getHeader(Exchange.FILE_NAME));
        assertEquals("Hello World", exchange.getIn().getBody(String.class));

        // must done when we are done using the exchange
        consumer.doneUoW(exchange);

        Thread.sleep(500);

        // poll the same file again
        exchange = consumer.receive(getFtpUrl(), 5000);
        assertNotNull(exchange);
        assertEquals("hello.txt", exchange.getIn().getHeader(Exchange.FILE_NAME));
        assertEquals("Hello World", exchange.getIn().getBody(String.class));

        // must done when we are done using the exchange
        consumer.doneUoW(exchange);

        // file should still exists
        Thread.sleep(500);
        File file = new File(FTP_ROOT_DIR + "/template/hello.txt");
        assertTrue("The file should exist: " + file, file.exists());
    }

    @Test
    public void testConsumerTemplateNotDone() throws Exception {
        Exchange exchange = consumer.receive(getFtpUrl(), 5000);
        assertNotNull(exchange);
        assertEquals("hello.txt", exchange.getIn().getHeader(Exchange.FILE_NAME));
        assertEquals("Hello World", exchange.getIn().getBody(String.class));

        // forget to call done

        Thread.sleep(500);

        // try poll the same file again
        Exchange exchange2 = consumer.receive(getFtpUrl(), 2000);
        assertNull(exchange2);

        // now done the original exchange
        consumer.doneUoW(exchange);

        // now we can poll the file again as we have done the exchange
        exchange2 = consumer.receive(getFtpUrl(), 2000);
        assertNotNull(exchange2);
        assertEquals("hello.txt", exchange2.getIn().getHeader(Exchange.FILE_NAME));
        assertEquals("Hello World", exchange2.getIn().getBody(String.class));
        consumer.doneUoW(exchange2);

        // file should still exists
        Thread.sleep(500);
        File file = new File(FTP_ROOT_DIR + "/template/hello.txt");
        assertTrue("The file should exist: " + file, file.exists());
    }

    private void prepareFtpServer() throws Exception {
        Endpoint endpoint = context.getEndpoint(getFtpUrl());
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody("Hello World");
        exchange.getIn().setHeader(Exchange.FILE_NAME, "hello.txt");
        Producer producer = endpoint.createProducer();
        producer.start();
        producer.process(exchange);
        producer.stop();

        // assert file is created
        File file = new File(FTP_ROOT_DIR + "/template/hello.txt");
        assertTrue("The file should exist: " + file, file.exists());
    }

}
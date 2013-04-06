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
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.util.FileUtil;
import org.junit.Before;
import org.junit.Test;

/**
 * @version 
 */
public class FtpConsumerLocalWorkDirectoryAsAbsolutePathTest extends FtpServerTestSupport {

    private String base;

    protected String getFtpUrl() {
        base = new File("target/lwd").getAbsolutePath();
        return "ftp://admin@localhost:" + getPort() + "/lwd/?password=admin&delay=5000&noop=true&localWorkDirectory=" + base;
    }

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/lwd");
        deleteDirectory("target/out");
        super.setUp();
        prepareFtpServer();
    }

    @Test
    public void testLocalWorkDirectory() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.expectedMessageCount(1);

        assertMockEndpointsSatisfied();

        // give test some time to close file resources
        Thread.sleep(6000);

        // now the lwd file should be deleted
        File local = new File("target/lwd/hello.txt");
        assertFalse("Local work file should have been deleted", local.exists());

        // and the out file should exists
        File out = new File("target/out/hello.txt");
        assertTrue("file should exists", out.exists());
        assertEquals("Hello World", IOConverter.toString(out, null));
    }

    private void prepareFtpServer() throws Exception {
        // prepares the FTP Server by creating a file on the server that we want to unit
        // test that we can pool
        Endpoint endpoint = context.getEndpoint(getFtpUrl());
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody("Hello World");
        exchange.getIn().setHeader(Exchange.FILE_NAME, "hello.txt");
        Producer producer = endpoint.createProducer();
        producer.start();
        producer.process(exchange);
        producer.stop();
    }
    
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(getFtpUrl()).process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        File body = exchange.getIn().getBody(File.class);
                        assertNotNull(body);
                        assertTrue("Should be absolute path", body.isAbsolute());
                        assertTrue("Local work file should exists", body.exists());
                        assertEquals(FileUtil.normalizePath(base + "/hello.txt"), body.getPath());
                    }
                }).to("mock:result", "file://target/out");
            }
        };
    }
}
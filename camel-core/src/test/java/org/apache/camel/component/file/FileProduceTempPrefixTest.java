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
package org.apache.camel.component.file;

import java.io.File;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;

/**
 * Unit test for file producer option tempPrefix
 */
public class FileProduceTempPrefixTest extends ContextTestSupport {

    private String fileUrl = "file://target/tempandrename/?tempPrefix=inprogress.";

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/tempandrename");
        super.setUp();
    }

    public void testCreateTempFileName() throws Exception {
        Endpoint endpoint = context.getEndpoint(fileUrl);
        GenericFileProducer<?> producer = (GenericFileProducer<?>) endpoint.createProducer();
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setHeader(Exchange.FILE_NAME, "claus.txt");

        String tempFileName = producer.createTempFileName(exchange, "target/tempandrename/claus.txt");
        assertDirectoryEquals("target/tempandrename/inprogress.claus.txt", tempFileName);
    }

    public void testCreateTempFileNameUsingComplexName() throws Exception {
        Endpoint endpoint = context.getEndpoint(fileUrl);
        GenericFileProducer<?> producer = (GenericFileProducer<?>) endpoint.createProducer();
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setHeader(Exchange.FILE_NAME, "foo/claus.txt");

        String tempFileName = producer.createTempFileName(exchange, "target/tempandrename/foo/claus.txt");
        assertDirectoryEquals("target/tempandrename/foo/inprogress.claus.txt", tempFileName);
    }

    public void testNoPathCreateTempFileName() throws Exception {
        Endpoint endpoint = context.getEndpoint(fileUrl);
        GenericFileProducer<?> producer = (GenericFileProducer<?>) endpoint.createProducer();
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setHeader(Exchange.FILE_NAME, "claus.txt");

        String tempFileName = producer.createTempFileName(exchange, ".");
        assertDirectoryEquals("inprogress.claus.txt", tempFileName);
    }

    public void testTempPrefix() throws Exception {
        template.sendBodyAndHeader("direct:a", "Hello World", Exchange.FILE_NAME, "hello.txt");

        File file = new File("target/tempandrename/hello.txt");
        assertEquals("The generated file should exists: " + file, true, file.exists());
    }

    public void testTempPrefixUUIDFilename() throws Exception {
        template.sendBody("direct:a", "Bye World");
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:a").to(fileUrl);
            }
        };
    }
}
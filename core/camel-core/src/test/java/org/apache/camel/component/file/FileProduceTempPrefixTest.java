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
package org.apache.camel.component.file;

import java.io.File;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test for file producer option tempPrefix
 */
public class FileProduceTempPrefixTest extends ContextTestSupport {

    private String fileUrl = "file://target/data/tempandrename/?tempPrefix=inprogress.";

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        deleteDirectory("target/data/tempandrename");
        super.setUp();
    }

    @Test
    public void testCreateTempFileName() throws Exception {
        Endpoint endpoint = context.getEndpoint(fileUrl);
        GenericFileProducer<?> producer = (GenericFileProducer<?>) endpoint.createProducer();
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setHeader(Exchange.FILE_NAME, "claus.txt");

        String tempFileName = producer.createTempFileName(exchange, "target/data/tempandrename/claus.txt");
        assertDirectoryEquals("target/data/tempandrename/inprogress.claus.txt", tempFileName);
    }

    @Test
    public void testCreateTempFileNameUsingComplexName() throws Exception {
        Endpoint endpoint = context.getEndpoint(fileUrl);
        GenericFileProducer<?> producer = (GenericFileProducer<?>) endpoint.createProducer();
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setHeader(Exchange.FILE_NAME, "foo/claus.txt");

        String tempFileName = producer.createTempFileName(exchange, "target/data/tempandrename/foo/claus.txt");
        assertDirectoryEquals("target/data/tempandrename/foo/inprogress.claus.txt", tempFileName);
    }

    @Test
    public void testNoPathCreateTempFileName() throws Exception {
        Endpoint endpoint = context.getEndpoint(fileUrl);
        GenericFileProducer<?> producer = (GenericFileProducer<?>) endpoint.createProducer();
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setHeader(Exchange.FILE_NAME, "claus.txt");

        String tempFileName = producer.createTempFileName(exchange, ".");
        assertDirectoryEquals("inprogress.claus.txt", tempFileName);
    }

    @Test
    public void testTempPrefix() throws Exception {
        template.sendBodyAndHeader("direct:a", "Hello World", Exchange.FILE_NAME, "hello.txt");

        File file = new File("target/data/tempandrename/hello.txt");
        assertEquals(true, file.exists(), "The generated file should exists: " + file);
    }

    @Test
    public void testTempPrefixUUIDFilename() throws Exception {
        template.sendBody("direct:a", "Bye World");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:a").to(fileUrl);
            }
        };
    }
}

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
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for file producer option tempPrefix
 */
public class FileProduceTempFileNameTest extends ContextTestSupport {

    private String fileUrl = "file://target/data/tempandrename/?tempFileName=inprogress-${file:name.noext}.tmp";
    private String parentFileUrl = "file://target/data/tempandrename/?tempFileName=../work/${file:name.noext}.tmp";
    private String childFileUrl = "file://target/data/tempandrename/?tempFileName=work/${file:name.noext}.tmp";

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/data/tempandrename");
        deleteDirectory("target/data/work");
        super.setUp();
    }

    @Test
    public void testCreateTempFileName() throws Exception {
        Endpoint endpoint = context.getEndpoint(fileUrl);
        GenericFileProducer<?> producer = (GenericFileProducer<?>)endpoint.createProducer();
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setHeader(Exchange.FILE_NAME, "claus.txt");

        String tempFileName = producer.createTempFileName(exchange, "target/data/tempandrename/claus.txt");
        assertDirectoryEquals("target/data/tempandrename/inprogress-claus.tmp", tempFileName);
    }

    @Test
    public void testNoPathCreateTempFileName() throws Exception {
        Endpoint endpoint = context.getEndpoint(fileUrl);
        GenericFileProducer<?> producer = (GenericFileProducer<?>)endpoint.createProducer();
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setHeader(Exchange.FILE_NAME, "claus.txt");

        String tempFileName = producer.createTempFileName(exchange, ".");
        assertDirectoryEquals("inprogress-claus.tmp", tempFileName);
    }

    @Test
    public void testTempFileName() throws Exception {
        template.sendBodyAndHeader("direct:a", "Hello World", Exchange.FILE_NAME, "hello.txt");

        File file = new File("target/data/tempandrename/hello.txt");
        assertEquals("The generated file should exist: " + file, true, file.exists());
    }

    @Test
    public void testParentTempFileName() throws Exception {
        template.sendBodyAndHeader("direct:b", "Hello World", Exchange.FILE_NAME, "hello.txt");

        File file = new File("target/data/work");
        assertEquals("The generated temp directory should exist: " + file, true, file.exists());
    }

    @Test
    public void testChildTempFileName() throws Exception {
        template.sendBodyAndHeader("direct:c", "Hello World", Exchange.FILE_NAME, "hello.txt");

        File file = new File("target/data/tempandrename/work");
        assertEquals("The generated temp directory should exist: " + file, true, file.exists());
    }

    @Test
    public void testCreateParentTempFileName() throws Exception {
        Endpoint endpoint = context.getEndpoint(parentFileUrl);
        GenericFileProducer<?> producer = (GenericFileProducer<?>)endpoint.createProducer();
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setHeader(Exchange.FILE_NAME, "claus.txt");

        String tempFileName = producer.createTempFileName(exchange, "target/data/tempandrename/claus.txt");
        assertDirectoryEquals("target/data/work/claus.tmp", tempFileName);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:a").to(fileUrl);
                from("direct:b").to(parentFileUrl);
                from("direct:c").to(childFileUrl);
            }
        };
    }
}

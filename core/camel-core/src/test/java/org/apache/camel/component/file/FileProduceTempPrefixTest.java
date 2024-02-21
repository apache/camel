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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

/**
 * Unit test for file producer option tempPrefix
 */
public class FileProduceTempPrefixTest extends ContextTestSupport {

    public static final String FILE_QUERY = "?tempPrefix=inprogress.";

    @Test
    public void testCreateTempFileName() throws Exception {
        Endpoint endpoint = context.getEndpoint(fileUri(FILE_QUERY));
        GenericFileProducer<?> producer = (GenericFileProducer<?>) endpoint.createProducer();
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setHeader(Exchange.FILE_NAME, "claus.txt");

        String tempFileName = producer.createTempFileName(exchange, testFile("claus.txt").toString());
        assertDirectoryEquals(testFile("inprogress.claus.txt").toString(), tempFileName);
    }

    @Test
    public void testCreateTempFileNameUsingComplexName() throws Exception {
        Endpoint endpoint = context.getEndpoint(fileUri(FILE_QUERY));
        GenericFileProducer<?> producer = (GenericFileProducer<?>) endpoint.createProducer();
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setHeader(Exchange.FILE_NAME, "foo/claus.txt");

        String tempFileName = producer.createTempFileName(exchange, testFile("foo/claus.txt").toString());
        assertDirectoryEquals(testFile("foo/inprogress.claus.txt").toString(), tempFileName);
    }

    @Test
    public void testNoPathCreateTempFileName() throws Exception {
        Endpoint endpoint = context.getEndpoint(fileUri(FILE_QUERY));
        GenericFileProducer<?> producer = (GenericFileProducer<?>) endpoint.createProducer();
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setHeader(Exchange.FILE_NAME, "claus.txt");

        String tempFileName = producer.createTempFileName(exchange, ".");
        assertDirectoryEquals("inprogress.claus.txt", tempFileName);
    }

    @Test
    public void testTempPrefix() throws Exception {
        template.sendBodyAndHeader("direct:a", "Hello World", Exchange.FILE_NAME, "hello.txt");

        assertFileExists(testFile("hello.txt"));
    }

    @Test
    public void testTempPrefixUUIDFilename() throws Exception {
        template.sendBody("direct:a", "Bye World");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:a").to(fileUri(FILE_QUERY));
            }
        };
    }
}

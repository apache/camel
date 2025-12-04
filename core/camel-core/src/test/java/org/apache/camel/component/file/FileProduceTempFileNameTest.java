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

import java.util.UUID;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

/**
 * Unit test for file producer option tempPrefix
 */
public class FileProduceTempFileNameTest extends ContextTestSupport {

    private static final String TEST_FILE_NAME = "hello" + UUID.randomUUID() + ".txt";
    private static final String TEST_FILE_NAME_CLAUS = "claus" + UUID.randomUUID(); // noext

    public static final String FILE_URL_QUERY = "tempandrename?tempFileName=inprogress-${file:name.noext}.tmp";
    public static final String PARENT_FILE_URL_QUERY = "tempandrename?tempFileName=../work/${file:name.noext}.tmp";
    public static final String CHILD_FILE_URL_QUERY = "tempandrename?tempFileName=work/${file:name.noext}.tmp";

    @Test
    public void testCreateTempFileName() throws Exception {
        Endpoint endpoint = context.getEndpoint(fileUri(FILE_URL_QUERY));
        GenericFileProducer<?> producer = (GenericFileProducer<?>) endpoint.createProducer();
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setHeader(Exchange.FILE_NAME, TEST_FILE_NAME_CLAUS + ".txt");

        String tempFileName = producer.createTempFileName(
                exchange, testFile(TEST_FILE_NAME_CLAUS + ".txt").toString());
        assertDirectoryEquals(
                testFile("inprogress-" + TEST_FILE_NAME_CLAUS + ".tmp").toString(), tempFileName);
    }

    @Test
    public void testNoPathCreateTempFileName() throws Exception {
        Endpoint endpoint = context.getEndpoint(fileUri(FILE_URL_QUERY));
        GenericFileProducer<?> producer = (GenericFileProducer<?>) endpoint.createProducer();
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setHeader(Exchange.FILE_NAME, TEST_FILE_NAME_CLAUS + ".txt");

        String tempFileName = producer.createTempFileName(exchange, ".");
        assertDirectoryEquals("inprogress-" + TEST_FILE_NAME_CLAUS + ".tmp", tempFileName);
    }

    @Test
    public void testTempFileName() {
        template.sendBodyAndHeader("direct:a", "Hello World", Exchange.FILE_NAME, TEST_FILE_NAME);

        assertFileExists(testFile("tempandrename/" + TEST_FILE_NAME));
    }

    @Test
    public void testParentTempFileName() {
        template.sendBodyAndHeader("direct:b", "Hello World", Exchange.FILE_NAME, TEST_FILE_NAME);

        assertDirectoryExists(testDirectory("work"));
    }

    @Test
    public void testChildTempFileName() {
        template.sendBodyAndHeader("direct:c", "Hello World", Exchange.FILE_NAME, TEST_FILE_NAME);

        assertDirectoryExists(testDirectory("tempandrename/work"));
    }

    @Test
    public void testCreateParentTempFileName() throws Exception {
        Endpoint endpoint = context.getEndpoint(fileUri(PARENT_FILE_URL_QUERY));
        GenericFileProducer<?> producer = (GenericFileProducer<?>) endpoint.createProducer();
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setHeader(Exchange.FILE_NAME, TEST_FILE_NAME_CLAUS + ".txt");

        String tempFileName = producer.createTempFileName(
                exchange,
                testFile("tempandrename/" + TEST_FILE_NAME_CLAUS + ".txt").toString());
        assertDirectoryEquals(
                testDirectory("work/" + TEST_FILE_NAME_CLAUS + ".tmp").toString(), tempFileName);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:a").to(fileUri(FILE_URL_QUERY));
                from("direct:b").to(fileUri(PARENT_FILE_URL_QUERY));
                from("direct:c").to(fileUri(CHILD_FILE_URL_QUERY));
            }
        };
    }
}

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
public class FileProduceTempPrefixTest extends ContextTestSupport {
    private static final String TEST_FILE_NAME_1 = "hello" + UUID.randomUUID() + ".txt";
    private static final String TEST_FILE_NAME_2 = "claus" + UUID.randomUUID() + ".txt";

    public static final String FILE_QUERY = "?tempPrefix=inprogress.";

    @Test
    public void testCreateTempFileName() throws Exception {
        Endpoint endpoint = context.getEndpoint(fileUri(FILE_QUERY));
        GenericFileProducer<?> producer = (GenericFileProducer<?>) endpoint.createProducer();
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setHeader(Exchange.FILE_NAME, TEST_FILE_NAME_2);

        String tempFileName = producer.createTempFileName(exchange, testFile(TEST_FILE_NAME_2).toString());
        assertDirectoryEquals(testFile("inprogress." + TEST_FILE_NAME_2).toString(), tempFileName);
    }

    @Test
    public void testCreateTempFileNameUsingComplexName() throws Exception {
        Endpoint endpoint = context.getEndpoint(fileUri(FILE_QUERY));
        GenericFileProducer<?> producer = (GenericFileProducer<?>) endpoint.createProducer();
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setHeader(Exchange.FILE_NAME, "foo/" + TEST_FILE_NAME_2);

        String tempFileName = producer.createTempFileName(exchange, testFile("foo/" + TEST_FILE_NAME_2).toString());
        assertDirectoryEquals(testFile("foo/inprogress." + TEST_FILE_NAME_2).toString(), tempFileName);
    }

    @Test
    public void testNoPathCreateTempFileName() throws Exception {
        Endpoint endpoint = context.getEndpoint(fileUri(FILE_QUERY));
        GenericFileProducer<?> producer = (GenericFileProducer<?>) endpoint.createProducer();
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setHeader(Exchange.FILE_NAME, TEST_FILE_NAME_2);

        String tempFileName = producer.createTempFileName(exchange, ".");
        assertDirectoryEquals("inprogress." + TEST_FILE_NAME_2, tempFileName);
    }

    @Test
    public void testTempPrefix() {
        template.sendBodyAndHeader("direct:a", "Hello World", Exchange.FILE_NAME, TEST_FILE_NAME_1);

        assertFileExists(testFile(TEST_FILE_NAME_1));
    }

    @Test
    public void testTempPrefixUUIDFilename() {
        template.sendBody("direct:a", "Bye World");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:a").to(fileUri(FILE_QUERY));
            }
        };
    }
}

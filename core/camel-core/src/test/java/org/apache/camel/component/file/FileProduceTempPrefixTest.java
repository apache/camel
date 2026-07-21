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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit test for file producer option tempPrefix
 */
class FileProduceTempPrefixTest extends ContextTestSupport {
    private static final String TEST_FILE_NAME_1 = "hello" + UUID.randomUUID() + ".txt";
    private static final String TEST_FILE_NAME_2 = "claus" + UUID.randomUUID() + ".txt";

    public static final String FILE_QUERY = "?tempPrefix=inprogress.";

    @Test
    void testCreateTempFileName() throws Exception {
        Endpoint endpoint = context.getEndpoint(fileUri(FILE_QUERY));
        GenericFileProducer<?> producer = (GenericFileProducer<?>) endpoint.createProducer();
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setHeader(Exchange.FILE_NAME, TEST_FILE_NAME_2);

        String tempFileName = producer.createTempFileName(exchange, testFile(TEST_FILE_NAME_2).toString());
        assertDirectoryEquals(testFile("inprogress." + TEST_FILE_NAME_2).toString(), tempFileName);
    }

    @Test
    void testCreateTempFileNameUsingComplexName() throws Exception {
        Endpoint endpoint = context.getEndpoint(fileUri(FILE_QUERY));
        GenericFileProducer<?> producer = (GenericFileProducer<?>) endpoint.createProducer();
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setHeader(Exchange.FILE_NAME, "foo/" + TEST_FILE_NAME_2);

        String tempFileName = producer.createTempFileName(exchange, testFile("foo/" + TEST_FILE_NAME_2).toString());
        assertDirectoryEquals(testFile("foo/inprogress." + TEST_FILE_NAME_2).toString(), tempFileName);
    }

    @Test
    void testNoPathCreateTempFileName() throws Exception {
        Endpoint endpoint = context.getEndpoint(fileUri(FILE_QUERY));
        GenericFileProducer<?> producer = (GenericFileProducer<?>) endpoint.createProducer();
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setHeader(Exchange.FILE_NAME, TEST_FILE_NAME_2);

        String tempFileName = producer.createTempFileName(exchange, ".");
        assertDirectoryEquals("inprogress." + TEST_FILE_NAME_2, tempFileName);
    }

    @Test
    void testTempPrefix() {
        template.sendBodyAndHeader("direct:a", "Hello World", Exchange.FILE_NAME, TEST_FILE_NAME_1);

        assertFileExists(testFile(TEST_FILE_NAME_1));
    }

    @Test
    void testTempPrefixUUIDFilename() throws Exception {
        template.sendBody("direct:a", "Bye World");

        // When no FILE_NAME header is set, the producer creates a file with an auto-generated UUID name
        List<Path> files;
        try (Stream<Path> stream = Files.list(testDirectory())) {
            files = stream.toList();
        }
        assertNotNull(files, "Test directory should contain files");
        assertEquals(1, files.size(), "exactly one file should have been created");
        String content = Files.readString(files.get(0));
        assertEquals("Bye World", content, "File content should match the body that was sent");
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

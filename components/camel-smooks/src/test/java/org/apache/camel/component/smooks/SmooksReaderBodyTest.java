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
package org.apache.camel.component.smooks;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.transform.sax.SAXSource;

import org.xml.sax.InputSource;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.dataformat.smooks.Customer;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.smooks.Smooks;
import org.smooks.io.payload.Export;
import org.smooks.io.payload.Exports;
import org.smooks.io.sink.JavaSink;
import org.smooks.io.sink.StringSink;
import org.smooks.support.StreamUtils;
import org.xmlunit.builder.DiffBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Message bodies that are a {@link SAXSource}, a file or a remote file are read by the reader of the Smooks
 * configuration.
 */
class SmooksReaderBodyTest extends CamelTestSupport {

    @TempDir
    Path tempDir;

    private String orderEdi;
    private String expectedOrderXml;

    @BeforeEach
    void readOrder() throws Exception {
        try (InputStream edi = getClass().getResourceAsStream("/data/order.txt");
             InputStream xml = getClass().getResourceAsStream("/xml/expected-order.xml")) {
            orderEdi = new String(StreamUtils.readStream(edi), StandardCharsets.UTF_8);
            expectedOrderXml = new String(StreamUtils.readStream(xml), StandardCharsets.UTF_8);
        }
    }

    @Test
    void saxSourceWithCharacterStreamIsReadByTheConfiguredReader() {
        assertOrderXml(new SAXSource(new InputSource(new StringReader(orderEdi))));
    }

    @Test
    void saxSourceWithByteStreamIsReadByTheConfiguredReader() {
        assertOrderXml(new SAXSource(new InputSource(new ByteArrayInputStream(orderEdi.getBytes(StandardCharsets.UTF_8)))));
    }

    @Test
    void saxSourceWithSystemIdIsReadByTheConfiguredReader() throws Exception {
        assertOrderXml(new SAXSource(new InputSource(writeOrder().toUri().toString())));
    }

    @Test
    void saxSourceWithoutInputIsRejected() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("direct:edi", new SAXSource()));
        assertInstanceOf(InvalidPayloadException.class, e.getCause().getCause());
    }

    @Test
    void saxSourceIsParsedWithoutExternalEntitiesByTheDefaultReader() throws Exception {
        Path entityFile = Files.writeString(tempDir.resolve("first-name.txt"), "Text of the external entity");
        String xml = "<!DOCTYPE customer [<!ENTITY name SYSTEM \"" + entityFile.toUri() + "\">]>"
                     + "<customer><firstName>&name;</firstName><lastName>Smith</lastName></customer>";

        Customer customer = template.requestBody("direct:customer", new SAXSource(new InputSource(new StringReader(xml))),
                Customer.class);
        assertEquals("Smith", customer.getLastName());
        assertEquals("", customer.getFirstName(), "The external entity must not be read by default");
    }

    @Test
    void fileBodyIsReadByTheConfiguredReader() throws Exception {
        assertOrderXml(writeOrder().toFile());
    }

    @Test
    void pathBodyIsReadByTheConfiguredReader() throws Exception {
        assertOrderXml(writeOrder());
    }

    @Test
    void localFileBodyIsReadByTheConfiguredReader() throws Exception {
        GenericFile<File> localFile = new GenericFile<>();
        localFile.setFile(writeOrder().toFile());
        assertOrderXml(localFile);
    }

    @Test
    void remoteFileBodyIsReadByTheConfiguredReader() {
        // A remote file (ftp, sftp, ftps) holds the file of its client library and its downloaded content as body
        GenericFile<Object> remoteFile = new GenericFile<>();
        remoteFile.setFile(new Object());
        remoteFile.setFileName("order.txt");
        remoteFile.setBody(orderEdi.getBytes(StandardCharsets.UTF_8));
        assertOrderXml(remoteFile);
    }

    @Test
    void severalExportsAreReturnedInTheOrderOfTheirSinkType() {
        // Smooks creates the sinks in no fixed order, so send several times
        for (int i = 0; i < 20; i++) {
            List<?> body = template.requestBody("direct:severalExports", "<order><id>1</id></order>", List.class);
            assertEquals(2, body.size());
            assertInstanceOf(Map.class, body.get(0), "The result of the JavaSink comes first");
            assertEquals("<order><id>1</id></order>", body.get(1).toString(), "The result of the StringSink comes last");
        }
    }

    private Path writeOrder() throws Exception {
        return Files.writeString(tempDir.resolve("order.txt"), orderEdi);
    }

    private void assertOrderXml(Object body) {
        String xml = template.requestBody("direct:edi", body, String.class);
        assertFalse(DiffBuilder.compare(expectedOrderXml).withTest(xml).ignoreComments().ignoreWhitespace().build()
                .hasDifferences(), xml);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            @SuppressWarnings("resource")
            // NOTE: the context will take care to clean resources
            public void configure() {
                // Without stream caching, as it would turn a SAXSource body into a String before Smooks reads it
                from("direct:edi").noStreamCaching().to("smooks://edi-to-xml-smooks-config.xml");
                from("direct:customer").noStreamCaching().to("smooks://smooks-config.xml");
                from("direct:severalExports")
                        .process(new SmooksProcessor(
                                new Smooks().setExports(
                                        new Exports(Set.of(new Export(StringSink.class), new Export(JavaSink.class)))),
                                context));
            }
        };
    }
}

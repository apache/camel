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
import java.io.StringReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dataformat.smooks.Customer;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.smooks.Smooks;
import org.smooks.api.SmooksException;
import org.smooks.api.io.Source;
import org.smooks.io.payload.Exports;
import org.smooks.io.sink.StringSink;
import org.smooks.io.source.ByteSource;
import org.smooks.io.source.DOMSource;
import org.smooks.io.source.JavaSource;
import org.smooks.io.source.ReaderSource;
import org.smooks.io.source.StreamSource;
import org.smooks.io.source.StringSource;
import org.smooks.io.source.URLSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A message body that is already a Smooks {@link Source} follows the {@code allowExternalEntities} option like the
 * other XML message bodies.
 */
class SmooksSourceBodyTest extends CamelTestSupport {

    private static final String ENTITY_TEXT = "Text of the external entity";

    @TempDir
    Path tempDir;

    private String customerXml;

    @BeforeEach
    void writeEntityFile() throws Exception {
        Path entityFile = Files.writeString(tempDir.resolve("first-name.txt"), ENTITY_TEXT);
        customerXml = "<!DOCTYPE customer [<!ENTITY name SYSTEM \"" + entityFile.toUri() + "\">]>"
                      + "<customer><firstName>&name;</firstName><lastName>Smith</lastName></customer>";
    }

    @Test
    void stringSourceBodyFollowsAllowExternalEntities() {
        assertFollowsAllowExternalEntities(() -> new StringSource(customerXml));
    }

    @Test
    void readerSourceBodyFollowsAllowExternalEntities() {
        assertFollowsAllowExternalEntities(() -> new ReaderSource<>(new StringReader(customerXml)));
    }

    @Test
    void streamSourceBodyFollowsAllowExternalEntities() {
        assertFollowsAllowExternalEntities(
                () -> new StreamSource<>(new ByteArrayInputStream(customerXml.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void byteSourceBodyFollowsAllowExternalEntities() {
        assertFollowsAllowExternalEntities(() -> new ByteSource(customerXml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void urlSourceBodyFollowsAllowExternalEntities() throws Exception {
        URL url = Files.writeString(tempDir.resolve("customer.xml"), customerXml).toUri().toURL();
        assertFollowsAllowExternalEntities(() -> new URLSource(url));
    }

    @Test
    void internalEntitiesAreExpandedInStringAndSourceBodies() {
        String xml = "<!DOCTYPE customer [<!ENTITY name \"Ann\">]>"
                     + "<customer><firstName>&name;</firstName><lastName>Smith</lastName></customer>";
        assertEquals("Ann", template.requestBody("direct:customer", xml, Customer.class).getFirstName());
        assertEquals("Ann", template.requestBody("direct:customer", new StringSource(xml), Customer.class).getFirstName());
    }

    @Test
    void streamSourceBodyWithByteOrderMarkIsParsed() {
        byte[] xml = "\uFEFF<customer><lastName>Smith</lastName></customer>".getBytes(StandardCharsets.UTF_8);
        Customer customer = template.requestBody("direct:customer", new ByteSource(xml), Customer.class);
        assertEquals("Smith", customer.getLastName());
    }

    @Test
    void domSourceAndJavaSourceBodiesAreProcessedAsGiven() throws Exception {
        Document order = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element id = order.createElementNS(null, "id");
        id.setTextContent("1");
        order.appendChild(order.createElementNS(null, "order")).appendChild(id);
        assertEquals("<order><id>1</id></order>", template.requestBody("direct:toXml", new DOMSource(order), String.class));

        String coordinate = template.requestBody("direct:toXml", new JavaSource(new Coordinate(111, 222)), String.class);
        assertTrue(coordinate.contains("<x>111</x>") && coordinate.contains("<y>222</y>"), coordinate);
    }

    @Test
    void unsupportedSourceBodyIsRejected() {
        Source body = new Source() {
        };
        CamelExecutionException e
                = assertThrows(CamelExecutionException.class, () -> template.requestBody("direct:customer", body));
        SmooksException cause = assertInstanceOf(SmooksException.class, e.getCause());
        assertTrue(cause.getMessage().startsWith("A message body of type " + body.getClass().getName() + " is not supported"),
                cause.getMessage());
    }

    private void assertFollowsAllowExternalEntities(Supplier<Source> body) {
        Customer customer = template.requestBody("direct:customer", body.get(), Customer.class);
        assertEquals("Smith", customer.getLastName());
        assertEquals("", customer.getFirstName(), "The external entity must not be read by default");

        customer = template.requestBody("direct:customerWithExternalEntities", body.get(), Customer.class);
        assertEquals(ENTITY_TEXT, customer.getFirstName());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            @SuppressWarnings("resource")
            // NOTE: the context will take care to clean resources
            public void configure() {
                from("direct:customer").to("smooks://smooks-config.xml");
                from("direct:customerWithExternalEntities").to("smooks://smooks-config.xml?allowExternalEntities=true");
                from("direct:toXml")
                        .process(new SmooksProcessor(new Smooks().setExports(new Exports(StringSink.class)), context));
            }
        };
    }
}

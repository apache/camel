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
package org.apache.camel.component.xslt.saxon;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import javax.xml.XMLConstants;

import net.sf.saxon.TransformerFactoryImpl;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The XSLT 3.0 resource functions {@code unparsed-text()}, {@code unparsed-text-lines()},
 * {@code unparsed-text-available()}, {@code json-doc()} and {@code collection()} honour the transformer factory's
 * {@code ACCESS_EXTERNAL_STYLESHEET} restriction at transform time, the same way {@code document()} does, also in a
 * stylesheet run with {@code fn:transform()}. The {@code xslt-saxon} endpoint sets it to deny-all, so an external
 * {@code file:} resource is refused by default; a factory that permits {@code file} (or {@code all}) lets it through
 * again. The stylesheet's classpath {@code xsl:include} and Camel's {@code classpath:} scheme keep working.
 */
public class XsltSaxonResourceFunctionsExternalAccessTest extends CamelTestSupport {

    private static final String XSL = "org/apache/camel/component/xslt/saxon/resource-functions.xsl";
    private static final String MARKER = "EXTERNAL-DATA";

    @TempDir
    Path dir;

    @BeforeEach
    void createExternalResources() throws Exception {
        Files.writeString(dir.resolve("data.txt"), MARKER + "\n");
        Files.writeString(dir.resolve("data.json"), "{\"marker\": \"" + MARKER + "\"}");
        Path collection = Files.createDirectories(dir.resolve("collection"));
        Files.writeString(collection.resolve("data.xml"), "<data><marker>" + MARKER + "</marker></data>");
    }

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("fileFactory", factoryAllowing("file"));
        registry.bind("allFactory", factoryAllowing("all"));
    }

    private static TransformerFactoryImpl factoryAllowing(String protocols) {
        TransformerFactoryImpl factory = new TransformerFactoryImpl();
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, protocols);
        return factory;
    }

    private String uriOf(String function) {
        return switch (function) {
            case "json-doc" -> dir.resolve("data.json").toUri().toString();
            case "collection" -> dir.resolve("collection").toUri().toString();
            default -> dir.resolve("data.txt").toUri().toString();
        };
    }

    private String transform(String endpoint, String function, String resourceUri) {
        return template.requestBodyAndHeaders(endpoint, "<a/>",
                Map.of("function", function, "resourceUri", resourceUri), String.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { "unparsed-text", "unparsed-text-lines", "json-doc", "collection", "transform" })
    public void externalFileIsRefusedByDefault(String function) {
        String uri = uriOf(function);
        // the endpoint's factory denies external access ("" == deny-all), so the function fails instead of reading the
        // (existing) file given by a message header
        assertThrows(CamelExecutionException.class, () -> transform("direct:default", function, uri));
    }

    @Test
    public void unparsedTextAvailableIsFalseByDefault() {
        assertEquals("included:false", transform("direct:default", "unparsed-text-available", uriOf("unparsed-text")));
    }

    @Test
    public void refusalNamesTheRestriction() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> transform("direct:default", "unparsed-text", uriOf("unparsed-text")));
        assertTrue(messages(e).contains("ACCESS_EXTERNAL_STYLESHEET"),
                "expected the refusal to name the restriction, got: " + messages(e));
    }

    @ParameterizedTest
    @ValueSource(strings = { "direct:file", "direct:all" })
    public void externalFileIsReadWhenTheFactoryPermitsIt(String endpoint) {
        assertEquals("included:" + MARKER + "\n", transform(endpoint, "unparsed-text", uriOf("unparsed-text")));
        assertEquals("included:" + MARKER, transform(endpoint, "unparsed-text-lines", uriOf("unparsed-text")));
        assertEquals("included:true", transform(endpoint, "unparsed-text-available", uriOf("unparsed-text")));
        assertEquals("included:" + MARKER, transform(endpoint, "json-doc", uriOf("json-doc")));
        assertEquals("included:" + MARKER, transform(endpoint, "collection", uriOf("collection")));
        assertEquals("included:" + MARKER + "\n", transform(endpoint, "transform", uriOf("unparsed-text")));
    }

    @Test
    public void classpathResourceIsReadByDefault() {
        // Camel's classpath: scheme is outside the JAXP external-access model and is resolved as before
        assertEquals("included:CLASSPATH-DATA\n", transform("direct:default", "unparsed-text",
                "classpath:org/apache/camel/component/xslt/saxon/resource-functions-data.txt"));
    }

    private static String messages(Throwable e) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause()) {
            sb.append(t.getMessage()).append('\n');
        }
        return sb.toString();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:default").to("xslt-saxon:" + XSL);
                from("direct:file").to("xslt-saxon:" + XSL + "?transformerFactory=#fileFactory");
                from("direct:all").to("xslt-saxon:" + XSL + "?transformerFactory=#allFactory");
            }
        };
    }
}

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
package org.apache.camel.builder.xml;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.TypeConversionException;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.language.xpath.XPathBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.camel.language.xpath.XPathBuilder.xpath;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
public class XPathFeatureTest extends ContextTestSupport {
    public static final String DOM_BUILDER_FACTORY_FEATURE = XmlConverter.DOCUMENT_BUILDER_FACTORY_FEATURE;

    private static final String CANARY = "CANARY-SHOULD-NOT-BE-READ";

    public static final String XML_DATA
            = " <!DOCTYPE foo [ " + " <!ELEMENT foo ANY > <!ENTITY xxe SYSTEM \"file:///bin/test.sh\" >]> <test> &xxe; </test>";
    public static final String XML_DATA_INVALID
            = " <!DOCTYPE foo [ "
              + " <!ELEMENT foo ANY > <!ENTITY xxe SYSTEM \"file:///bin/test.sh\" >]> <test> &xxe; </test><notwellformed>";

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testXPathDocTypeDisallowed() {
        Exception e = assertThrows(Exception.class,
                () -> xpath("/").stringResult().evaluate(createExchange(XML_DATA)),
                "Should have thrown exception");

        assertIsInstanceOf(SAXParseException.class, e.getCause());
    }

    @Test
    public void testXPath() {
        // Set these features will enable the external general entities
        System.setProperty(DOM_BUILDER_FACTORY_FEATURE + ":" + "http://xml.org/sax/features/external-general-entities", "true");
        System.setProperty(DOM_BUILDER_FACTORY_FEATURE + ":" + "http://apache.org/xml/features/disallow-doctype-decl", "false");
        try {
            TypeConversionException ex = assertThrows(TypeConversionException.class,
                    () -> xpath("/").stringResult().evaluate(createExchange(XML_DATA)),
                    "Expect an Exception here");

            boolean b = ex.getCause() instanceof FileNotFoundException;
            assertTrue(b,
                    "Get a wrong exception cause: " + ex.getCause().getClass() + " instead of " + FileNotFoundException.class);
        } finally {
            System.clearProperty(DOM_BUILDER_FACTORY_FEATURE + ":" + "http://xml.org/sax/features/external-general-entities");
            System.clearProperty(DOM_BUILDER_FACTORY_FEATURE + ":" + "http://apache.org/xml/features/disallow-doctype-decl");
        }
    }

    /**
     * {@code documentType=InputSource} used to hand the payload straight to {@link javax.xml.xpath.XPathExpression},
     * which builds a DocumentBuilder of its own with the JDK defaults - so the DOCTYPE that
     * {@link #testXPathDocTypeDisallowed()} pins as refused on the default document type was accepted here, and the
     * external entity was resolved and expanded into the evaluated document. The two document types must agree on the
     * parser configuration.
     */
    @Test
    void docTypeIsAlsoDisallowedForAnInputSourceDocumentType() throws Exception {
        Path secret = Files.createTempFile("camel-xpath-entity", ".txt");
        try {
            Files.writeString(secret, CANARY);
            // an InputStream body, since that is what converts to an InputSource - and what a streaming
            // documentType=InputSource deployment actually receives
            String xml = "<!DOCTYPE foo [ <!ELEMENT foo ANY > <!ENTITY xxe SYSTEM \""
                         + secret.toUri() + "\" >]> <test> &xxe; </test>";

            // both branches of doInEvaluateAs: with a result QName and without one
            for (XPathBuilder builder : List.of(xpath("/").documentType(InputSource.class).stringResult(),
                    xpath("/test").documentType(InputSource.class))) {
                assertThatThrownBy(() -> builder.evaluate(createExchange(new ByteArrayInputStream(xml.getBytes(UTF_8)))))
                        .as("a DOCTYPE must be refused for documentType=InputSource, as it is for the default type")
                        .hasRootCauseInstanceOf(SAXParseException.class)
                        .rootCause().hasMessageContaining("DOCTYPE");
            }
        } finally {
            Files.deleteIfExists(secret);
        }
    }

    /**
     * The {@code InputSource} document type now shares the default type's parser, so it also shares its escape hatch:
     * the same system properties that {@link #testXPath()} uses relax it. Points at a file that does not exist, so a
     * {@code FileNotFoundException} is what proves the DOCTYPE was accepted and resolution attempted.
     */
    @Test
    void theDocumentBuilderFactoryFeaturesAlsoRelaxTheInputSourceDocumentType() {
        System.setProperty(DOM_BUILDER_FACTORY_FEATURE + ":" + "http://xml.org/sax/features/external-general-entities", "true");
        System.setProperty(DOM_BUILDER_FACTORY_FEATURE + ":" + "http://apache.org/xml/features/disallow-doctype-decl", "false");
        try {
            assertThatThrownBy(() -> xpath("/").documentType(InputSource.class).stringResult()
                    .evaluate(createExchange(new ByteArrayInputStream(XML_DATA.getBytes(UTF_8)))))
                    .hasRootCauseInstanceOf(FileNotFoundException.class);
        } finally {
            System.clearProperty(DOM_BUILDER_FACTORY_FEATURE + ":" + "http://xml.org/sax/features/external-general-entities");
            System.clearProperty(DOM_BUILDER_FACTORY_FEATURE + ":" + "http://apache.org/xml/features/disallow-doctype-decl");
        }
    }

    /**
     * Guards the assumption the test above rests on: an {@code InputStream} body really does reach the
     * {@code InputSource} branch, rather than failing earlier for want of a type converter.
     */
    @Test
    void anInputStreamBodyConvertsToAnInputSourceDocumentType() {
        Object result = xpath("/test/text()").documentType(InputSource.class).stringResult()
                .evaluate(createExchange(new ByteArrayInputStream("<test>ok</test>".getBytes(UTF_8))));

        assertThat(result).isEqualTo("ok");
    }

    @Test
    public void testXPathNoTypeConverter() {
        // define a class without type converter as document type
        RuntimeCamelException ex = assertThrows(RuntimeCamelException.class,
                () -> xpath("/").documentType(Exchange.class).stringResult().evaluate(createExchange(XML_DATA)),
                "Expect an Exception here");

        boolean b = ex.getCause() instanceof NoTypeConversionAvailableException;
        assertTrue(b, "Get a wrong exception cause: " + ex.getCause().getClass() + " instead of "
                      + NoTypeConversionAvailableException.class);
    }

    @Test
    public void testXPathResultOnInvalidData() {
        TypeConversionException ex = assertThrows(TypeConversionException.class,
                () -> xpath("/").stringResult().evaluate(createExchange(XML_DATA_INVALID)),
                "Expect an Exception here");

        boolean b = ex.getCause() instanceof SAXParseException;
        assertTrue(b,
                "Get a wrong exception cause: " + ex.getCause().getClass() + " instead of " + SAXParseException.class);
    }

    protected Exchange createExchange(Object xml) {
        Exchange exchange = createExchangeWithBody(context, xml);
        return exchange;
    }

}

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

import java.io.FileNotFoundException;

import org.xml.sax.SAXParseException;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.TypeConversionException;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import static org.apache.camel.language.xpath.XPathBuilder.xpath;
import static org.junit.jupiter.api.Assertions.*;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
public class XPathFeatureTest extends ContextTestSupport {
    public static final String DOM_BUILDER_FACTORY_FEATURE = XmlConverter.DOCUMENT_BUILDER_FACTORY_FEATURE;

    public static final String XML_DATA
            = " <!DOCTYPE foo [ " + " <!ELEMENT foo ANY > <!ENTITY xxe SYSTEM \"file:///bin/test.sh\" >]> <test> &xxe; </test>";
    public static final String XML_DATA_INVALID
            = " <!DOCTYPE foo [ "
              + " <!ELEMENT foo ANY > <!ENTITY xxe SYSTEM \"file:///bin/test.sh\" >]> <test> &xxe; </test><notwellformed>";

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        resetCoreConverters();
        super.setUp();
    }

    private void resetCoreConverters() throws Exception {
        /*
         * Field field =
         * CoreStaticTypeConverterLoader.class.getDeclaredField("INSTANCE");
         * field.setAccessible(true); Field modifiersField =
         * Field.class.getDeclaredField("modifiers");
         * modifiersField.setAccessible(true); modifiersField.setInt(field,
         * field.getModifiers() & ~Modifier.FINAL); Constructor<?> cns =
         * CoreStaticTypeConverterLoader.class.getDeclaredConstructor();
         * cns.setAccessible(true); field.set(null, cns.newInstance());
         */
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testXPathDocTypeDisallowed() throws Exception {
        try {
            xpath("/").stringResult().evaluate(createExchange(XML_DATA));
            fail();
        } catch (Exception e) {
            assertIsInstanceOf(SAXParseException.class, e.getCause());
        }
    }

    @Test
    public void testXPath() throws Exception {
        // Set these features will enable the external general entities
        System.setProperty(DOM_BUILDER_FACTORY_FEATURE + ":" + "http://xml.org/sax/features/external-general-entities", "true");
        System.setProperty(DOM_BUILDER_FACTORY_FEATURE + ":" + "http://apache.org/xml/features/disallow-doctype-decl", "false");
        try {
            xpath("/").stringResult().evaluate(createExchange(XML_DATA));
            fail("Expect an Exception here");
        } catch (TypeConversionException ex) {
            boolean b = ex.getCause() instanceof FileNotFoundException;
            assertTrue(b,
                    "Get a wrong exception cause: " + ex.getCause().getClass() + " instead of " + FileNotFoundException.class);
        } finally {
            System.clearProperty(DOM_BUILDER_FACTORY_FEATURE + ":" + "http://xml.org/sax/features/external-general-entities");
            System.clearProperty(DOM_BUILDER_FACTORY_FEATURE + ":" + "http://apache.org/xml/features/disallow-doctype-decl");
        }
    }

    @Test
    public void testXPathNoTypeConverter() throws Exception {
        try {
            // define a class without type converter as document type
            xpath("/").documentType(Exchange.class).stringResult().evaluate(createExchange(XML_DATA));
            fail("Expect an Exception here");
        } catch (RuntimeCamelException ex) {
            boolean b = ex.getCause() instanceof NoTypeConversionAvailableException;
            assertTrue(b, "Get a wrong exception cause: " + ex.getCause().getClass() + " instead of "
                          + NoTypeConversionAvailableException.class);
        }
    }

    @Test
    public void testXPathResultOnInvalidData() throws Exception {
        try {
            xpath("/").stringResult().evaluate(createExchange(XML_DATA_INVALID));
            fail("Expect an Exception here");
        } catch (TypeConversionException ex) {
            boolean b = ex.getCause() instanceof SAXParseException;
            assertTrue(b,
                    "Get a wrong exception cause: " + ex.getCause().getClass() + " instead of " + SAXParseException.class);
        }
    }

    protected Exchange createExchange(Object xml) {
        Exchange exchange = createExchangeWithBody(context, xml);
        return exchange;
    }

}

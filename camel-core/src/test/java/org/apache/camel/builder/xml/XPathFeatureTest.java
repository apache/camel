/**
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

import static org.apache.camel.builder.xml.XPathBuilder.xpath;

public class XPathFeatureTest extends ContextTestSupport {
    public static final String DOM_BUILDER_FACTORY_FEATURE = XmlConverter.DOCUMENT_BUILDER_FACTORY_FEATURE;
    
    public static final String XML_DATA = " <!DOCTYPE foo [ " 
        + " <!ELEMENT foo ANY > <!ENTITY xxe SYSTEM \"file:///bin/test.sh\" >]> <test> &xxe; </test>";
    public static final String XML_DATA_INVALID = " <!DOCTYPE foo [ " 
            + " <!ELEMENT foo ANY > <!ENTITY xxe SYSTEM \"file:///bin/test.sh\" >]> <test> &xxe; </test><notwellformed>";

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testXPathResult() throws Exception {
        String result = (String)xpath("/").stringResult().evaluate(createExchange(XML_DATA));
        assertEquals("Get a wrong result", "  ", result);
    }

    public void testXPath() throws Exception {
        // Set this feature will enable the external general entities
        System.setProperty(DOM_BUILDER_FACTORY_FEATURE + ":"
            + "http://xml.org/sax/features/external-general-entities", "true");
        try {
            xpath("/").stringResult().evaluate(createExchange(XML_DATA));
            fail("Expect an Exception here");
        } catch (TypeConversionException ex) {
            assertTrue("Get a wrong exception cause.", ex.getCause() instanceof RuntimeCamelException);
            assertTrue("Get a wrong exception cause.", ex.getCause().getCause() instanceof FileNotFoundException);
        } finally {
            System.clearProperty(DOM_BUILDER_FACTORY_FEATURE + ":"
                + "http://xml.org/sax/features/external-general-entities");
        }
    }

    public void testXPathNoTypeConverter() throws Exception {
        try {
            // define a class without type converter as document type
            xpath("/").documentType(Exchange.class).stringResult().evaluate(createExchange(XML_DATA));
            fail("Expect an Exception here");
        } catch (RuntimeCamelException ex) {
            assertTrue("Get a wrong exception cause.", ex.getCause() instanceof NoTypeConversionAvailableException);
        }
    }

    public void testXPathResultOnInvalidData() throws Exception {
        try {
            xpath("/").stringResult().evaluate(createExchange(XML_DATA_INVALID));
            fail("Expect an Exception here");
        } catch (TypeConversionException ex) {
            assertTrue("Get a wrong exception cause.", ex.getCause() instanceof RuntimeCamelException);
            assertTrue("Get a wrong exception cause.", ex.getCause().getCause() instanceof SAXParseException);
        }
    }

    protected Exchange createExchange(Object xml) {
        Exchange exchange = createExchangeWithBody(context, xml);
        return exchange;
    }

}

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

import java.io.ByteArrayInputStream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;

import net.sf.saxon.TransformerFactoryImpl;
import org.apache.camel.util.xml.StringSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the configuration helpers shared by {@link XsltSaxonEndpoint} and
 * {@link XsltSaxonAggregationStrategy}.
 */
class XsltSaxonHelperConfigurationTest {

    @Test
    void shouldRestrictExternalResourceAccessOnFactory() {
        TransformerFactoryImpl factory = new TransformerFactoryImpl();
        XsltSaxonHelper.configureExternalAccess(factory);

        assertEquals("", factory.getAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET));
    }

    @Test
    void shouldWrapStreamBasedSourceWithRestrictedReader() throws Exception {
        Source secured = XsltSaxonHelper.secureSource(new StringSource("<item>A</item>"));

        SAXSource saxSource = assertInstanceOf(SAXSource.class, secured);
        assertNotNull(saxSource.getXMLReader(), "A restricted reader should be installed on the source");
        assertTrue(saxSource.getXMLReader().getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
        assertFalse(saxSource.getXMLReader().getFeature("http://xml.org/sax/features/external-general-entities"));
        assertFalse(saxSource.getXMLReader().getFeature("http://xml.org/sax/features/external-parameter-entities"));
        assertFalse(saxSource.getXMLReader().getFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd"));
    }

    @Test
    void shouldLeaveAlreadyParsedSourceUnchanged() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DOMSource domSource = new DOMSource(
                dbf.newDocumentBuilder().parse(new ByteArrayInputStream("<item>A</item>".getBytes())));

        Source secured = XsltSaxonHelper.secureSource(domSource);

        assertSame(domSource, secured, "A source that is already parsed should be returned unchanged");
    }
}

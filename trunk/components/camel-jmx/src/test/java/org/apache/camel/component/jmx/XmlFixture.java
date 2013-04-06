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
package org.apache.camel.component.jmx;

import java.io.File;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.Difference;
import org.custommonkey.xmlunit.DifferenceConstants;
import org.custommonkey.xmlunit.DifferenceListener;
import org.custommonkey.xmlunit.XMLAssert;
import org.custommonkey.xmlunit.XMLUnit;

import static org.junit.Assert.fail;

public final class XmlFixture {

    private XmlFixture() {
    }

    public static Document toDoc(String aXmlString) throws Exception {
        return XMLUnit.buildControlDocument(aXmlString);
    }

    public static Document toDoc(File aFile) throws Exception {
        return XMLUnit.buildControlDocument(new InputSource(aFile.toString()));
    }

    public static void assertXMLIgnorePrefix(String aMessage, Document aExpected, Document aActual) throws Exception {
        XMLUnit.setIgnoreComments(true);
        XMLUnit.setIgnoreWhitespace(true);
        XMLUnit.setIgnoreAttributeOrder(true);

        Diff diff = new Diff(aExpected, aActual);
        diff.overrideDifferenceListener(new DifferenceListener() {

            public void skippedComparison(Node aArg0, Node aArg1) {
            }

            public int differenceFound(Difference aDifference) {
                if (aDifference.getId() == DifferenceConstants.NAMESPACE_PREFIX_ID) {
                    return DifferenceListener.RETURN_IGNORE_DIFFERENCE_NODES_IDENTICAL;
                }
                return DifferenceListener.RETURN_ACCEPT_DIFFERENCE;
            }
        });
        try {
            XMLAssert.assertXMLEqual(diff, true);
        } catch (Throwable t) {
            dump(aActual);
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            fail(sw.toString());
        }
    }

    public static void dump(Document aActual) throws TransformerConfigurationException,
            TransformerException {
        TransformerFactory tf = XMLUnit.getTransformerFactory();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.transform(new DOMSource(aActual), new StreamResult(System.out));
    }

    public static Document stripTimestamp(Document aDocument) throws Exception {
        String resourcePath = "/stripTimestamp.xsl";
        return transform(aDocument, resourcePath);
    }

    public static Document stripUUID(Document aDocument) throws Exception {
        String resourcePath = "/stripUUID.xsl";
        return transform(aDocument, resourcePath);
    }

    protected static Document transform(Document aDocument, String aResourcePath) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        InputStream in = XmlFixture.class.getResourceAsStream(aResourcePath);
        Source src = new StreamSource(in);
        src.setSystemId(XmlFixture.class.getResource(aResourcePath).toExternalForm());
        Transformer t = tf.newTransformer(src);
        DOMResult result = new DOMResult();
        t.transform(new DOMSource(aDocument), result);
        return (Document) result.getNode();
    }

}

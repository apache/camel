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
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Diff;

import static org.junit.Assert.assertFalse;

public final class XmlFixture {

    private XmlFixture() {
    }

    public static Source toSource(String aXmlString) throws Exception {
        return Input.fromString(aXmlString).build();
    }

    public static Source toSource(File aFile) throws Exception {
        return Input.fromFile(aFile).build();
    }

    public static void assertXMLIgnorePrefix(String aMessage, Source aExpected, Source aActual) throws Exception {
        Diff diff = DiffBuilder.compare(aExpected).withTest(aActual)
                .ignoreComments().ignoreWhitespace()
                .checkForSimilar().build();
        try {
            assertFalse(aMessage + ":\n" + diff.toString(), diff.hasDifferences());
        } catch (Throwable t) {
            dump(aActual);
            throw t;
        }
    }

    public static void dump(Source aActual) throws TransformerConfigurationException,
            TransformerException {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.transform(aActual, new StreamResult(System.out));
    }

    public static Source stripTimestamp(Source aSource) throws Exception {
        String resourcePath = "/stripTimestamp.xsl";
        return transform(aSource, resourcePath);
    }

    public static Source stripUUID(Source aSource) throws Exception {
        String resourcePath = "/stripUUID.xsl";
        return transform(aSource, resourcePath);
    }

    protected static Source transform(Source aSource, String aResourcePath) throws Exception {
        Source stylesheet = new StreamSource(XmlFixture.class.getResourceAsStream(aResourcePath));
        stylesheet.setSystemId(XmlFixture.class.getResource(aResourcePath).toExternalForm());
        return Input.byTransforming(aSource).withStylesheet(stylesheet).build();
    }

}

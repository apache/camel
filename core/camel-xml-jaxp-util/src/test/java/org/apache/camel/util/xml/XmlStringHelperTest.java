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
package org.apache.camel.util.xml;

import java.nio.file.Files;
import java.nio.file.Path;

import org.xml.sax.SAXException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class XmlStringHelperTest {

    private static final String LAUGHS = "<!DOCTYPE a [<!ENTITY l \"lol\"><!ENTITY l2 \"&l;&l;&l;&l;&l;&l;&l;&l;&l;&l;\">]>"
                                         + "<a>&l2;</a>";

    @TempDir
    Path dir;

    @Test
    public void testIsWellFormed() {
        assertTrue(XmlStringHelper.isWellFormed("<a><b x=\"1\">text</b></a>"));
        assertTrue(XmlStringHelper.isWellFormed("<?xml version=\"1.0\"?>\n<a/>"));
        assertTrue(XmlStringHelper.isWellFormed("<ns:a xmlns:ns=\"urn:a\"><ns:b/></ns:a>"));

        assertFalse(XmlStringHelper.isWellFormed(null));
        assertFalse(XmlStringHelper.isWellFormed(""));
        assertFalse(XmlStringHelper.isWellFormed("  "));
        assertFalse(XmlStringHelper.isWellFormed("Hello"));
        assertFalse(XmlStringHelper.isWellFormed("<a><b></a>"));
        assertFalse(XmlStringHelper.isWellFormed("<a/><b/>"));
        assertFalse(XmlStringHelper.isWellFormed("<ns:a/>"));
        assertFalse(XmlStringHelper.isWellFormed("{\"a\": 1}"));
    }

    @Test
    public void testDoctypeIsRejected() throws Exception {
        Path secret = dir.resolve("secret.txt");
        Files.writeString(secret, "top-secret");
        String xxe = "<!DOCTYPE a [<!ENTITY e SYSTEM \"" + secret.toUri() + "\">]><a>&e;</a>";
        String dtd = "<!DOCTYPE a SYSTEM \"" + dir.resolve("a.dtd").toUri() + "\"><a/>";
        String plain = "<!DOCTYPE a><a/>";

        for (String xml : new String[] { xxe, dtd, plain, LAUGHS }) {
            assertFalse(XmlStringHelper.isWellFormed(xml), xml);
            assertThrows(SAXException.class, () -> XmlStringHelper.stripComments(xml), xml);
            assertThrows(SAXException.class, () -> XmlStringHelper.stripNamespaces(xml), xml);
            assertThrows(SAXException.class, () -> XmlStringHelper.compact(xml), xml);
            assertThrows(SAXException.class, () -> XmlStringHelper.canonicalize(xml, true), xml);
            assertThrows(SAXException.class, () -> XmlStringHelper.canonicalize(xml, false), xml);
        }
    }

    @Test
    public void testStripDeclaration() {
        assertEquals("<a/>", XmlStringHelper.stripDeclaration("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<a/>"));
        assertEquals("<a/>", XmlStringHelper.stripDeclaration("﻿<?xml version='1.0'?><a/>"));
        assertEquals("<a>\n  <b/>\n</a>", XmlStringHelper.stripDeclaration("  <?xml version=\"1.0\"?>\r\n<a>\n  <b/>\n</a>"));
        // only the declaration goes; the rest is left exactly as written, even when it is not well-formed
        assertEquals("<a><b></a>", XmlStringHelper.stripDeclaration("<?xml version=\"1.0\"?><a><b></a>"));

        assertEquals("<a/>", XmlStringHelper.stripDeclaration("<a/>"));
        assertEquals("<?xml-stylesheet href=\"a.xsl\"?><a/>",
                XmlStringHelper.stripDeclaration("<?xml-stylesheet href=\"a.xsl\"?><a/>"));
        assertEquals("<?xml version=\"1.0\"", XmlStringHelper.stripDeclaration("<?xml version=\"1.0\""));
        assertEquals("Hello", XmlStringHelper.stripDeclaration("Hello"));
        assertNull(XmlStringHelper.stripDeclaration(null));
    }

    @Test
    public void testStripComments() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!-- header -->\n"
                     + "<order id='1'>\n  <!-- internal -->\n  <item>Cam<!-- x -->el</item>\n</order>";
        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                     + "<order id=\"1\">\n  \n  <item>Camel</item>\n</order>",
                XmlStringHelper.stripComments(xml));

        // CDATA, processing instructions, namespaces and escaped text are kept
        xml = "<a:x xmlns:a=\"urn:a\" t=\"&quot;&amp;\"><?pi data?><![CDATA[<keep>]]>1 &lt; 2<b/></a:x>";
        assertEquals(xml, XmlStringHelper.stripComments(xml));
        assertNull(XmlStringHelper.stripComments(null));
    }

    @Test
    public void testStripNamespaces() throws Exception {
        String xml = "<ns1:Order xmlns:ns1=\"http://example.com\" xmlns=\"urn:default\" ns1:id=\"1\">"
                     + "<ns1:Item xml:lang=\"en\">Camel</ns1:Item><Note/></ns1:Order>";
        assertEquals("<Order id=\"1\"><Item xml:lang=\"en\">Camel</Item><Note/></Order>",
                XmlStringHelper.stripNamespaces(xml));

        // the first of two attributes with the same local name is kept
        xml = "<a xmlns:x=\"urn:x\" xmlns:y=\"urn:y\" x:id=\"1\" y:id=\"2\"/>";
        assertEquals("<a id=\"1\"/>", XmlStringHelper.stripNamespaces(xml));

        // the SOAP case from CAMEL-27274
        xml = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
              + "<soap:Body><m:GetPrice xmlns:m=\"urn:prices\"><m:Item>Apples</m:Item></m:GetPrice></soap:Body>"
              + "</soap:Envelope>";
        assertEquals("<Envelope><Body><GetPrice><Item>Apples</Item></GetPrice></Body></Envelope>",
                XmlStringHelper.stripNamespaces(xml));
    }

    @Test
    public void testCompact() throws Exception {
        String xml = "<?xml version=\"1.0\"?>\n<!-- c -->\n<order>\n  <id>1</id>\n  <name> Camel  in Action </name>\n"
                     + "  <empty></empty>\n  <data><![CDATA[  a  ]]></data>\n</order>\n";
        assertEquals("<?xml version=\"1.0\"?><!-- c --><order><id>1</id><name> Camel  in Action </name>"
                     + "<empty/><data><![CDATA[  a  ]]></data></order>",
                XmlStringHelper.compact(xml));

        // the indentation goes, the text inside an element stays as it is
        assertEquals("<a><b><c>x</c><d> y </d></b></a>",
                XmlStringHelper.compact("<a>\n  <b>\n    <c>x</c>\n    <d> y </d>\n  </b>\n</a>"));

        assertThrows(SAXException.class, () -> XmlStringHelper.compact("<a><b></a>"));
    }

    @Test
    public void testCanonicalize() throws Exception {
        String a = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!-- c --><a  z=\"1\" b='2' xmlns:x=\"urn:x\""
                   + " xmlns:y=\"urn:unused\"><x:b/><c></c></a>";
        assertEquals("<a b=\"2\" z=\"1\"><x:b xmlns:x=\"urn:x\"></x:b><c></c></a>", XmlStringHelper.canonicalize(a, true));
        assertEquals("<a xmlns:x=\"urn:x\" xmlns:y=\"urn:unused\" b=\"2\" z=\"1\"><x:b></x:b><c></c></a>",
                XmlStringHelper.canonicalize(a, false));

        // formatting differences give the same result
        String b = "<a b=\"2\" z=\"1\" xmlns:x=\"urn:x\"><x:b></x:b><c/></a>";
        assertEquals(XmlStringHelper.canonicalize(a, true), XmlStringHelper.canonicalize(b, true));

        // non-ASCII characters, whatever encoding the declaration names
        assertEquals("<a>Ærø 東京</a>",
                XmlStringHelper.canonicalize("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><a>Ærø 東京</a>", true));

        assertThrows(SAXException.class, () -> XmlStringHelper.canonicalize("<a><b></a>", true));
        assertNull(XmlStringHelper.canonicalize(null, true));
    }
}

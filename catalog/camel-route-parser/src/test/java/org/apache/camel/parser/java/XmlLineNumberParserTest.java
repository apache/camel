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
package org.apache.camel.parser.java;

import java.io.FileInputStream;
import java.io.InputStream;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import org.apache.camel.parser.helper.XmlLineNumberParser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class XmlLineNumberParserTest {

    @Test
    public void testRespectNamespace() throws Exception {
        InputStream is = new FileInputStream("src/test/resources/org/apache/camel/parser/xml/mycamel.xml");
        Document parsedXml = XmlLineNumberParser.parseXml(is);
        NodeList fromCamelWithNamespace = parsedXml.getElementsByTagNameNS("http://camel.apache.org/schema/spring", "from");
        assertEquals(1, fromCamelWithNamespace.getLength());
    }
}

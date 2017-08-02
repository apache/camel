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
package org.apache.camel.dataformat.tagsoup;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import org.apache.camel.util.IOHelper;

public final class TidyMarkupTestSupport {
    
    private TidyMarkupTestSupport() {
        // Utility class
    }
    
    public static String loadFileAsString(File file) throws Exception {
        StringBuilder fileContent = new StringBuilder();
        BufferedReader input = IOHelper.buffered(new FileReader(file));
        try {
            String line = null;
            while ((line = input.readLine()) != null) {
                fileContent.append(line);
                fileContent.append(System.lineSeparator());
            }
        } finally {
            input.close();
        }
        return fileContent.toString();
    }

    /**
     * Convert XML String to a Document.
     * 
     * @param xmlString
     * @return document Document
     * @throws IOException
     * @throws SAXException
     * @throws ParserConfigurationException
     */
    public static Document stringToXml(String xmlString) throws SAXException, IOException, ParserConfigurationException {
        return createDocumentBuilder().parse(new InputSource(new StringReader(xmlString)));
    }

    /**
     * Static to generate a documentBuilder
     * 
     * @return
     * @throws ParserConfigurationException
     */
    public static DocumentBuilder createDocumentBuilder() throws ParserConfigurationException {

        DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
        docBuilderFactory.setIgnoringComments(true);
        docBuilderFactory.setIgnoringElementContentWhitespace(true);
        docBuilderFactory.setCoalescing(true);
        docBuilderFactory.setExpandEntityReferences(true);
        docBuilderFactory.setNamespaceAware(true);
        return docBuilderFactory.newDocumentBuilder();
    }
}

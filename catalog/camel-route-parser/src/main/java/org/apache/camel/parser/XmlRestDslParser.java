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
package org.apache.camel.parser;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;

import org.w3c.dom.Document;

import org.apache.camel.parser.helper.CamelXmlRestDslParserHelper;
import org.apache.camel.parser.helper.XmlLineNumberParser;
import org.apache.camel.parser.model.RestConfigurationDetails;
import org.apache.camel.parser.model.RestServiceDetails;

/**
 * A Camel XML parser that parses Camel XML Rest DSL source code.
 * <p/>
 * This implementation is higher level details, and uses the lower level parser {@link CamelXmlRestDslParserHelper}.
 */
public final class XmlRestDslParser {

    private XmlRestDslParser() {
    }

    /**
     * Parses the XML file and build a rest configuration model of the discovered rest configurations in the XML source file.
     *
     * @param xml                     the xml file as input stream
     * @param baseDir                 the base of the source code
     * @param fullyQualifiedFileName  the fully qualified source code file name
     * @return a list of rest configurations (often there is only one)
     */
    public static List<RestConfigurationDetails> parseRestConfiguration(InputStream xml, String baseDir, String fullyQualifiedFileName) {
        // try parse it as dom
        Document dom = null;
        try {
            dom = XmlLineNumberParser.parseXml(xml);
        } catch (Exception e) {
            // ignore as the xml file may not be valid at this point
        }
        if (dom != null) {
            CamelXmlRestDslParserHelper parser = new CamelXmlRestDslParserHelper();
            return parser.parseRestConfiguration(dom, baseDir, fullyQualifiedFileName);
        }

        return Collections.EMPTY_LIST;
    }

    /**
     * Parses the java source class and build a rest service model of the discovered rest services in the java source class.
     *
     * @param xml                     the xml file as input stream
     * @param baseDir                 the base of the source code
     * @param fullyQualifiedFileName  the fully qualified source code file name
     * @return a list of rest services
     */
    public static List<RestServiceDetails> parseRestService(InputStream xml, String baseDir, String fullyQualifiedFileName) {

        // try parse it as dom
        Document dom = null;
        try {
            dom = XmlLineNumberParser.parseXml(xml);
        } catch (Exception e) {
            // ignore as the xml file may not be valid at this point
        }
        if (dom != null) {
            CamelXmlRestDslParserHelper parser = new CamelXmlRestDslParserHelper();
            return parser.parseRestService(dom, baseDir, fullyQualifiedFileName);
        }

        return Collections.EMPTY_LIST;
    }

}

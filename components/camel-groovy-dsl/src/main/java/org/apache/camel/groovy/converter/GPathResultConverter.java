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
package org.apache.camel.groovy.converter;

import java.io.IOException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import groovy.util.XmlSlurper;
import groovy.util.slurpersupport.GPathResult;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.StringSource;
import org.apache.camel.converter.jaxp.XmlConverter;

@Converter
public class GPathResultConverter {

    private final XmlConverter xmlConverter = new XmlConverter();

    @Converter
    public GPathResult fromString(String input) throws ParserConfigurationException, SAXException, IOException {
        return new XmlSlurper().parseText(input);
    }

    @Converter
    public GPathResult fromStringSource(StringSource input) throws IOException, SAXException, ParserConfigurationException {
        return fromString(input.getText());
    }

    @Converter
    public GPathResult fromNode(Node input, Exchange exchange) throws IOException, SAXException, ParserConfigurationException, TransformerException {
        return fromString(xmlConverter.toString(input, exchange));
    }
}

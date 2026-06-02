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
package org.apache.camel.component.hl7;

import java.io.IOException;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.DefaultModelClassFactory;
import ca.uhn.hl7v2.parser.ParserConfiguration;
import ca.uhn.hl7v2.parser.UnexpectedSegmentBehaviourEnum;
import ca.uhn.hl7v2.parser.XMLParser;
import ca.uhn.hl7v2.validation.impl.ValidationContextFactory;
import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.converter.IOConverter;

/**
 * HL7 converters.
 */
@Converter(generateLoader = true)
public final class HL7Converter {

    private static final HapiContext DEFAULT_CONTEXT;

    static {
        ParserConfiguration parserConfiguration = new ParserConfiguration();
        parserConfiguration.setDefaultObx2Type("ST");
        parserConfiguration.setInvalidObx2Type("ST");
        parserConfiguration.setUnexpectedSegmentBehaviour(UnexpectedSegmentBehaviourEnum.ADD_INLINE);

        DEFAULT_CONTEXT = new DefaultHapiContext(
                parserConfiguration, ValidationContextFactory.noValidation(), new DefaultModelClassFactory());
    }

    private HL7Converter() {
        // Helper class
    }

    @Converter
    public static String toString(Message message) throws HL7Exception {
        return message.encode();
    }

    @Converter
    public static byte[] toByteArray(Message message, Exchange exchange) throws HL7Exception, IOException {
        return IOConverter.toByteArray(message.encode(), exchange);
    }

    @Converter
    public static Message toMessage(String body) throws HL7Exception {
        return DEFAULT_CONTEXT.getGenericParser().parse(body);
    }

    @Converter
    public static Message toMessage(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return DEFAULT_CONTEXT.getGenericParser().parse(IOConverter.toString(body, exchange));
    }

    @Converter
    public static Message toMessage(Document doc) throws HL7Exception {
        String version = getVersionFromDocument(doc);
        return ((XMLParser) DEFAULT_CONTEXT.getXMLParser()).parseDocument(doc, version);
    }

    private static final String HL7_V2_XML_NAMESPACE = "urn:hl7-org:v2xml";

    static String getVersionFromDocument(Document doc) {
        NodeList nodes = doc.getElementsByTagNameNS(HL7_V2_XML_NAMESPACE, "VID.1");
        if (nodes.getLength() > 0) {
            String version = nodes.item(0).getTextContent();
            if (version != null && !version.trim().isEmpty()) {
                return version.trim();
            }
        }
        nodes = doc.getElementsByTagNameNS(HL7_V2_XML_NAMESPACE, "MSH.12");
        if (nodes.getLength() > 0) {
            String version = nodes.item(0).getTextContent();
            if (version != null && !version.trim().isEmpty()) {
                return version.trim();
            }
        }
        return null;
    }

}

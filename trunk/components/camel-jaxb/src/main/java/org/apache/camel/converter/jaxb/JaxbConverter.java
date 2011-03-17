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
package org.apache.camel.converter.jaxb;

import java.util.HashMap;
import java.util.Map;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.util.JAXBSource;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.converter.HasAnnotation;
import org.apache.camel.converter.jaxp.XmlConverter;

/**
 * As we have the JAXB FallbackTypeConverter, so we don't need to register this converter
 * @version 
 */
public final class JaxbConverter {
    private XmlConverter xmlConverter = new XmlConverter();
    private Map<Class<?>, JAXBContext> contexts = new HashMap<Class<?>, JAXBContext>();

    //@Converter
    public JAXBSource toSource(Object value) throws JAXBException {
        if (value == null) {
            throw new IllegalArgumentException("Cannot convert from null value to JAXBSource");
        }
        // just need to check if the Object class has the XmlRootElement
        if (value.getClass().getAnnotation(XmlRootElement.class) != null) {
            JAXBContext context = getJaxbContext(value);
            return new JAXBSource(context, value);
        } else {
            return null;
        }
    }

    //@Converter
    public Document toDocument(Object value) throws JAXBException, ParserConfigurationException {
        if (value == null) {
            throw new IllegalArgumentException("Cannot convert from null value to JAXBSource");
        }
        if (value.getClass().getAnnotation(XmlRootElement.class) != null) {
            JAXBContext context = getJaxbContext(value);
            // must create a new instance of marshaller as its not thread safe
            Marshaller marshaller = context.createMarshaller();

            Document doc = xmlConverter.createDocument();
            marshaller.marshal(value, doc);
            return doc;
        } else {
            return null;
        }
    }

    @Converter
    public static MessageDefinition toMessageType(Exchange exchange) {
        return toMessageType(exchange.getIn());
    }

    @Converter
    public static MessageDefinition toMessageType(Message in) {
        MessageDefinition answer = new MessageDefinition();
        answer.copyFrom(in);
        return answer;
    }

    private synchronized JAXBContext getJaxbContext(Object value) throws JAXBException {
        Class<?> type = value.getClass();
        JAXBContext context = contexts.get(type);
        if (context == null) {
            context = JAXBContext.newInstance(type);
            contexts.put(type, context);
        }
        return context;
    }
}

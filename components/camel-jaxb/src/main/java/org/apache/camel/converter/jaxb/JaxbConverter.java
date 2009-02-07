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
 * @version $Revision$
 */
public final class JaxbConverter {
    private XmlConverter jaxbConverter;
    private Map<Class, JAXBContext> contexts = new HashMap<Class, JAXBContext>();

    public XmlConverter getJaxbConverter() {
        if (jaxbConverter == null) {
            jaxbConverter = new XmlConverter();
        }
        return jaxbConverter;
    }

    public void setJaxbConverter(XmlConverter jaxbConverter) {
        this.jaxbConverter = jaxbConverter;
    }

    @Converter
    public JAXBSource toSource(@HasAnnotation(XmlRootElement.class)Object value) throws JAXBException {
        JAXBContext context = getJaxbContext(value);
        return new JAXBSource(context, value);
    }

    @Converter
    public Document toDocument(@HasAnnotation(XmlRootElement.class)Object value) throws JAXBException, ParserConfigurationException {
        JAXBContext context = getJaxbContext(value);
        Marshaller marshaller = context.createMarshaller();

        Document doc = getJaxbConverter().createDocument();
        marshaller.marshal(value, doc);
        return doc;
    }

    @Converter
    public static MessageType toMessageType(Exchange exchange) {
        return toMessageType(exchange.getIn());
    }

    @Converter
    public static MessageType toMessageType(Message in) {
        MessageType answer = new MessageType();
        answer.copyFrom(in);
        return answer;
    }

    private synchronized JAXBContext getJaxbContext(Object value) throws JAXBException {
        JAXBContext context = contexts.get(value.getClass());
        if (context == null) {
            context = createJaxbContext(value);
            contexts.put(value.getClass(), context);
        }
        return context;
    }

    private JAXBContext createJaxbContext(Object value) throws JAXBException {
        if (value == null) {
            throw new IllegalArgumentException("Cannot convert from null value to JAXBSource");
        }
        return JAXBContext.newInstance(value.getClass());
    }

}

/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.converter.jaxb;

import org.apache.camel.converter.HasAnnotation;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.w3c.dom.Document;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.util.JAXBSource;
import javax.xml.parsers.ParserConfigurationException;

/**
 * @version $Revision$
 */
public class JaxbConverter {
    private XmlConverter jaxbConverter;

    public XmlConverter getJaxbConverter() {
        if (jaxbConverter == null) {
            jaxbConverter = new XmlConverter();
        }
        return jaxbConverter;
    }

    public void setJaxbConverter(XmlConverter jaxbConverter) {
        this.jaxbConverter = jaxbConverter;
    }

    public static JAXBSource toSource(@HasAnnotation(XmlRootElement.class)Object value) throws JAXBException {
        JAXBContext context = createJaxbContext(value);
        return new JAXBSource(context, value);
    }

    public Document toDocument(@HasAnnotation(XmlRootElement.class)Object value) throws JAXBException, ParserConfigurationException {
        JAXBContext context = createJaxbContext(value);
        Marshaller marshaller = context.createMarshaller();

        Document doc = getJaxbConverter().createDocument();
        marshaller.marshal(value, doc);
        return doc;
    }

    protected static JAXBContext createJaxbContext(Object value) throws JAXBException {
        if (value == null) {
            throw new IllegalArgumentException("Cannot convert from null value to JAXBSource");
        }
        JAXBContext context = JAXBContext.newInstance(value.getClass());
        return context;
    }

/*
    public void write(OutputStream out, Object value) throws JAXBException {
        JAXBContext context = JAXBContext.newInstance(value.getClass());
        Marshaller marshaller = context.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
		marshaller.marshal(value, out);
    }
*/
}

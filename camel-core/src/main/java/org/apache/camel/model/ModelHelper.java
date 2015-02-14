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
package org.apache.camel.model;

import org.apache.camel.NamedNode;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;

/**
 * Helper for the Camel {@link org.apache.camel.model model} classes.
 */
public final class ModelHelper {

    private ModelHelper() {
        // utility class
    }

    /**
     * Dumps the definition as XML
     *
     * @param definition  the definition, such as a {@link org.apache.camel.NamedNode}
     * @return            the output in XML (is formatted)
     * @throws JAXBException is throw if error marshalling to XML
     */
    public static String dumpModelAsXml(ModelCamelContext modelCamelContext, NamedNode definition) throws JAXBException {
        JAXBContext jaxbContext = modelCamelContext.getModelJAXBContextFactory().newJAXBContext();

        Marshaller marshaller = jaxbContext.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        StringWriter buffer = new StringWriter();
        marshaller.marshal(definition, buffer);

        return buffer.toString();
    }

    /**
     * Marshal the xml to the model definition
     *
     * @param xml the xml
     * @param type the definition type to return, will throw a {@link ClassCastException} if not the expected type
     * @return the model definition
     * @throws javax.xml.bind.JAXBException is thrown if error unmarshalling from xml to model
     */
    public static <T extends NamedNode> T createModelFromXml(ModelCamelContext modelCamelContext, String xml, Class<T> type) throws JAXBException {
        JAXBContext jaxbContext = modelCamelContext.getModelJAXBContextFactory().newJAXBContext();
        StringReader reader = new StringReader(xml);
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        Object result = unmarshaller.unmarshal(reader);
        reader.close();

        if (result == null) {
            throw new JAXBException("Cannot unmarshal to " + type + " using JAXB from XML: " + xml);
        }
        return type.cast(result);
    }

    /**
     * Marshal the xml to the model definition
     *
     * @param stream the xml stream
     * @param type the definition type to return, will throw a {@link ClassCastException} if not the expected type
     * @return the model definition
     * @throws javax.xml.bind.JAXBException is thrown if error unmarshalling from xml to model
     */
    public static <T extends NamedNode> T createModelFromXml(ModelCamelContext modelCamelContext, InputStream stream, Class<T> type) throws JAXBException {
        JAXBContext jaxbContext = modelCamelContext.getModelJAXBContextFactory().newJAXBContext();
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        Object result = unmarshaller.unmarshal(stream);
        return type.cast(result);
    }
}

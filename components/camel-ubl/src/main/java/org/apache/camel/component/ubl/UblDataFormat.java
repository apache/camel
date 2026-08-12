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
package org.apache.camel.component.ubl;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

import com.helger.jaxb.GenericJAXBMarshaller;
import com.helger.ubl21.UBL21Marshaller;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.service.ServiceSupport;

/**
 * Marshal and unmarshal UBL 2.1 (Universal Business Language) documents using the
 * <a href="https://github.com/phax/ph-ubl">ph-ubl</a> library.
 */
@Dataformat("ubl")
@Metadata(firstVersion = "4.23.0", title = "UBL")
public class UblDataFormat extends ServiceSupport implements DataFormat, DataFormatName, CamelContextAware {

    private CamelContext camelContext;
    private boolean prettyPrint;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public String getDataFormatName() {
        return "ubl";
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
        String className = graph.getClass().getSimpleName();
        if (!className.endsWith("Type")) {
            throw new CamelExchangeException(
                    "Unsupported UBL document type: " + graph.getClass().getName()
                                             + ". Expected a UBL 2.1 JAXB type (e.g. InvoiceType, CreditNoteType)",
                    exchange);
        }
        String docName = className.substring(0, className.length() - 4);
        String methodName = Character.toLowerCase(docName.charAt(0)) + docName.substring(1);

        GenericJAXBMarshaller marshaller = lookupMarshaller(methodName, exchange);
        marshaller.setFormattedOutput(prettyPrint);
        marshaller.setUseSchema(false);
        byte[] bytes = marshaller.getAsBytes(graph);
        if (bytes == null) {
            throw new CamelExchangeException("Failed to marshal UBL document of type " + className, exchange);
        }
        stream.write(bytes);
    }

    @Override
    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        byte[] data = stream.readAllBytes();

        String localName = peekRootElement(data, exchange);
        String methodName = Character.toLowerCase(localName.charAt(0)) + localName.substring(1);

        GenericJAXBMarshaller<?> marshaller = lookupMarshaller(methodName, exchange);
        marshaller.setUseSchema(false);
        Object result = marshaller.read(new ByteArrayInputStream(data));
        if (result == null) {
            throw new CamelExchangeException("Failed to unmarshal UBL document with root element: " + localName, exchange);
        }
        return result;
    }

    private GenericJAXBMarshaller<?> lookupMarshaller(String methodName, Exchange exchange) throws Exception {
        try {
            Method method = UBL21Marshaller.class.getMethod(methodName);
            return (GenericJAXBMarshaller<?>) method.invoke(null);
        } catch (NoSuchMethodException e) {
            throw new CamelExchangeException(
                    "No UBL 2.1 marshaller found for: " + methodName
                                             + ". Ensure the document type is a valid UBL 2.1 type.",
                    exchange);
        }
    }

    private static String peekRootElement(byte[] data, Exchange exchange) throws Exception {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        XMLStreamReader reader = factory.createXMLStreamReader(new ByteArrayInputStream(data));
        try {
            while (reader.hasNext()) {
                if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                    return reader.getLocalName();
                }
            }
            throw new CamelExchangeException("No root element found in UBL document", exchange);
        } finally {
            reader.close();
        }
    }

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }
}

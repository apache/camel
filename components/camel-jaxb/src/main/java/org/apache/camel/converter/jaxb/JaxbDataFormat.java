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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * A <a href="http://camel.apache.org/data-format.html">data format</a> ({@link DataFormat})
 * using JAXB2 to marshal to and from XML
 *
 * @version $Revision$
 */
public class JaxbDataFormat implements DataFormat, CamelContextAware {

    private CamelContext camelContext;
    private JAXBContext context;
    private String contextPath;
    private boolean prettyPrint = true;
    private boolean ignoreJAXBElement = true;
    private boolean filterNonXmlChars;
    private String encoding;
    // partial support
    private QName partNamespace;
    private String partClass;
    private Class partialClass;

    public JaxbDataFormat() {
    }

    public JaxbDataFormat(JAXBContext context) {
        this.context = context;
    }

    public JaxbDataFormat(String contextPath) {
        this.contextPath = contextPath;
    }

    public void marshal(Exchange exchange, Object graph, OutputStream stream) throws IOException {
        try {            
            // must create a new instance of marshaller as its not thread safe
            Marshaller marshaller = getContext().createMarshaller();
            if (isPrettyPrint()) {
                marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
            } 
            // exchange take precedence over encoding option
            String charset = exchange.getProperty(Exchange.CHARSET_NAME, String.class);
            if (charset == null) {
                charset = encoding;
            }
            if (charset != null) {
                marshaller.setProperty(Marshaller.JAXB_ENCODING, charset);
            }

            marshal(exchange, graph, stream, marshaller);

        } catch (JAXBException e) {
            throw IOHelper.createIOException(e);
        } catch (XMLStreamException e) {
            throw IOHelper.createIOException(e);
        }
    }

    @SuppressWarnings("unchecked")
    void marshal(Exchange exchange, Object graph, OutputStream stream, Marshaller marshaller)
        throws XMLStreamException, JAXBException {

        Object e = graph;
        if (getPartClass() != null && getPartNamespace() != null) {
            e = new JAXBElement(getPartNamespace(), getPartialClass(exchange), graph);
        }

        if (needFiltering(exchange)) {
            marshaller.marshal(e, createFilteringWriter(stream));
        } else {
            marshaller.marshal(e, stream);
        }
    }

    private FilteringXmlStreamWriter createFilteringWriter(OutputStream stream)
        throws XMLStreamException, FactoryConfigurationError {
        XMLStreamWriter writer = XMLOutputFactory.newInstance().createXMLStreamWriter(stream);
        FilteringXmlStreamWriter filteringWriter = new FilteringXmlStreamWriter(writer);
        return filteringWriter;
    }

    @SuppressWarnings("unchecked")
    public Object unmarshal(Exchange exchange, InputStream stream) throws IOException {
        try {
            // must create a new instance of unmarshaller as its not thread safe
            Object answer;
            Unmarshaller unmarshaller = getContext().createUnmarshaller();

            if (getPartClass() != null) {
                // partial unmarshalling
                Source source;
                if (needFiltering(exchange)) {
                    source = new StreamSource(createNonXmlFilterReader(exchange, stream));
                } else {
                    source = new StreamSource(stream);
                }
                answer = unmarshaller.unmarshal(source, getPartialClass(exchange));
            } else {
                if (needFiltering(exchange)) {
                    NonXmlFilterReader reader = createNonXmlFilterReader(exchange, stream);
                    answer = unmarshaller.unmarshal(reader);
                } else  {
                    answer = unmarshaller.unmarshal(stream);
                }
            }

            if (answer instanceof JAXBElement && isIgnoreJAXBElement()) {
                answer = ((JAXBElement<?>)answer).getValue();
            }
            return answer;
        } catch (JAXBException e) {
            throw IOHelper.createIOException(e);
        }
    }

    private NonXmlFilterReader createNonXmlFilterReader(Exchange exchange, InputStream stream) throws UnsupportedEncodingException {
        return new NonXmlFilterReader(new InputStreamReader(stream, IOConverter.getCharsetName(exchange)));
    }

    protected boolean needFiltering(Exchange exchange) {
        // exchange property takes precedence over data format property
        return exchange == null ? filterNonXmlChars : exchange.getProperty(Exchange.FILTER_NON_XML_CHARS, filterNonXmlChars, Boolean.class);
    }

    private synchronized Class getPartialClass(Exchange exchange) {
        if (partialClass == null) {
            partialClass = exchange.getContext().getClassResolver().resolveClass(getPartClass());
        }
        return partialClass;
    }

    // Properties
    // -------------------------------------------------------------------------
    public boolean isIgnoreJAXBElement() {        
        return ignoreJAXBElement;
    }
    
    public void setIgnoreJAXBElement(boolean flag) {
        ignoreJAXBElement = flag;
    }
    
    public synchronized JAXBContext getContext() throws JAXBException {
        if (context == null) {
            context = createContext();
        }
        return context;
    }

    public void setContext(JAXBContext context) {
        this.context = context;
    }

    public String getContextPath() {
        return contextPath;
    }

    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    public boolean isFilterNonXmlChars() {
        return filterNonXmlChars;
    }

    public void setFilterNonXmlChars(boolean filterNonXmlChars) {
        this.filterNonXmlChars = filterNonXmlChars;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public final QName getPartNamespace() {
        return partNamespace;
    }

    public final void setPartNamespace(QName partNamespace) {
        this.partNamespace = partNamespace;
    }

    public final String getPartClass() {
        return partClass;
    }

    public final void setPartClass(String partClass) {
        this.partClass = partClass;
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    protected JAXBContext createContext() throws JAXBException {
        if (contextPath != null) {
            ObjectHelper.notNull(camelContext, "CamelContext", this);
            // use class loader from CamelContext to ensure the JAXB class loading works in various runtimes
            return JAXBContext.newInstance(contextPath, camelContext.getClass().getClassLoader());
        } else {
            return JAXBContext.newInstance();
        }
    }
}

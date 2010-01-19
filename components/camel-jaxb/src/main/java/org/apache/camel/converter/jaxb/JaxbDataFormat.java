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
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.camel.Exchange;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.util.IOHelper;

/**
 * A <a href="http://camel.apache.org/data-format.html">data format</a> ({@link DataFormat})
 * using JAXB2 to marshal to and from XML
 *
 * @version $Revision$
 */
public class JaxbDataFormat implements DataFormat {

    private JAXBContext context;
    private String contextPath;
    private boolean prettyPrint = true;
    private boolean ignoreJAXBElement = true;
    private boolean filterNonXmlChars;
    private String encoding;

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

    void marshal(Exchange exchange, Object graph, OutputStream stream, Marshaller marshaller)
        throws XMLStreamException, JAXBException {
        if (needFiltering(exchange)) {
            XMLStreamWriter writer = XMLOutputFactory.newInstance().createXMLStreamWriter(stream);
            FilteringXmlStreamWriter filteringWriter = new FilteringXmlStreamWriter(writer);
            marshaller.marshal(graph, filteringWriter);
        } else {
            marshaller.marshal(graph, stream);
        }
    }
    
    public Object unmarshal(Exchange exchange, InputStream stream) throws IOException {
        try {
            // must create a new instance of unmarshaller as its not thread safe
            Object answer;
            Unmarshaller unmarshaller = getContext().createUnmarshaller();
            if (needFiltering(exchange)) {
                answer = unmarshaller.unmarshal(new NonXmlFilterReader(new InputStreamReader(stream, IOConverter.getCharsetName(exchange))));
            } else  {
                answer = unmarshaller.unmarshal(stream);
            }

            if (answer instanceof JAXBElement && isIgnoreJAXBElement()) {
                answer = ((JAXBElement<?>)answer).getValue();
            }
            return answer;
        } catch (JAXBException e) {
            throw IOHelper.createIOException(e);
        }
    }
    
    protected boolean needFiltering(Exchange exchange) {
        // exchange property takes precedence over data format property
        return exchange == null ? filterNonXmlChars : exchange.getProperty(Exchange.FILTER_NON_XML_CHARS, filterNonXmlChars, Boolean.class);
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

    protected JAXBContext createContext() throws JAXBException {
        if (contextPath != null) {
            return JAXBContext.newInstance(contextPath);
        } else {
            return JAXBContext.newInstance();
        }
    }
}

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

import java.io.Closeable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Source;

import org.apache.camel.Exchange;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.StreamCache;
import org.apache.camel.TypeConverter;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.spi.TypeConverterAware;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @version $Revision$
 */
public class FallbackTypeConverter implements TypeConverter, TypeConverterAware {
    private static final transient Log LOG = LogFactory.getLog(FallbackTypeConverter.class);
    private Map<Class<?>, JAXBContext> contexts = new HashMap<Class<?>, JAXBContext>();
    private TypeConverter parentTypeConverter;
    private boolean prettyPrint = true;

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    public void setTypeConverter(TypeConverter parentTypeConverter) {
        this.parentTypeConverter = parentTypeConverter;
    }

    public <T> T convertTo(Class<T> type, Object value) {
        return convertTo(type, null, value);
    }

    private <T> boolean isNotStreamCacheType(Class<T> type) {
        return !StreamCache.class.isAssignableFrom(type);
    }

    public <T> T convertTo(Class<T> type, Exchange exchange, Object value) {
        try {
            if (isJaxbType(type)) {
                return unmarshall(type, exchange, value);
            }
            if (value != null) {
                if (isJaxbType(value.getClass()) && isNotStreamCacheType(type)) {
                    return marshall(type, exchange, value);
                }
            }
            return null;
        } catch (Exception e) {
            throw ObjectHelper.wrapCamelExecutionException(exchange, e);
        }
        
    }

    public <T> T mandatoryConvertTo(Class<T> type, Object value) throws NoTypeConversionAvailableException {
        return mandatoryConvertTo(type, null, value);
    }

    public <T> T mandatoryConvertTo(Class<T> type, Exchange exchange, Object value) throws NoTypeConversionAvailableException {
        T answer = convertTo(type, exchange, value);
        if (answer == null) {
            throw new NoTypeConversionAvailableException(value, type);
        }
        return answer;
    }

    protected <T> boolean isJaxbType(Class<T> type) {
        XmlRootElement element = type.getAnnotation(XmlRootElement.class);
        return element != null;
    }

    /**
     * Lets try parse via JAXB
     */
    protected <T> T unmarshall(Class<T> type, Exchange exchange, Object value) throws Exception {
        if (value == null) {
            throw new IllegalArgumentException("Cannot convert from null value to JAXBSource");
        }

        JAXBContext context = createContext(type);
        // must create a new instance of unmarshaller as its not thread safe
        Unmarshaller unmarshaller = context.createUnmarshaller();

        if (parentTypeConverter != null) {
            InputStream inputStream = parentTypeConverter.convertTo(InputStream.class, value);
            if (inputStream != null) {
                Object unmarshalled = unmarshal(unmarshaller, exchange, inputStream);
                return type.cast(unmarshalled);
            }
            Reader reader = parentTypeConverter.convertTo(Reader.class, value);
            if (reader != null) {
                Object unmarshalled = unmarshal(unmarshaller, exchange, reader);
                return type.cast(unmarshalled);
            }
            Source source = parentTypeConverter.convertTo(Source.class, value);
            if (source != null) {
                Object unmarshalled = unmarshal(unmarshaller, exchange, source);
                return type.cast(unmarshalled);
            }
        }

        if (value instanceof String) {
            value = new StringReader((String) value);
        }
        if (value instanceof InputStream || value instanceof Reader) {
            Object unmarshalled = unmarshal(unmarshaller, exchange, value);
            return type.cast(unmarshalled);
        }

        return null;
    }

    protected <T> T marshall(Class<T> type, Exchange exchange, Object value) throws JAXBException, XMLStreamException, FactoryConfigurationError {
        T answer = null;
        if (parentTypeConverter != null) {
            // lets convert the object to a JAXB source and try convert that to
            // the required source
            JAXBContext context = createContext(value.getClass());
            // must create a new instance of marshaller as its not thread safe
            Marshaller marshaller = context.createMarshaller();
            Writer buffer = new StringWriter();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, isPrettyPrint() ? Boolean.TRUE : Boolean.FALSE);
            if (exchange != null && exchange.getProperty(Exchange.CHARSET_NAME, String.class) != null) {
                marshaller.setProperty(Marshaller.JAXB_ENCODING, exchange.getProperty(Exchange.CHARSET_NAME, String.class));
            }
            if (needFiltering(exchange)) {
                XMLStreamWriter writer = XMLOutputFactory.newInstance().createXMLStreamWriter(buffer);
                FilteringXmlStreamWriter filteringWriter = new FilteringXmlStreamWriter(writer);
                marshaller.marshal(value, filteringWriter);
            } else {
                marshaller.marshal(value, buffer);
            }
            answer = parentTypeConverter.convertTo(type, buffer.toString());
        }

        return answer;
    }

    /**
     * Unmarshals the given value with the unmarshaller
     *
     * @param unmarshaller  the unmarshaller
     * @param exchange the exchange 
     * @param value  the stream to unmarshal (will close it after use, also if exception is thrown)
     * @return  the value
     * @throws JAXBException is thrown if an exception occur while unmarshalling
     * @throws UnsupportedEncodingException 
     */
    protected Object unmarshal(Unmarshaller unmarshaller, Exchange exchange, Object value)
        throws JAXBException, UnsupportedEncodingException {
        try {
            if (value instanceof InputStream) {
                if (needFiltering(exchange)) {
                    return unmarshaller.unmarshal(new NonXmlFilterReader(new InputStreamReader((InputStream)value, IOConverter.getCharsetName(exchange))));
                }
                return unmarshaller.unmarshal((InputStream)value);
            } else if (value instanceof Reader) {
                Reader reader = (Reader)value;
                if (needFiltering(exchange)) {
                    if (!(value instanceof NonXmlFilterReader)) {
                        reader = new NonXmlFilterReader((Reader)value);
                    }
                }
                return unmarshaller.unmarshal(reader);
            } else if (value instanceof Source) {
                return unmarshaller.unmarshal((Source)value);
            }
        } finally {
            if (value instanceof Closeable) {
                IOHelper.close((Closeable)value, "Unmarshalling", LOG);
            }
        }
        return null;
    }
    
    protected boolean needFiltering(Exchange exchange) {
        // exchange property takes precedence over data format property
        return exchange != null && exchange.getProperty(Exchange.FILTER_NON_XML_CHARS, Boolean.FALSE, Boolean.class);
    }

    protected synchronized <T> JAXBContext createContext(Class<T> type) throws JAXBException {
        JAXBContext context = contexts.get(type);
        if (context == null) {
            context = JAXBContext.newInstance(type);
            contexts.put(type, context);
        }
        return context;
    }

}

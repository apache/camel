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
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Source;

import org.apache.camel.Exchange;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.Processor;
import org.apache.camel.StreamCache;
import org.apache.camel.TypeConversionException;
import org.apache.camel.TypeConverter;
import org.apache.camel.component.bean.BeanInvocation;
import org.apache.camel.converter.jaxp.StaxConverter;
import org.apache.camel.spi.TypeConverterAware;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version
 */
public class FallbackTypeConverter extends ServiceSupport implements TypeConverter, TypeConverterAware {
    public static final String PRETTY_PRINT = "CamelJaxbPrettyPrint"; 
    private static final Logger LOG = LoggerFactory.getLogger(FallbackTypeConverter.class);
    private final Map<Class<?>, JAXBContext> contexts = new HashMap<Class<?>, JAXBContext>();
    private final StaxConverter staxConverter = new StaxConverter();
    private TypeConverter parentTypeConverter;
    private boolean prettyPrint = true;

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    public boolean allowNull() {
        return false;
    }

    public void setTypeConverter(TypeConverter parentTypeConverter) {
        this.parentTypeConverter = parentTypeConverter;
    }

    public <T> T convertTo(Class<T> type, Object value) {
        return convertTo(type, null, value);
    }

    public <T> T convertTo(Class<T> type, Exchange exchange, Object value) {
        if (BeanInvocation.class.isAssignableFrom(type) || Processor.class.isAssignableFrom(type)) {
            // JAXB cannot convert to a BeanInvocation / Processor, so we need to indicate this
            // to avoid Camel trying to do this when using beans with JAXB payloads
            return null;
        }

        try {
            if (isJaxbType(type)) {
                return unmarshall(type, exchange, value);
            }
            if (value != null) {
                if (isJaxbType(value.getClass()) && isNotStreamCacheType(type)) {
                    return marshall(type, exchange, value);
                }
            }
        } catch (Exception e) {
            throw new TypeConversionException(value, type, e);
        }

        // should return null if didn't even try to convert at all or for whatever reason the conversion is failed
        return null;
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

    public <T> T tryConvertTo(Class<T> type, Object value) {
        try {
            return convertTo(type, null, value);
        } catch (Exception e) {
            return null;
        }
    }

    public <T> T tryConvertTo(Class<T> type, Exchange exchange, Object value) {
        try {
            return convertTo(type, exchange, value);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        contexts.clear();
    }

    protected <T> boolean isJaxbType(Class<T> type) {
        XmlRootElement element = type.getAnnotation(XmlRootElement.class);
        return element != null;
    }

    /**
     * Lets try parse via JAXB
     */
    protected <T> T unmarshall(Class<T> type, Exchange exchange, Object value) throws Exception {
        LOG.trace("Unmarshal to {} with value {}", type, value);

        if (value == null) {
            throw new IllegalArgumentException("Cannot convert from null value to JAXBSource");
        }

        Unmarshaller unmarshaller = getUnmarshaller(type);

        if (parentTypeConverter != null) {
            if (!needFiltering(exchange)) {
                // we cannot filter the XMLStreamReader if necessary
                XMLStreamReader xmlReader = parentTypeConverter.convertTo(XMLStreamReader.class, exchange, value);
                if (xmlReader != null) {
                    try {
                        Object unmarshalled = unmarshal(unmarshaller, exchange, xmlReader);
                        return type.cast(unmarshalled);
                    } catch (Exception ex) {
                        // There is some issue on the StaxStreamReader to CXFPayload message body with different namespaces
                        LOG.debug("Cannot use StaxStreamReader to unmarshal the message, due to {}", ex);
                    }
                }
            }
            InputStream inputStream = parentTypeConverter.convertTo(InputStream.class, exchange, value);
            if (inputStream != null) {
                Object unmarshalled = unmarshal(unmarshaller, exchange, inputStream);
                return type.cast(unmarshalled);
            }
            Reader reader = parentTypeConverter.convertTo(Reader.class, exchange, value);
            if (reader != null) {
                Object unmarshalled = unmarshal(unmarshaller, exchange, reader);
                return type.cast(unmarshalled);
            }
            Source source = parentTypeConverter.convertTo(Source.class, exchange, value);
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

    protected <T> T marshall(Class<T> type, Exchange exchange, Object value)
        throws JAXBException, XMLStreamException, FactoryConfigurationError, TypeConversionException {
        LOG.trace("Marshal from value {} to type {}", value, type);

        T answer = null;
        if (parentTypeConverter != null) {
            // lets convert the object to a JAXB source and try convert that to
            // the required source
            JAXBContext context = createContext(value.getClass());
            // must create a new instance of marshaller as its not thread safe
            Marshaller marshaller = context.createMarshaller();
            Writer buffer = new StringWriter();
            boolean prettyPrint = isPrettyPrint();
            // check the camel context property to decide the value of PrettyPrint
            if (exchange != null) {
                String property = exchange.getContext().getProperty(PRETTY_PRINT);
                if (property != null) {
                    if (property.equalsIgnoreCase("false")) {
                        prettyPrint = false;
                    } else {
                        prettyPrint = true;
                    }
                }
            }
            if (prettyPrint) {
                marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
            }
            if (exchange != null && exchange.getProperty(Exchange.CHARSET_NAME, String.class) != null) {
                marshaller.setProperty(Marshaller.JAXB_ENCODING, exchange.getProperty(Exchange.CHARSET_NAME, String.class));
            }
            if (needFiltering(exchange)) {
                XMLStreamWriter writer = parentTypeConverter.convertTo(XMLStreamWriter.class, buffer);
                FilteringXmlStreamWriter filteringWriter = new FilteringXmlStreamWriter(writer);
                marshaller.marshal(value, filteringWriter);
            } else {
                marshaller.marshal(value, buffer);
            }
            // we need to pass the exchange
            answer = parentTypeConverter.convertTo(type, exchange, buffer.toString());
        }

        return answer;
    }

    protected Object unmarshal(Unmarshaller unmarshaller, Exchange exchange, Object value)
        throws JAXBException, UnsupportedEncodingException, XMLStreamException {
        try {
            XMLStreamReader xmlReader;
            if (value instanceof XMLStreamReader) {
                xmlReader = (XMLStreamReader) value;
            } else if (value instanceof InputStream) {
                if (needFiltering(exchange)) {
                    xmlReader = staxConverter.createXMLStreamReader(new NonXmlFilterReader(new InputStreamReader((InputStream)value, IOHelper.getCharsetName(exchange))));
                } else {
                    xmlReader = staxConverter.createXMLStreamReader((InputStream)value, exchange);
                }
            } else if (value instanceof Reader) {
                Reader reader = (Reader)value;
                if (needFiltering(exchange)) {
                    if (!(value instanceof NonXmlFilterReader)) {
                        reader = new NonXmlFilterReader((Reader)value);
                    }
                }
                xmlReader = staxConverter.createXMLStreamReader(reader);
            } else if (value instanceof Source) {
                xmlReader = staxConverter.createXMLStreamReader((Source)value);
            } else {
                throw new IllegalArgumentException("Cannot convert from " + value.getClass());
            }
            return unmarshaller.unmarshal(xmlReader);
        } finally {
            if (value instanceof Closeable) {
                IOHelper.close((Closeable)value, "Unmarshalling", LOG);
            }
        }
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

    protected <T> Unmarshaller getUnmarshaller(Class<T> type) throws JAXBException {
        JAXBContext context = createContext(type);
        return context.createUnmarshaller();
    }

    private static <T> boolean isNotStreamCacheType(Class<T> type) {
        return !StreamCache.class.isAssignableFrom(type);
    }

}

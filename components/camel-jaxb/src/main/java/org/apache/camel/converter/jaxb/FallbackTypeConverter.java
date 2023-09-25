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
package org.apache.camel.converter.jaxb;

import java.io.Closeable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.XmlRootElement;

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Source;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.StreamCache;
import org.apache.camel.TypeConversionException;
import org.apache.camel.TypeConverter;
import org.apache.camel.converter.jaxp.StaxConverter;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Converter(generateLoader = true)
public class FallbackTypeConverter {

    public static final String PRETTY_PRINT = "CamelJaxbPrettyPrint";
    public static final String OBJECT_FACTORY = "CamelJaxbObjectFactory";

    private static final Logger LOG = LoggerFactory.getLogger(FallbackTypeConverter.class);

    private final Map<AnnotatedElement, JAXBContext> contexts = new HashMap<>();
    private final StaxConverter staxConverter = new StaxConverter();
    private boolean defaultPrettyPrint = true;
    private boolean defaultObjectFactory;

    public boolean isPrettyPrint() {
        return defaultPrettyPrint;
    }

    /**
     * Whether the JAXB converter should use pretty print or not (default is true)
     */
    public void setPrettyPrint(boolean prettyPrint) {
        this.defaultPrettyPrint = prettyPrint;
    }

    public boolean isObjectFactory() {
        return defaultObjectFactory;
    }

    /**
     * Whether the JAXB converter supports using ObjectFactory classes to create the POJO classes during conversion.
     * This only applies to POJO classes that has not been annotated with JAXB and providing jaxb.index descriptor
     * files.
     */
    public void setObjectFactory(boolean objectFactory) {
        this.defaultObjectFactory = objectFactory;
    }

    @Converter(fallback = true)
    public Object convertTo(Class<?> type, Exchange exchange, Object value, TypeConverterRegistry registry) {

        boolean prettyPrint = defaultPrettyPrint;
        String property = exchange != null ? exchange.getContext().getGlobalOption(PRETTY_PRINT) : null;
        if (property != null) {
            if (property.equalsIgnoreCase("false")) {
                prettyPrint = false;
            } else {
                prettyPrint = true;
            }
        }

        // configure object factory
        boolean objectFactory = defaultObjectFactory;
        property = exchange != null ? exchange.getContext().getGlobalOption(OBJECT_FACTORY) : null;
        if (property != null) {
            if (property.equalsIgnoreCase("false")) {
                objectFactory = false;
            } else {
                objectFactory = true;
            }
        }

        TypeConverter converter = null;
        if (registry instanceof TypeConverter) {
            converter = (TypeConverter) registry;
        } else if (exchange != null) {
            converter = exchange.getContext().getTypeConverter();
        }

        try {
            if (isJaxbType(type, exchange, objectFactory)) {
                return unmarshall(type, exchange, value, converter);
            }
            if (value != null && isNotStreamCacheType(type)) {
                if (hasXmlRootElement(value.getClass())) {
                    return marshall(type, exchange, value, converter, null, prettyPrint);
                }
                if (objectFactory) {
                    Method objectFactoryMethod
                            = JaxbHelper.getJaxbElementFactoryMethod(exchange.getContext(), value.getClass());
                    if (objectFactoryMethod != null) {
                        return marshall(type, exchange, value, converter, objectFactoryMethod, prettyPrint);
                    }
                }
            }
        } catch (Exception e) {
            throw new TypeConversionException(value, type, e);
        }

        // should return null if didn't even try to convert at all or for whatever reason the conversion is failed
        return null;
    }

    private <T> boolean hasXmlRootElement(Class<T> type) {
        boolean answer = type.getAnnotation(XmlRootElement.class) != null;
        if (!answer && LOG.isTraceEnabled()) {
            LOG.trace("Class {} is not annotated with @{}", type.getName(), XmlRootElement.class.getName());
        }
        return answer;
    }

    protected <T> boolean isJaxbType(Class<T> type, Exchange exchange, boolean objectFactory) {
        if (objectFactory) {
            return hasXmlRootElement(type) || JaxbHelper.getJaxbElementFactoryMethod(exchange.getContext(), type) != null;
        } else {
            return hasXmlRootElement(type);
        }
    }

    private <T> T castJaxbType(Object o, Class<T> type) {
        if (type.isAssignableFrom(o.getClass())) {
            return type.cast(o);
        } else {
            return type.cast(((JAXBElement<?>) o).getValue());
        }
    }

    /**
     * Lets try parse via JAXB
     */
    protected <T> T unmarshall(Class<T> type, Exchange exchange, Object value, TypeConverter converter) throws Exception {
        LOG.trace("Unmarshal to {} with value {}", type, value);

        if (value == null) {
            throw new IllegalArgumentException("Cannot convert from null value to JAXBSource");
        }

        // Check if the object is a JAXBElement of the correct type
        if (value instanceof JAXBElement) {
            JAXBElement<?> jaxbElement = (JAXBElement<?>) value;
            if (type.isAssignableFrom(jaxbElement.getDeclaredType())) {
                return castJaxbType(jaxbElement, type);
            }
        }

        Unmarshaller unmarshaller = getUnmarshaller(type);

        if (converter != null) {
            if (!needFiltering(exchange)) {
                // we cannot filter the XMLStreamReader if necessary
                XMLStreamReader xmlReader = converter.convertTo(XMLStreamReader.class, exchange, value);
                if (xmlReader != null) {
                    try {
                        Object unmarshalled = unmarshal(unmarshaller, exchange, xmlReader);
                        return castJaxbType(unmarshalled, type);
                    } catch (Exception ex) {
                        // There is some issue on the StaxStreamReader to CXFPayload message body with different namespaces
                        LOG.debug("Cannot use StaxStreamReader to unmarshal the message, due to {}", ex.getMessage(), ex);
                    }
                }
            }
            InputStream inputStream = converter.convertTo(InputStream.class, exchange, value);
            if (inputStream != null) {
                Object unmarshalled = unmarshal(unmarshaller, exchange, inputStream);
                return castJaxbType(unmarshalled, type);
            }
            Reader reader = converter.convertTo(Reader.class, exchange, value);
            if (reader != null) {
                Object unmarshalled = unmarshal(unmarshaller, exchange, reader);
                return castJaxbType(unmarshalled, type);
            }
            Source source = converter.convertTo(Source.class, exchange, value);
            if (source != null) {
                Object unmarshalled = unmarshal(unmarshaller, exchange, source);
                return castJaxbType(unmarshalled, type);
            }
        }

        if (value instanceof String) {
            value = new StringReader((String) value);
        }
        if (value instanceof InputStream || value instanceof Reader) {
            Object unmarshalled = unmarshal(unmarshaller, exchange, value);
            return castJaxbType(unmarshalled, type);
        }

        return null;
    }

    protected <T> T marshall(
            Class<T> type, Exchange exchange, Object value, TypeConverter converter,
            Method objectFactoryMethod, boolean prettyPrint)
            throws JAXBException, FactoryConfigurationError, TypeConversionException {
        LOG.trace("Marshal from value {} to type {}", value, type);

        T answer = null;
        if (converter != null) {
            // lets convert the object to a JAXB source and try convert that to
            // the required source
            JAXBContext context = createContext(value.getClass());
            // must create a new instance of marshaller as its not thread safe
            Marshaller marshaller = context.createMarshaller();
            Writer buffer = new StringWriter();

            if (prettyPrint) {
                marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
            }
            String charset = exchange != null ? exchange.getProperty(ExchangePropertyKey.CHARSET_NAME, String.class) : null;
            if (charset != null) {
                marshaller.setProperty(Marshaller.JAXB_ENCODING, charset);
            }
            Object toMarshall = value;
            if (objectFactoryMethod != null) {
                try {
                    Object instance = objectFactoryMethod.getDeclaringClass().newInstance();
                    if (instance != null) {
                        toMarshall = objectFactoryMethod.invoke(instance, value);
                    }
                } catch (Exception e) {
                    LOG.debug("Unable to create JAXBElement object for type {} due to {}", value.getClass(),
                            e.getMessage(), e);
                }
            }
            if (needFiltering(exchange)) {
                XMLStreamWriter writer = converter.convertTo(XMLStreamWriter.class, buffer);
                FilteringXmlStreamWriter filteringWriter = new FilteringXmlStreamWriter(writer, charset);
                marshaller.marshal(toMarshall, filteringWriter);
            } else {
                marshaller.marshal(toMarshall, buffer);
            }
            // we need to pass the exchange
            answer = converter.convertTo(type, exchange, buffer.toString());
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
                    xmlReader = staxConverter.createXMLStreamReader(new NonXmlFilterReader(
                            new InputStreamReader((InputStream) value, ExchangeHelper.getCharsetName(exchange))));
                } else {
                    xmlReader = staxConverter.createXMLStreamReader((InputStream) value, exchange);
                }
            } else if (value instanceof Reader) {
                Reader reader = (Reader) value;
                if (needFiltering(exchange)) {
                    if (!(value instanceof NonXmlFilterReader)) {
                        reader = new NonXmlFilterReader((Reader) value);
                    }
                }
                xmlReader = staxConverter.createXMLStreamReader(reader);
            } else if (value instanceof Source) {
                xmlReader = staxConverter.createXMLStreamReader((Source) value);
            } else {
                throw new IllegalArgumentException("Cannot convert from " + value.getClass());
            }
            return unmarshaller.unmarshal(xmlReader);
        } finally {
            if (value instanceof Closeable) {
                IOHelper.close((Closeable) value, "Unmarshalling", LOG);
            }
        }
    }

    protected boolean needFiltering(Exchange exchange) {
        // exchange property takes precedence over data format property
        return exchange != null && exchange.getProperty(Exchange.FILTER_NON_XML_CHARS, Boolean.FALSE, Boolean.class);
    }

    protected synchronized <T> JAXBContext createContext(Class<T> type) throws JAXBException {
        AnnotatedElement ae = hasXmlRootElement(type) ? type : type.getPackage();
        JAXBContext context = contexts.get(ae);
        if (context == null) {
            if (hasXmlRootElement(type)) {
                context = JAXBContext.newInstance(type);
                contexts.put(type, context);
            } else {
                context = JAXBContext.newInstance(type.getPackage().getName());
                contexts.put(type.getPackage(), context);
            }
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

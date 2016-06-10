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
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Source;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
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
public class FallbackTypeConverter extends ServiceSupport implements TypeConverter, TypeConverterAware, CamelContextAware {

    public static final String PRETTY_PRINT = "CamelJaxbPrettyPrint";
    public static final String OBJECT_FACTORY = "CamelJaxbObjectFactory";

    private static final Logger LOG = LoggerFactory.getLogger(FallbackTypeConverter.class);
    private final Map<AnnotatedElement, JAXBContext> contexts = new HashMap<>();
    private final StaxConverter staxConverter = new StaxConverter();
    private TypeConverter parentTypeConverter;
    private boolean prettyPrint = true;
    private boolean objectFactory;
    private CamelContext camelContext;

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    /**
     * Whether the JAXB converter should use pretty print or not (default is true)
     */
    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }


    public boolean isObjectFactory() {
        return objectFactory;
    }

    /**
     * Whether the JAXB converter supports using ObjectFactory classes to create the POJO classes during conversion.
     * This only applies to POJO classes that has not been annotated with JAXB and providing jaxb.index descriptor files.
     */
    public void setObjectFactory(boolean objectFactory) {
        this.objectFactory = objectFactory;
    }

    public boolean allowNull() {
        return false;
    }

    public void setTypeConverter(TypeConverter parentTypeConverter) {
        this.parentTypeConverter = parentTypeConverter;
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;

        if (camelContext != null) {
            // configure pretty print
            String property = camelContext.getProperty(PRETTY_PRINT);
            if (property != null) {
                if (property.equalsIgnoreCase("false")) {
                    setPrettyPrint(false);
                } else {
                    setPrettyPrint(true);
                }
            }

            // configure object factory
            property = camelContext.getProperty(OBJECT_FACTORY);
            if (property != null) {
                if (property.equalsIgnoreCase("false")) {
                    setObjectFactory(false);
                } else {
                    setObjectFactory(true);
                }
            }
        }
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
            if (value != null && isNotStreamCacheType(type)) {
                if (hasXmlRootElement(value.getClass())) {
                    return marshall(type, exchange, value, null);
                }
                if (isObjectFactory()) {
                    CamelContext context = exchange != null ? exchange.getContext() : camelContext;
                    Method objectFactoryMethod = JaxbHelper.getJaxbElementFactoryMethod(context, value.getClass());
                    if (objectFactoryMethod != null) {
                        return marshall(type, exchange, value, objectFactoryMethod);
                    }
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
        LOG.info("Jaxb FallbackTypeConverter[prettyPrint={}, objectFactory={}]", prettyPrint, objectFactory);
    }

    @Override
    protected void doStop() throws Exception {
        contexts.clear();
    }

    private <T> boolean hasXmlRootElement(Class<T> type) {
        return type.getAnnotation(XmlRootElement.class) != null;
    }

    protected <T> boolean isJaxbType(Class<T> type) {
        if (isObjectFactory()) {
            return hasXmlRootElement(type) || JaxbHelper.getJaxbElementFactoryMethod(camelContext, type) != null;
        } else {
            return hasXmlRootElement(type);
        }
    }

    private <T> T castJaxbType(Object o, Class<T> type) {
        if (type.isAssignableFrom(o.getClass())) {
            return type.cast(o);
        } else {
            return type.cast(((JAXBElement) o).getValue());
        }
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
                        return castJaxbType(unmarshalled, type);
                    } catch (Exception ex) {
                        // There is some issue on the StaxStreamReader to CXFPayload message body with different namespaces
                        LOG.debug("Cannot use StaxStreamReader to unmarshal the message, due to {}", ex);
                    }
                }
            }
            InputStream inputStream = parentTypeConverter.convertTo(InputStream.class, exchange, value);
            if (inputStream != null) {
                Object unmarshalled = unmarshal(unmarshaller, exchange, inputStream);
                return castJaxbType(unmarshalled, type);
            }
            Reader reader = parentTypeConverter.convertTo(Reader.class, exchange, value);
            if (reader != null) {
                Object unmarshalled = unmarshal(unmarshaller, exchange, reader);
                return castJaxbType(unmarshalled, type);
            }
            Source source = parentTypeConverter.convertTo(Source.class, exchange, value);
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

    protected <T> T marshall(Class<T> type, Exchange exchange, Object value, Method objectFactoryMethod)
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

            if (isPrettyPrint()) {
                marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
            }
            if (exchange != null && exchange.getProperty(Exchange.CHARSET_NAME, String.class) != null) {
                marshaller.setProperty(Marshaller.JAXB_ENCODING, exchange.getProperty(Exchange.CHARSET_NAME, String.class));
            }
            Object toMarshall = value;
            if (objectFactoryMethod != null) {
                try {
                    Object instance = objectFactoryMethod.getDeclaringClass().newInstance();
                    if (instance != null) {
                        toMarshall = objectFactoryMethod.invoke(instance, value);
                    }
                } catch (Exception e) {
                    LOG.debug("Unable to create JAXBElement object for type " + value.getClass() + " due to " + e.getMessage(), e);
                }
            }
            if (needFiltering(exchange)) {
                XMLStreamWriter writer = parentTypeConverter.convertTo(XMLStreamWriter.class, buffer);
                FilteringXmlStreamWriter filteringWriter = new FilteringXmlStreamWriter(writer);
                marshaller.marshal(toMarshall, filteringWriter);
            } else {
                marshaller.marshal(toMarshall, buffer);
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

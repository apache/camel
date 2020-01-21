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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.JAXBIntrospector;
import javax.xml.bind.MarshalException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.ValidationEvent;
import javax.xml.bind.ValidationEventHandler;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.xml.sax.SAXException;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.TypeConverter;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatContentTypeHeader;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A <a href="http://camel.apache.org/data-format.html">data format</a> ({@link DataFormat})
 * using JAXB2 to marshal to and from XML
 */
@Dataformat("jaxb")
public class JaxbDataFormat extends ServiceSupport implements DataFormat, DataFormatName, DataFormatContentTypeHeader, CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(JaxbDataFormat.class);

    private static final BlockingQueue<SchemaFactory> SCHEMA_FACTORY_POOL = new LinkedBlockingQueue<>();

    private SchemaFactory schemaFactory;
    private CamelContext camelContext;
    private JAXBContext context;
    private JAXBIntrospector introspector;
    private String contextPath;
    private String schema;
    private int schemaSeverityLevel; // 0 = warning, 1 = error, 2 = fatal
    private String schemaLocation;
    private String noNamespaceSchemaLocation;

    private boolean prettyPrint = true;
    private boolean objectFactory = true;
    private boolean ignoreJAXBElement = true;
    private boolean mustBeJAXBElement;
    private boolean filterNonXmlChars;
    private String encoding;
    private boolean fragment;
    // partial support
    private QName partNamespace;
    private Class<?> partClass;
    private Map<String, String> namespacePrefix;
    private JaxbNamespacePrefixMapper namespacePrefixMapper;
    private JaxbXmlStreamWriterWrapper xmlStreamWriterWrapper;
    private TypeConverter typeConverter;
    private Schema cachedSchema;
    private Map<String, Object> jaxbProviderProperties;
    private boolean contentTypeHeader = true;

    public JaxbDataFormat() {
    }

    public JaxbDataFormat(JAXBContext context) {
        this.context = context;
    }

    public JaxbDataFormat(String contextPath) {
        this.contextPath = contextPath;
    }

    @Override
    public String getDataFormatName() {
        return "jaxb";
    }

    @Override
    public void marshal(Exchange exchange, Object graph, OutputStream stream) throws IOException {
        try {
            // must create a new instance of marshaller as its not thread safe
            Marshaller marshaller = createMarshaller();

            if (isPrettyPrint()) {
                marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
            }
            // exchange take precedence over encoding option
            String charset = exchange.getProperty(Exchange.CHARSET_NAME, String.class);
            if (charset == null) {
                charset = encoding;
                //Propagate the encoding of the exchange
                if (charset != null) {
                    exchange.setProperty(Exchange.CHARSET_NAME, charset);
                }
            }
            if (charset != null) {
                marshaller.setProperty(Marshaller.JAXB_ENCODING, charset);
            }
            if (isFragment()) {
                marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
            }
            if (ObjectHelper.isNotEmpty(schemaLocation)) {
                marshaller.setProperty(Marshaller.JAXB_SCHEMA_LOCATION, schemaLocation);
            }
            if (ObjectHelper.isNotEmpty(noNamespaceSchemaLocation)) {
                marshaller.setProperty(Marshaller.JAXB_NO_NAMESPACE_SCHEMA_LOCATION, noNamespaceSchemaLocation);
            }
            if (namespacePrefixMapper != null) {
                marshaller.setProperty(namespacePrefixMapper.getRegistrationKey(), namespacePrefixMapper);
            }
            // Inject any JAX-RI custom properties from the exchange or from the instance into the marshaller
            Map<String, Object> customProperties = exchange.getProperty(JaxbConstants.JAXB_PROVIDER_PROPERTIES, Map.class);
            if (customProperties == null) {
                customProperties = getJaxbProviderProperties();
            }
            if (customProperties != null) {
                for (Entry<String, Object> property : customProperties.entrySet()) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Using JAXB Provider Property {}={}", property.getKey(), property.getValue());
                    }
                    marshaller.setProperty(property.getKey(), property.getValue());
                }
            }
            doMarshal(exchange, graph, stream, marshaller, charset);

            if (contentTypeHeader) {
                if (exchange.hasOut()) {
                    exchange.getOut().setHeader(Exchange.CONTENT_TYPE, "application/xml");
                } else {
                    exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/xml");
                }
            }
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    void doMarshal(Exchange exchange, Object graph, OutputStream stream, Marshaller marshaller, String charset) throws Exception {

        Object element = graph;
        QName partNamespaceOnDataFormat = getPartNamespace();
        String partClassFromHeader = exchange.getIn().getHeader(JaxbConstants.JAXB_PART_CLASS, String.class);
        String partNamespaceFromHeader = exchange.getIn().getHeader(JaxbConstants.JAXB_PART_NAMESPACE, String.class);
        if ((partClass != null || partClassFromHeader != null)
                && (partNamespaceOnDataFormat != null || partNamespaceFromHeader != null)) {
            if (partClassFromHeader != null) {
                try {
                    partClass = camelContext.getClassResolver().resolveMandatoryClass(partClassFromHeader, Object.class);
                } catch (ClassNotFoundException e) {
                    throw new JAXBException(e);
                }
            }
            if (partNamespaceFromHeader != null) {
                partNamespaceOnDataFormat = QName.valueOf(partNamespaceFromHeader);
            }
            element = new JAXBElement<>(partNamespaceOnDataFormat, (Class<Object>) partClass, graph);
        }

        // only marshal if its possible
        if (introspector.isElement(element)) {
            if (asXmlStreamWriter(exchange)) {
                XMLStreamWriter writer = typeConverter.convertTo(XMLStreamWriter.class, exchange, stream);
                if (needFiltering(exchange)) {
                    writer = new FilteringXmlStreamWriter(writer, charset);
                }
                if (xmlStreamWriterWrapper != null) {
                    writer = xmlStreamWriterWrapper.wrapWriter(writer);
                }
                marshaller.marshal(element, writer);
            } else {
                marshaller.marshal(element, stream);
            }
            return;
        } else if (objectFactory && element != null) {
            Method objectFactoryMethod = JaxbHelper.getJaxbElementFactoryMethod(camelContext, element.getClass());
            if (objectFactoryMethod != null) {
                try {
                    Object instance = objectFactoryMethod.getDeclaringClass().newInstance();
                    if (instance != null) {
                        Object toMarshall = objectFactoryMethod.invoke(instance, element);
                        if (asXmlStreamWriter(exchange)) {
                            XMLStreamWriter writer = typeConverter.convertTo(XMLStreamWriter.class, exchange, stream);
                            if (needFiltering(exchange)) {
                                writer = new FilteringXmlStreamWriter(writer, charset);
                            }
                            if (xmlStreamWriterWrapper != null) {
                                writer = xmlStreamWriterWrapper.wrapWriter(writer);
                            }
                            marshaller.marshal(toMarshall, writer);
                        } else {
                            marshaller.marshal(toMarshall, stream);
                        }
                        return;
                    }
                } catch (Exception e) {
                    // if a schema is set then an MarshallException is thrown when the XML is not valid
                    // and the method must throw this exception as it would when the object in the body is a root element
                    // or a partial class (the other alternatives above)
                    // 
                    // it would be best to completely remove the exception handler here but it's left for backwards compatibility reasons.
                    if (MarshalException.class.isAssignableFrom(e.getClass()) && schema != null) {
                        throw e;
                    }
                    
                    LOG.debug("Unable to create JAXBElement object for type " + element.getClass() + " due to " + e.getMessage(), e);
                }
            }
        }

        // cannot marshal
        if (!mustBeJAXBElement) {
            // write the graph as is to the output stream
            if (LOG.isDebugEnabled()) {
                LOG.debug("Attempt to marshalling non JAXBElement with type {} as InputStream", ObjectHelper.classCanonicalName(graph));
            }
            InputStream is = exchange.getContext().getTypeConverter().mandatoryConvertTo(InputStream.class, exchange, graph);
            IOHelper.copyAndCloseInput(is, stream);
        } else {
            throw new InvalidPayloadException(exchange, JAXBElement.class);
        }
    }
    
    private boolean asXmlStreamWriter(Exchange exchange) {
        return needFiltering(exchange) || (xmlStreamWriterWrapper != null);
    }

    @Override
    public Object unmarshal(Exchange exchange, InputStream stream) throws IOException {
        try {
            Object answer;

            final XMLStreamReader xmlReader;
            if (needFiltering(exchange)) {
                xmlReader = typeConverter.convertTo(XMLStreamReader.class, exchange, createNonXmlFilterReader(exchange, stream));
            } else {
                xmlReader = typeConverter.convertTo(XMLStreamReader.class, exchange, stream);
            }
            String partClassFromHeader = exchange.getIn().getHeader(JaxbConstants.JAXB_PART_CLASS, String.class);
            if (partClass != null || partClassFromHeader != null) {
                // partial unmarshalling
                if (partClassFromHeader != null) {
                    try {
                        partClass = camelContext.getClassResolver().resolveMandatoryClass(partClassFromHeader, Object.class);
                    } catch (ClassNotFoundException e) {
                        throw new JAXBException(e);
                    }
                }
                answer = createUnmarshaller().unmarshal(xmlReader, partClass);
            } else {
                answer = createUnmarshaller().unmarshal(xmlReader);
            }

            if (answer instanceof JAXBElement && isIgnoreJAXBElement()) {
                answer = ((JAXBElement<?>)answer).getValue();
            }
            return answer;
        } catch (JAXBException e) {
            throw new IOException(e);
        }
    }

    private NonXmlFilterReader createNonXmlFilterReader(Exchange exchange, InputStream stream) throws UnsupportedEncodingException {
        return new NonXmlFilterReader(new InputStreamReader(stream, ExchangeHelper.getCharsetName(exchange)));
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

    public boolean isMustBeJAXBElement() {
        return mustBeJAXBElement;
    }

    public void setMustBeJAXBElement(boolean mustBeJAXBElement) {
        this.mustBeJAXBElement = mustBeJAXBElement;
    }

    public JAXBContext getContext() {
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

    public SchemaFactory getSchemaFactory() {
        if (schemaFactory == null) {
            return getOrCreateSchemaFactory();
        }
        return schemaFactory;
    }

    public void setSchemaFactory(SchemaFactory schemaFactory) {
        this.schemaFactory = schemaFactory;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public int getSchemaSeverityLevel() {
        return schemaSeverityLevel;
    }

    public void setSchemaSeverityLevel(int schemaSeverityLevel) {
        this.schemaSeverityLevel = schemaSeverityLevel;
    }

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    public boolean isObjectFactory() {
        return objectFactory;
    }

    public void setObjectFactory(boolean objectFactory) {
        this.objectFactory = objectFactory;
    }

    public boolean isFragment() {
        return fragment;
    }

    public void setFragment(boolean fragment) {
        this.fragment = fragment;
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

    public QName getPartNamespace() {
        return partNamespace;
    }

    public void setPartNamespace(QName partNamespace) {
        this.partNamespace = partNamespace;
    }

    public Class<?> getPartClass() {
        return partClass;
    }

    public void setPartClass(Class<?> partClass) {
        this.partClass = partClass;
    }

    public Map<String, String> getNamespacePrefix() {
        return namespacePrefix;
    }

    public void setNamespacePrefix(Map<String, String> namespacePrefix) {
        this.namespacePrefix = namespacePrefix;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public JaxbXmlStreamWriterWrapper getXmlStreamWriterWrapper() {
        return xmlStreamWriterWrapper;
    }

    public void setXmlStreamWriterWrapper(JaxbXmlStreamWriterWrapper xmlStreamWriterWrapper) {
        this.xmlStreamWriterWrapper = xmlStreamWriterWrapper;
    }

    public String getSchemaLocation() {
        return schemaLocation;
    }

    public void setSchemaLocation(String schemaLocation) {
        this.schemaLocation = schemaLocation;
    }

    public String getNoNamespaceSchemaLocation() {
        return schemaLocation;
    }

    public void setNoNamespaceSchemaLocation(String schemaLocation) {
        this.noNamespaceSchemaLocation = schemaLocation;
    }

    public Map<String, Object> getJaxbProviderProperties() {
        return jaxbProviderProperties;
    }

    public void setJaxbProviderProperties(Map<String, Object> jaxbProviderProperties) {
        this.jaxbProviderProperties = jaxbProviderProperties;
    }

    public boolean isContentTypeHeader() {
        return contentTypeHeader;
    }

    /**
     * If enabled then JAXB will set the Content-Type header to <tt>application/xml</tt> when marshalling.
     */
    public void setContentTypeHeader(boolean contentTypeHeader) {
        this.contentTypeHeader = contentTypeHeader;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doStart() throws Exception {
        ObjectHelper.notNull(camelContext, "CamelContext");

        if (context == null) {
            // if context not injected, create one and resolve partial class up front so they are ready to be used
            context = createContext();
        }
        introspector = context.createJAXBIntrospector();

        if (namespacePrefix != null) {
            namespacePrefixMapper = NamespacePrefixMapperFactory.newNamespacePrefixMapper(camelContext, namespacePrefix);
        }
        typeConverter = camelContext.getTypeConverter();
        if (schema != null) {
            cachedSchema = createSchema(getSources());
        }

        LOG.debug("JaxbDataFormat [prettyPrint={}, objectFactory={}]", prettyPrint, objectFactory);
    }

    @Override
    protected void doStop() throws Exception {
    }

    /**
     * Strategy to create JAXB context
     */
    protected JAXBContext createContext() throws JAXBException {
        if (contextPath != null) {
            // prefer to use application class loader which is most likely to be able to
            // load the class which has been JAXB annotated
            ClassLoader cl = camelContext.getApplicationContextClassLoader();
            if (cl != null) {
                LOG.debug("Creating JAXBContext with contextPath: " + contextPath + " and ApplicationContextClassLoader: " + cl);
                return JAXBContext.newInstance(contextPath, cl);
            } else {
                LOG.debug("Creating JAXBContext with contextPath: {}", contextPath);
                return JAXBContext.newInstance(contextPath);
            }
        } else {
            LOG.debug("Creating JAXBContext");
            return JAXBContext.newInstance();
        }
    }

    protected Unmarshaller createUnmarshaller() throws JAXBException {
        Unmarshaller unmarshaller = getContext().createUnmarshaller();
        if (schema != null) {
            unmarshaller.setSchema(cachedSchema);
            unmarshaller.setEventHandler(new ValidationEventHandler() {
                public boolean handleEvent(ValidationEvent event) {
                    // continue if the severity is lower than the configured level
                    return event.getSeverity() < getSchemaSeverityLevel();
                }
            });
        }

        return unmarshaller;
    }

    protected Marshaller createMarshaller() throws JAXBException {
        Marshaller marshaller = getContext().createMarshaller();
        if (schema != null) {
            marshaller.setSchema(cachedSchema);
            marshaller.setEventHandler(new ValidationEventHandler() {
                public boolean handleEvent(ValidationEvent event) {
                    // continue if the severity is lower than the configured level
                    return event.getSeverity() < getSchemaSeverityLevel();
                }
            });
        }

        return marshaller;
    }

    private Schema createSchema(Source[] sources) throws SAXException {
        SchemaFactory factory = getOrCreateSchemaFactory();
        try {
            return factory.newSchema(sources);
        } finally {
            returnSchemaFactory(factory);
        }
    }

    private Source[] getSources() throws FileNotFoundException, MalformedURLException {
        // we support multiple schema by delimiting they by ','
        String[] schemas = schema.split(",");
        Source[] sources = new Source[schemas.length];
        for (int i = 0; i < schemas.length; i++) {
            URL schemaUrl = ResourceHelper.resolveMandatoryResourceAsUrl(camelContext.getClassResolver(), schemas[i]);
            sources[i] = new StreamSource(schemaUrl.toExternalForm());
        }
        return sources;
    }

    private SchemaFactory getOrCreateSchemaFactory() {
        SchemaFactory factory = SCHEMA_FACTORY_POOL.poll();
        if (factory == null) {
            factory = createSchemaFactory();
        }
        return factory;
    }

    public static SchemaFactory createSchemaFactory() {
        return SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
    }

    private void returnSchemaFactory(SchemaFactory factory) {
        if (factory != schemaFactory) {
            SCHEMA_FACTORY_POOL.offer(factory);
        }
    }

}

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

package org.apache.camel.model.dataformat;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.builder.DataFormatBuilder;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.Metadata;

/**
 * Marshal Java objects to SOAP messages and back.
 */
@Metadata(firstVersion = "2.3.0", label = "dataformat,transformation,xml", title = "SOAP")
@XmlRootElement(name = "soap")
@XmlAccessorType(XmlAccessType.FIELD)
public class SoapDataFormat extends DataFormatDefinition {

    @XmlTransient
    private Object elementNameStrategyObject;

    @XmlAttribute(required = true)
    private String contextPath;

    @XmlAttribute
    private String encoding;

    @XmlAttribute
    @Metadata(label = "advanced", javaType = "org.apache.camel.dataformat.soap.name.ElementNameStrategy")
    private String elementNameStrategy;

    @XmlAttribute
    @Metadata(defaultValue = "1.1", enums = "1.1,1.2")
    private String version;

    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.util.Map")
    private String namespacePrefix;

    @XmlAttribute
    private String schema;

    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
    private String ignoreUnmarshalledHeaders;

    public SoapDataFormat() {
        super("soap");
    }

    protected SoapDataFormat(SoapDataFormat source) {
        super(source);
        this.contextPath = source.contextPath;
        this.encoding = source.encoding;
        this.elementNameStrategy = source.elementNameStrategy;
        this.elementNameStrategyObject = source.elementNameStrategyObject;
        this.version = source.version;
        this.namespacePrefix = source.namespacePrefix;
        this.schema = source.schema;
        this.ignoreUnmarshalledHeaders = source.ignoreUnmarshalledHeaders;
    }

    public SoapDataFormat(String contextPath) {
        this();
        setContextPath(contextPath);
    }

    public SoapDataFormat(String contextPath, String elementNameStrategyRef) {
        this();
        setContextPath(contextPath);
        setElementNameStrategy(elementNameStrategyRef);
    }

    public SoapDataFormat(String contextPath, Object elementNameStrategyObject) {
        this();
        setContextPath(contextPath);
        setElementNameStrategyObject(elementNameStrategyObject);
    }

    private SoapDataFormat(Builder builder) {
        this();
        this.contextPath = builder.contextPath;
        this.encoding = builder.encoding;
        this.elementNameStrategy = builder.elementNameStrategy;
        this.elementNameStrategyObject = builder.elementNameStrategyObject;
        this.version = builder.version;
        this.namespacePrefix = builder.namespacePrefix;
        this.schema = builder.schema;
        this.ignoreUnmarshalledHeaders = builder.ignoreUnmarshalledHeaders;
    }

    @Override
    public SoapDataFormat copyDefinition() {
        return new SoapDataFormat(this);
    }

    /**
     * Package name where your JAXB classes are located.
     */
    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    public String getContextPath() {
        return contextPath;
    }

    /**
     * To overrule and use a specific encoding
     */
    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public String getEncoding() {
        return encoding;
    }

    /**
     * Refers to an element strategy to lookup from the registry.
     * <p/>
     * An element name strategy is used for two purposes. The first is to find a xml element name for a given object and
     * soap action when marshaling the object into a SOAP message. The second is to find an Exception class for a given
     * soap fault name.
     * <p/>
     * The following three element strategy class name is provided out of the box. QNameStrategy - Uses a fixed qName
     * that is configured on instantiation. Exception lookup is not supported TypeNameStrategy - Uses the name and
     * namespace from the @XMLType annotation of the given type. If no namespace is set then package-info is used.
     * Exception lookup is not supported ServiceInterfaceStrategy - Uses information from a webservice interface to
     * determine the type name and to find the exception class for a SOAP fault
     * <p/>
     * All three classes is located in the package name org.apache.camel.dataformat.soap.name
     * <p/>
     * If you have generated the web service stub code with cxf-codegen or a similar tool then you probably will want to
     * use the ServiceInterfaceStrategy. In the case you have no annotated service interface you should use
     * QNameStrategy or TypeNameStrategy.
     */
    public void setElementNameStrategy(String elementNameStrategy) {
        this.elementNameStrategy = elementNameStrategy;
    }

    public String getElementNameStrategy() {
        return elementNameStrategy;
    }

    public String getVersion() {
        return version;
    }

    /**
     * SOAP version should either be 1.1 or 1.2.
     * <p/>
     * Is by default 1.1
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * Sets an element strategy instance to use.
     * <p/>
     * An element name strategy is used for two purposes. The first is to find a xml element name for a given object and
     * soap action when marshaling the object into a SOAP message. The second is to find an Exception class for a given
     * soap fault name.
     * <p/>
     * The following three element strategy class name is provided out of the box. QNameStrategy - Uses a fixed qName
     * that is configured on instantiation. Exception lookup is not supported TypeNameStrategy - Uses the name and
     * namespace from the @XMLType annotation of the given type. If no namespace is set then package-info is used.
     * Exception lookup is not supported ServiceInterfaceStrategy - Uses information from a webservice interface to
     * determine the type name and to find the exception class for a SOAP fault
     * <p/>
     * All three classes is located in the package name org.apache.camel.dataformat.soap.name
     * <p/>
     * If you have generated the web service stub code with cxf-codegen or a similar tool then you probably will want to
     * use the ServiceInterfaceStrategy. In the case you have no annotated service interface you should use
     * QNameStrategy or TypeNameStrategy.
     */
    public void setElementNameStrategyObject(Object elementNameStrategyObject) {
        this.elementNameStrategyObject = elementNameStrategyObject;
    }

    public Object getElementNameStrategyObject() {
        return elementNameStrategyObject;
    }

    public String getNamespacePrefix() {
        return namespacePrefix;
    }

    /**
     * When marshalling using JAXB or SOAP then the JAXB implementation will automatic assign namespace prefixes, such
     * as ns2, ns3, ns4 etc. To control this mapping, Camel allows you to refer to a map which contains the desired
     * mapping.
     */
    public void setNamespacePrefix(String namespacePrefix) {
        this.namespacePrefix = namespacePrefix;
    }

    public String getSchema() {
        return schema;
    }

    /**
     * To validate against an existing schema. Your can use the prefix classpath:, file:* or *http: to specify how the
     * resource should be resolved. You can separate multiple schema files by using the ',' character.
     */
    public void setSchema(String schema) {
        this.schema = schema;
    }

    public String getIgnoreUnmarshalledHeaders() {
        return ignoreUnmarshalledHeaders;
    }

    /**
     * Whether to ignore headers that was not unmarshalled. By default, headers which could not be unmarshalled is
     * recorded in the org.apache.camel.dataformat.soap.UNMARSHALLED_HEADER_LIST header which allows to inspect any
     * problematic header.
     */
    public void setIgnoreUnmarshalledHeaders(String ignoreUnmarshalledHeaders) {
        this.ignoreUnmarshalledHeaders = ignoreUnmarshalledHeaders;
    }

    /**
     * {@code Builder} is a specific builder for {@link SoapDataFormat}.
     */
    @XmlTransient
    public static class Builder implements DataFormatBuilder<SoapDataFormat> {

        private String contextPath;
        private String encoding;
        private String elementNameStrategy;
        private Object elementNameStrategyObject;
        private String version;
        private String namespacePrefix;
        private String schema;
        private String ignoreUnmarshalledHeaders;

        /**
         * Package name where your JAXB classes are located.
         */
        public Builder contextPath(String contextPath) {
            this.contextPath = contextPath;
            return this;
        }

        /**
         * To overrule and use a specific encoding
         */
        public Builder encoding(String encoding) {
            this.encoding = encoding;
            return this;
        }

        /**
         * Refers to an element strategy to lookup from the registry.
         * <p/>
         * An element name strategy is used for two purposes. The first is to find a xml element name for a given object
         * and soap action when marshaling the object into a SOAP message. The second is to find an Exception class for
         * a given soap fault name.
         * <p/>
         * The following three element strategy class name is provided out of the box. QNameStrategy - Uses a fixed
         * qName that is configured on instantiation. Exception lookup is not supported TypeNameStrategy - Uses the name
         * and namespace from the @XMLType annotation of the given type. If no namespace is set then package-info is
         * used. Exception lookup is not supported ServiceInterfaceStrategy - Uses information from a webservice
         * interface to determine the type name and to find the exception class for a SOAP fault
         * <p/>
         * All three classes is located in the package name org.apache.camel.dataformat.soap.name
         * <p/>
         * If you have generated the web service stub code with cxf-codegen or a similar tool then you probably will
         * want to use the ServiceInterfaceStrategy. In the case you have no annotated service interface you should use
         * QNameStrategy or TypeNameStrategy.
         */
        public Builder elementNameStrategy(String elementNameStrategy) {
            this.elementNameStrategy = elementNameStrategy;
            return this;
        }

        /**
         * SOAP version should either be 1.1 or 1.2.
         * <p/>
         * Is by default 1.1
         */
        public Builder version(String version) {
            this.version = version;
            return this;
        }

        /**
         * Sets an element strategy instance to use.
         * <p/>
         * An element name strategy is used for two purposes. The first is to find a xml element name for a given object
         * and soap action when marshaling the object into a SOAP message. The second is to find an Exception class for
         * a given soap fault name.
         * <p/>
         * The following three element strategy class name is provided out of the box. QNameStrategy - Uses a fixed
         * qName that is configured on instantiation. Exception lookup is not supported TypeNameStrategy - Uses the name
         * and namespace from the @XMLType annotation of the given type. If no namespace is set then package-info is
         * used. Exception lookup is not supported ServiceInterfaceStrategy - Uses information from a webservice
         * interface to determine the type name and to find the exception class for a SOAP fault
         * <p/>
         * All three classes is located in the package name org.apache.camel.dataformat.soap.name
         * <p/>
         * If you have generated the web service stub code with cxf-codegen or a similar tool then you probably will
         * want to use the ServiceInterfaceStrategy. In the case you have no annotated service interface you should use
         * QNameStrategy or TypeNameStrategy.
         */
        public Builder elementNameStrategyObject(Object elementNameStrategyObject) {
            this.elementNameStrategyObject = elementNameStrategyObject;
            return this;
        }

        /**
         * When marshalling using JAXB or SOAP then the JAXB implementation will automatic assign namespace prefixes,
         * such as ns2, ns3, ns4 etc. To control this mapping, Camel allows you to refer to a map which contains the
         * desired mapping.
         */
        public Builder namespacePrefix(String namespacePrefix) {
            this.namespacePrefix = namespacePrefix;
            return this;
        }

        /**
         * To validate against an existing schema. Your can use the prefix classpath:, file:* or *http: to specify how
         * the resource should be resolved. You can separate multiple schema files by using the ',' character.
         */
        public Builder schema(String schema) {
            this.schema = schema;
            return this;
        }

        /**
         * Whether to ignore headers that was not unmarshalled. By default, headers which could not be unmarshalled is
         * recorded in the org.apache.camel.dataformat.soap.UNMARSHALLED_HEADER_LIST header which allows to inspect any
         * problematic header.
         */
        public Builder ignoreUnmarshalledHeaders(boolean ignoreUnmarshalledHeaders) {
            return ignoreUnmarshalledHeaders(Boolean.valueOf(ignoreUnmarshalledHeaders));
        }

        /**
         * Whether to ignore headers that was not unmarshalled. By default, headers which could not be unmarshalled is
         * recorded in the org.apache.camel.dataformat.soap.UNMARSHALLED_HEADER_LIST header which allows to inspect any
         * problematic header.
         */
        public Builder ignoreUnmarshalledHeaders(String ignoreUnmarshalledHeaders) {
            this.ignoreUnmarshalledHeaders = ignoreUnmarshalledHeaders;
            return this;
        }

        @Override
        public SoapDataFormat end() {
            return new SoapDataFormat(this);
        }
    }
}

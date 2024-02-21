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
 * Unmarshal XML payloads to POJOs and back using JAXB2 XML marshalling standard.
 */
@Metadata(firstVersion = "1.0.0", label = "dataformat,transformation,xml", title = "JAXB")
@XmlRootElement(name = "jaxb")
@XmlAccessorType(XmlAccessType.FIELD)
public class JaxbDataFormat extends DataFormatDefinition implements ContentTypeHeaderAware {

    @XmlAttribute(required = true)
    private String contextPath;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String contextPathIsClassName;
    @XmlAttribute
    private String schema;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Integer", enums = "0,1,2", defaultValue = "0")
    private String schemaSeverityLevel;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String prettyPrint;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
    private String objectFactory;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
    private String ignoreJAXBElement;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
    private String mustBeJAXBElement;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
    private String filterNonXmlChars;
    @XmlAttribute
    private String encoding;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
    private String fragment;
    // Partial encoding
    @XmlAttribute
    @Metadata(label = "advanced")
    private String partClass;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String partNamespace;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String namespacePrefixRef;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String xmlStreamWriterWrapper;
    @XmlAttribute
    private String schemaLocation;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String noNamespaceSchemaLocation;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String jaxbProviderProperties;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean", defaultValue = "true",
              description = "Whether the data format should set the Content-Type header with the type from the data format."
                            + " For example application/xml for data formats marshalling to XML, or application/json for data formats marshalling to JSON")
    private String contentTypeHeader;
    @XmlAttribute
    @Metadata(label = "security", defaultValue = "false")
    private String accessExternalSchemaProtocols;

    public JaxbDataFormat() {
        super("jaxb");
    }

    public JaxbDataFormat(boolean prettyPrint) {
        this();
        setPrettyPrint(Boolean.toString(prettyPrint));
    }

    private JaxbDataFormat(Builder builder) {
        this();
        this.contextPath = builder.contextPath;
        this.contextPathIsClassName = builder.contextPathIsClassName;
        this.schema = builder.schema;
        this.schemaSeverityLevel = builder.schemaSeverityLevel;
        this.prettyPrint = builder.prettyPrint;
        this.objectFactory = builder.objectFactory;
        this.ignoreJAXBElement = builder.ignoreJAXBElement;
        this.mustBeJAXBElement = builder.mustBeJAXBElement;
        this.filterNonXmlChars = builder.filterNonXmlChars;
        this.encoding = builder.encoding;
        this.fragment = builder.fragment;
        this.partClass = builder.partClass;
        this.partNamespace = builder.partNamespace;
        this.namespacePrefixRef = builder.namespacePrefixRef;
        this.xmlStreamWriterWrapper = builder.xmlStreamWriterWrapper;
        this.schemaLocation = builder.schemaLocation;
        this.noNamespaceSchemaLocation = builder.noNamespaceSchemaLocation;
        this.jaxbProviderProperties = builder.jaxbProviderProperties;
        this.contentTypeHeader = builder.contentTypeHeader;
        this.accessExternalSchemaProtocols = builder.accessExternalSchemaProtocols;
    }

    public String getContextPath() {
        return contextPath;
    }

    /**
     * Package name where your JAXB classes are located.
     */
    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    public String getContextPathIsClassName() {
        return contextPathIsClassName;
    }

    /**
     * This can be set to true to mark that the contextPath is referring to a classname and not a package name.
     */
    public void setContextPathIsClassName(String contextPathIsClassName) {
        this.contextPathIsClassName = contextPathIsClassName;
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

    public String getSchemaSeverityLevel() {
        return schemaSeverityLevel;
    }

    /**
     * Sets the schema severity level to use when validating against a schema. This level determines the minimum
     * severity error that triggers JAXB to stop continue parsing. The default value of 0 (warning) means that any error
     * (warning, error or fatal error) will trigger JAXB to stop. There are the following three levels: 0=warning,
     * 1=error, 2=fatal error.
     */
    public void setSchemaSeverityLevel(String schemaSeverityLevel) {
        this.schemaSeverityLevel = schemaSeverityLevel;
    }

    public String getPrettyPrint() {
        return prettyPrint;
    }

    /**
     * To enable pretty printing output nicely formatted.
     * <p/>
     * Is by default false.
     */
    public void setPrettyPrint(String prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    public String getObjectFactory() {
        return objectFactory;
    }

    /**
     * Whether to allow using ObjectFactory classes to create the POJO classes during marshalling. This only applies to
     * POJO classes that has not been annotated with JAXB and providing jaxb.index descriptor files.
     */
    public void setObjectFactory(String objectFactory) {
        this.objectFactory = objectFactory;
    }

    public String getIgnoreJAXBElement() {
        return ignoreJAXBElement;
    }

    /**
     * Whether to ignore JAXBElement elements - only needed to be set to false in very special use-cases.
     */
    public void setIgnoreJAXBElement(String ignoreJAXBElement) {
        this.ignoreJAXBElement = ignoreJAXBElement;
    }

    public String getMustBeJAXBElement() {
        return mustBeJAXBElement;
    }

    /**
     * Whether marhsalling must be java objects with JAXB annotations. And if not then it fails. This option can be set
     * to false to relax that, such as when the data is already in XML format.
     */
    public void setMustBeJAXBElement(String mustBeJAXBElement) {
        this.mustBeJAXBElement = mustBeJAXBElement;
    }

    /**
     * To turn on marshalling XML fragment trees. By default JAXB looks for @XmlRootElement annotation on given class to
     * operate on whole XML tree. This is useful but not always - sometimes generated code does not have @XmlRootElement
     * annotation, sometimes you need unmarshall only part of tree. In that case you can use partial unmarshalling. To
     * enable this behaviours you need set property partClass. Camel will pass this class to JAXB's unmarshaler.
     */
    public void setFragment(String fragment) {
        this.fragment = fragment;
    }

    public String getFragment() {
        return fragment;
    }

    public String getFilterNonXmlChars() {
        return filterNonXmlChars;
    }

    /**
     * To ignore non xml characheters and replace them with an empty space.
     */
    public void setFilterNonXmlChars(String filterNonXmlChars) {
        this.filterNonXmlChars = filterNonXmlChars;
    }

    public String getEncoding() {
        return encoding;
    }

    /**
     * To overrule and use a specific encoding
     */
    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public String getPartClass() {
        return partClass;
    }

    /**
     * Name of class used for fragment parsing.
     * <p/>
     * See more details at the fragment option.
     */
    public void setPartClass(String partClass) {
        this.partClass = partClass;
    }

    public String getPartNamespace() {
        return partNamespace;
    }

    /**
     * XML namespace to use for fragment parsing.
     * <p/>
     * See more details at the fragment option.
     */
    public void setPartNamespace(String partNamespace) {
        this.partNamespace = partNamespace;
    }

    public String getNamespacePrefixRef() {
        return namespacePrefixRef;
    }

    /**
     * When marshalling using JAXB or SOAP then the JAXB implementation will automatic assign namespace prefixes, such
     * as ns2, ns3, ns4 etc. To control this mapping, Camel allows you to refer to a map which contains the desired
     * mapping.
     */
    public void setNamespacePrefixRef(String namespacePrefixRef) {
        this.namespacePrefixRef = namespacePrefixRef;
    }

    public String getXmlStreamWriterWrapper() {
        return xmlStreamWriterWrapper;
    }

    /**
     * To use a custom xml stream writer.
     */
    public void setXmlStreamWriterWrapper(String xmlStreamWriterWrapperRef) {
        this.xmlStreamWriterWrapper = xmlStreamWriterWrapperRef;
    }

    public String getSchemaLocation() {
        return schemaLocation;
    }

    /**
     * To define the location of the schema
     */
    public void setSchemaLocation(String schemaLocation) {
        this.schemaLocation = schemaLocation;
    }

    public String getNoNamespaceSchemaLocation() {
        return noNamespaceSchemaLocation;
    }

    /**
     * To define the location of the namespaceless schema
     */
    public void setNoNamespaceSchemaLocation(String schemaLocation) {
        this.noNamespaceSchemaLocation = schemaLocation;
    }

    public String getJaxbProviderProperties() {
        return jaxbProviderProperties;
    }

    /**
     * Refers to a custom java.util.Map to lookup in the registry containing custom JAXB provider properties to be used
     * with the JAXB marshaller.
     */
    public void setJaxbProviderProperties(String jaxbProviderProperties) {
        this.jaxbProviderProperties = jaxbProviderProperties;
    }

    public String getContentTypeHeader() {
        return contentTypeHeader;
    }

    public void setContentTypeHeader(String contentTypeHeader) {
        this.contentTypeHeader = contentTypeHeader;
    }

    public String getAccessExternalSchemaProtocols() {
        return accessExternalSchemaProtocols;
    }

    /**
     * Only in use if schema validation has been enabled.
     *
     * Restrict access to the protocols specified for external reference set by the schemaLocation attribute, Import and
     * Include element. Examples of protocols are file, http, jar:file.
     *
     * false or none to deny all access to external references; a specific protocol, such as file, to give permission to
     * only the protocol; the keyword all to grant permission to all protocols.
     */
    public void setAccessExternalSchemaProtocols(String accessExternalSchemaProtocols) {
        this.accessExternalSchemaProtocols = accessExternalSchemaProtocols;
    }

    /**
     * {@code Builder} is a specific builder for {@link JaxbDataFormat}.
     */
    @XmlTransient
    public static class Builder implements DataFormatBuilder<JaxbDataFormat> {

        private String contextPath;
        private String contextPathIsClassName;
        private String schema;
        private String schemaSeverityLevel;
        private String prettyPrint;
        private String objectFactory;
        private String ignoreJAXBElement;
        private String mustBeJAXBElement;
        private String filterNonXmlChars;
        private String encoding;
        private String fragment;
        private String partClass;
        private String partNamespace;
        private String namespacePrefixRef;
        private String xmlStreamWriterWrapper;
        private String schemaLocation;
        private String noNamespaceSchemaLocation;
        private String jaxbProviderProperties;
        private String contentTypeHeader;
        private String accessExternalSchemaProtocols;

        /**
         * Package name where your JAXB classes are located.
         */
        public Builder contextPath(String contextPath) {
            this.contextPath = contextPath;
            return this;
        }

        /**
         * This can be set to true to mark that the contextPath is referring to a classname and not a package name.
         */
        public Builder contextPathIsClassName(String contextPathIsClassName) {
            this.contextPathIsClassName = contextPathIsClassName;
            return this;
        }

        /**
         * This can be set to true to mark that the contextPath is referring to a classname and not a package name.
         */
        public Builder contextPathIsClassName(boolean contextPathIsClassName) {
            this.contextPathIsClassName = Boolean.toString(contextPathIsClassName);
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
         * Sets the schema severity level to use when validating against a schema. This level determines the minimum
         * severity error that triggers JAXB to stop continue parsing. The default value of 0 (warning) means that any
         * error (warning, error or fatal error) will trigger JAXB to stop. There are the following three levels:
         * 0=warning, 1=error, 2=fatal error.
         */
        public Builder schemaSeverityLevel(String schemaSeverityLevel) {
            this.schemaSeverityLevel = schemaSeverityLevel;
            return this;
        }

        /**
         * Sets the schema severity level to use when validating against a schema. This level determines the minimum
         * severity error that triggers JAXB to stop continue parsing. The default value of 0 (warning) means that any
         * error (warning, error or fatal error) will trigger JAXB to stop. There are the following three levels:
         * 0=warning, 1=error, 2=fatal error.
         */
        public Builder schemaSeverityLevel(int schemaSeverityLevel) {
            this.schemaSeverityLevel = Integer.toString(schemaSeverityLevel);
            return this;
        }

        /**
         * To enable pretty printing output nicely formatted.
         * <p/>
         * Is by default false.
         */
        public Builder prettyPrint(String prettyPrint) {
            this.prettyPrint = prettyPrint;
            return this;
        }

        /**
         * To enable pretty printing output nicely formatted.
         * <p/>
         * Is by default false.
         */
        public Builder prettyPrint(boolean prettyPrint) {
            this.prettyPrint = Boolean.toString(prettyPrint);
            return this;
        }

        /**
         * Whether to allow using ObjectFactory classes to create the POJO classes during marshalling. This only applies
         * to POJO classes that has not been annotated with JAXB and providing jaxb.index descriptor files.
         */
        public Builder objectFactory(String objectFactory) {
            this.objectFactory = objectFactory;
            return this;
        }

        /**
         * Whether to allow using ObjectFactory classes to create the POJO classes during marshalling. This only applies
         * to POJO classes that has not been annotated with JAXB and providing jaxb.index descriptor files.
         */
        public Builder objectFactory(boolean objectFactory) {
            this.objectFactory = Boolean.toString(objectFactory);
            return this;
        }

        /**
         * Whether to ignore JAXBElement elements - only needed to be set to false in very special use-cases.
         */
        public Builder ignoreJAXBElement(String ignoreJAXBElement) {
            this.ignoreJAXBElement = ignoreJAXBElement;
            return this;
        }

        /**
         * Whether to ignore JAXBElement elements - only needed to be set to false in very special use-cases.
         */
        public Builder ignoreJAXBElement(boolean ignoreJAXBElement) {
            this.ignoreJAXBElement = Boolean.toString(ignoreJAXBElement);
            return this;
        }

        /**
         * Whether marhsalling must be java objects with JAXB annotations. And if not then it fails. This option can be
         * set to false to relax that, such as when the data is already in XML format.
         */
        public Builder mustBeJAXBElement(String mustBeJAXBElement) {
            this.mustBeJAXBElement = mustBeJAXBElement;
            return this;
        }

        /**
         * Whether marhsalling must be java objects with JAXB annotations. And if not then it fails. This option can be
         * set to false to relax that, such as when the data is already in XML format.
         */
        public Builder mustBeJAXBElement(boolean mustBeJAXBElement) {
            this.mustBeJAXBElement = Boolean.toString(mustBeJAXBElement);
            return this;
        }

        /**
         * To turn on marshalling XML fragment trees. By default JAXB looks for @XmlRootElement annotation on given
         * class to operate on whole XML tree. This is useful but not always - sometimes generated code does not
         * have @XmlRootElement annotation, sometimes you need unmarshall only part of tree. In that case you can use
         * partial unmarshalling. To enable this behaviours you need set property partClass. Camel will pass this class
         * to JAXB's unmarshaler.
         */
        public Builder fragment(String fragment) {
            this.fragment = fragment;
            return this;
        }

        /**
         * To turn on marshalling XML fragment trees. By default JAXB looks for @XmlRootElement annotation on given
         * class to operate on whole XML tree. This is useful but not always - sometimes generated code does not
         * have @XmlRootElement annotation, sometimes you need unmarshall only part of tree. In that case you can use
         * partial unmarshalling. To enable this behaviours you need set property partClass. Camel will pass this class
         * to JAXB's unmarshaler.
         */
        public Builder fragment(boolean fragment) {
            this.fragment = Boolean.toString(fragment);
            return this;
        }

        /**
         * To ignore non xml characters and replace them with an empty space.
         */
        public Builder filterNonXmlChars(String filterNonXmlChars) {
            this.filterNonXmlChars = filterNonXmlChars;
            return this;
        }

        /**
         * To ignore non xml characters and replace them with an empty space.
         */
        public Builder filterNonXmlChars(boolean filterNonXmlChars) {
            this.filterNonXmlChars = Boolean.toString(filterNonXmlChars);
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
         * Name of class used for fragment parsing.
         * <p/>
         * See more details at the fragment option.
         */
        public Builder partClass(String partClass) {
            this.partClass = partClass;
            return this;
        }

        /**
         * XML namespace to use for fragment parsing.
         * <p/>
         * See more details at the fragment option.
         */
        public Builder partNamespace(String partNamespace) {
            this.partNamespace = partNamespace;
            return this;
        }

        /**
         * When marshalling using JAXB or SOAP then the JAXB implementation will automatically assign namespace
         * prefixes, such as ns2, ns3, ns4 etc. To control this mapping, Camel allows you to refer to a map which
         * contains the desired mapping.
         */
        public Builder namespacePrefixRef(String namespacePrefixRef) {
            this.namespacePrefixRef = namespacePrefixRef;
            return this;
        }

        /**
         * To use a custom xml stream writer.
         */
        public Builder xmlStreamWriterWrapper(String xmlStreamWriterWrapperRef) {
            this.xmlStreamWriterWrapper = xmlStreamWriterWrapperRef;
            return this;
        }

        /**
         * To define the location of the schema
         */
        public Builder schemaLocation(String schemaLocation) {
            this.schemaLocation = schemaLocation;
            return this;
        }

        /**
         * To define the location of the namespaceless schema
         */
        public Builder noNamespaceSchemaLocation(String schemaLocation) {
            this.noNamespaceSchemaLocation = schemaLocation;
            return this;
        }

        /**
         * Refers to a custom java.util.Map to lookup in the registry containing custom JAXB provider properties to be
         * used with the JAXB marshaller.
         */
        public Builder jaxbProviderProperties(String jaxbProviderProperties) {
            this.jaxbProviderProperties = jaxbProviderProperties;
            return this;
        }

        public Builder contentTypeHeader(String contentTypeHeader) {
            this.contentTypeHeader = contentTypeHeader;
            return this;
        }

        public Builder contentTypeHeader(boolean contentTypeHeader) {
            this.contentTypeHeader = Boolean.toString(contentTypeHeader);
            return this;
        }

        /**
         * Only in use if schema validation has been enabled.
         *
         * Restrict access to the protocols specified for external reference set by the schemaLocation attribute, Import
         * and Include element. Examples of protocols are file, http, jar:file.
         *
         * false or none to deny all access to external references; a specific protocol, such as file, to give
         * permission to only the protocol; the keyword all to grant permission to all protocols.
         */
        public Builder accessExternalSchemaProtocols(String accessExternalSchemaProtocols) {
            this.accessExternalSchemaProtocols = accessExternalSchemaProtocols;
            return this;
        }

        @Override
        public JaxbDataFormat end() {
            return new JaxbDataFormat(this);
        }
    }
}

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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.Metadata;

/**
 * JAXB data format uses the JAXB2 XML marshalling standard to unmarshal an XML
 * payload into Java objects or to marshal Java objects into an XML payload.
 */
@Metadata(firstVersion = "1.0.0", label = "dataformat,transformation,xml", title = "JAXB")
@XmlRootElement(name = "jaxb")
@XmlAccessorType(XmlAccessType.FIELD)
public class JaxbDataFormat extends DataFormatDefinition {
    @XmlAttribute(required = true)
    private String contextPath;
    @XmlAttribute
    private String schema;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Integer", enums = "0,1,2", defaultValue = "0")
    private String schemaSeverityLevel;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String prettyPrint;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String objectFactory;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String ignoreJAXBElement;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String mustBeJAXBElement;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String filterNonXmlChars;
    @XmlAttribute
    private String encoding;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String fragment;
    // Partial encoding
    @XmlAttribute
    private String partClass;
    @XmlAttribute
    private String partNamespace;
    @XmlAttribute
    private String namespacePrefixRef;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String xmlStreamWriterWrapper;
    @XmlAttribute
    private String schemaLocation;
    @XmlAttribute
    private String noNamespaceSchemaLocation;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String jaxbProviderProperties;

    public JaxbDataFormat() {
        super("jaxb");
    }

    public JaxbDataFormat(boolean prettyPrint) {
        this();
        setPrettyPrint(Boolean.toString(prettyPrint));
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

    public String getSchema() {
        return schema;
    }

    /**
     * To validate against an existing schema. Your can use the prefix
     * classpath:, file:* or *http: to specify how the resource should by
     * resolved. You can separate multiple schema files by using the ','
     * character.
     */
    public void setSchema(String schema) {
        this.schema = schema;
    }

    public String getSchemaSeverityLevel() {
        return schemaSeverityLevel;
    }

    /**
     * Sets the schema severity level to use when validating against a schema.
     * This level determines the minimum severity error that triggers JAXB to
     * stop continue parsing. The default value of 0 (warning) means that any
     * error (warning, error or fatal error) will trigger JAXB to stop. There
     * are the following three levels: 0=warning, 1=error, 2=fatal error.
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
     * Whether to allow using ObjectFactory classes to create the POJO classes
     * during marshalling. This only applies to POJO classes that has not been
     * annotated with JAXB and providing jaxb.index descriptor files.
     */
    public void setObjectFactory(String objectFactory) {
        this.objectFactory = objectFactory;
    }

    public String getIgnoreJAXBElement() {
        return ignoreJAXBElement;
    }

    /**
     * Whether to ignore JAXBElement elements - only needed to be set to false
     * in very special use-cases.
     */
    public void setIgnoreJAXBElement(String ignoreJAXBElement) {
        this.ignoreJAXBElement = ignoreJAXBElement;
    }

    public String getMustBeJAXBElement() {
        return mustBeJAXBElement;
    }

    /**
     * Whether marhsalling must be java objects with JAXB annotations. And if
     * not then it fails. This option can be set to false to relax that, such as
     * when the data is already in XML format.
     */
    public void setMustBeJAXBElement(String mustBeJAXBElement) {
        this.mustBeJAXBElement = mustBeJAXBElement;
    }

    /**
     * To turn on marshalling XML fragment trees. By default JAXB looks
     * for @XmlRootElement annotation on given class to operate on whole XML
     * tree. This is useful but not always - sometimes generated code does not
     * have @XmlRootElement annotation, sometimes you need unmarshall only part
     * of tree. In that case you can use partial unmarshalling. To enable this
     * behaviours you need set property partClass. Camel will pass this class to
     * JAXB's unmarshaler.
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
     * When marshalling using JAXB or SOAP then the JAXB implementation will
     * automatic assign namespace prefixes, such as ns2, ns3, ns4 etc. To
     * control this mapping, Camel allows you to refer to a map which contains
     * the desired mapping.
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
     * Refers to a custom java.util.Map to lookup in the registry containing
     * custom JAXB provider properties to be used with the JAXB marshaller.
     */
    public void setJaxbProviderProperties(String jaxbProviderProperties) {
        this.jaxbProviderProperties = jaxbProviderProperties;
    }

}

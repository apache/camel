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
package org.apache.camel.model.dataformat;

import java.util.Map;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.namespace.QName;

import org.apache.camel.CamelContext;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * JAXB data format uses the JAXB2 XML marshalling standard to unmarshal an XML payload into Java objects or to marshal Java objects into an XML payload.
 *
 * @version 
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
    private Boolean prettyPrint;
    @XmlAttribute
    private Boolean objectFactory;
    @XmlAttribute
    private Boolean ignoreJAXBElement;
    @XmlAttribute
    private Boolean mustBeJAXBElement;
    @XmlAttribute
    private Boolean filterNonXmlChars;
    @XmlAttribute
    private String encoding;
    @XmlAttribute
    private Boolean fragment;
    // Partial encoding
    @XmlAttribute
    private String partClass;
    @XmlAttribute
    private String partNamespace;
    @XmlAttribute
    private String namespacePrefixRef;
    @XmlAttribute @Metadata(label = "advanced")
    private String xmlStreamWriterWrapper;
    @XmlAttribute
    private String schemaLocation;
    @XmlAttribute
    private String noNamespaceSchemaLocation;
    @XmlAttribute @Metadata(label = "advanced")
    private String jaxbProviderProperties;

    public JaxbDataFormat() {
        super("jaxb");
    }

    public JaxbDataFormat(boolean prettyPrint) {
        this();
        setPrettyPrint(prettyPrint);
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
     * To validate against an existing schema.
     * Your can use the prefix classpath:, file:* or *http: to specify how the resource should by resolved.
     * You can separate multiple schema files by using the ',' character.
     */
    public void setSchema(String schema) {
        this.schema = schema;
    }

    public Boolean getPrettyPrint() {
        return prettyPrint;
    }

    /**
     * To enable pretty printing output nicely formatted.
     * <p/>
     * Is by default false.
     */
    public void setPrettyPrint(Boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    public Boolean getObjectFactory() {
        return objectFactory;
    }

    /**
     * Whether to allow using ObjectFactory classes to create the POJO classes during marshalling.
     * This only applies to POJO classes that has not been annotated with JAXB and providing jaxb.index descriptor files.
     */
    public void setObjectFactory(Boolean objectFactory) {
        this.objectFactory = objectFactory;
    }

    public Boolean getIgnoreJAXBElement() {
        return ignoreJAXBElement;
    }

    /**
     * Whether to ignore JAXBElement elements - only needed to be set to false in very special use-cases.
     */
    public void setIgnoreJAXBElement(Boolean ignoreJAXBElement) {
        this.ignoreJAXBElement = ignoreJAXBElement;
    }

    public Boolean getMustBeJAXBElement() {
        return mustBeJAXBElement;
    }

    /**
     * Whether marhsalling must be java objects with JAXB annotations. And if not then it fails.
     * This option can be set to false to relax that, such as when the data is already in XML format.
     */
    public void setMustBeJAXBElement(Boolean mustBeJAXBElement) {
        this.mustBeJAXBElement = mustBeJAXBElement;
    }

    /**
     * To turn on marshalling XML fragment trees.
     * By default JAXB looks for @XmlRootElement annotation on given class to operate on whole XML tree.
     * This is useful but not always - sometimes generated code does not have @XmlRootElement annotation,
     * sometimes you need unmarshall only part of tree.
     * In that case you can use partial unmarshalling. To enable this behaviours you need set property partClass.
     * Camel will pass this class to JAXB's unmarshaler.
     */
    public void setFragment(Boolean fragment) {
        this.fragment = fragment;
    }
    
    public Boolean getFragment() {
        return fragment;
    }

    public Boolean getFilterNonXmlChars() {
        return filterNonXmlChars;
    }

    /**
     * To ignore non xml characheters and replace them with an empty space.
     */
    public void setFilterNonXmlChars(Boolean filterNonXmlChars) {
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
     * When marshalling using JAXB or SOAP then the JAXB implementation will automatic assign namespace prefixes,
     * such as ns2, ns3, ns4 etc. To control this mapping, Camel allows you to refer to a map which contains the desired mapping.
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
     * Refers to a custom java.util.Map to lookup in the registry containing custom JAXB provider properties
     * to be used with the JAXB marshaller.
     */
    public void setJaxbProviderProperties(String jaxbProviderProperties) {
        this.jaxbProviderProperties = jaxbProviderProperties;
    }

    @Override
    protected void configureDataFormat(DataFormat dataFormat, CamelContext camelContext) {
        Boolean answer = ObjectHelper.toBoolean(getPrettyPrint());
        if (answer != null && !answer) {
            setProperty(camelContext, dataFormat, "prettyPrint", Boolean.FALSE);
        } else { // the default value is true
            setProperty(camelContext, dataFormat, "prettyPrint", Boolean.TRUE);
        }
        answer = ObjectHelper.toBoolean(getObjectFactory());
        if (answer != null && !answer) {
            setProperty(camelContext, dataFormat, "objectFactory", Boolean.FALSE);
        } else { // the default value is true
            setProperty(camelContext, dataFormat, "objectFactory", Boolean.TRUE);
        }
        answer = ObjectHelper.toBoolean(getIgnoreJAXBElement());
        if (answer != null && !answer) {
            setProperty(camelContext, dataFormat, "ignoreJAXBElement", Boolean.FALSE);
        } else { // the default value is true
            setProperty(camelContext, dataFormat, "ignoreJAXBElement", Boolean.TRUE);
        }
        answer = ObjectHelper.toBoolean(getMustBeJAXBElement());
        if (answer != null && answer) {
            setProperty(camelContext, dataFormat, "mustBeJAXBElement", Boolean.TRUE);
        } else { // the default value is false
            setProperty(camelContext, dataFormat, "mustBeJAXBElement", Boolean.FALSE);
        }
        answer = ObjectHelper.toBoolean(getFilterNonXmlChars());
        if (answer != null && answer) {
            setProperty(camelContext, dataFormat, "filterNonXmlChars", Boolean.TRUE);
        } else { // the default value is false
            setProperty(camelContext, dataFormat, "filterNonXmlChars", Boolean.FALSE);
        }
        answer = ObjectHelper.toBoolean(getFragment());
        if (answer != null && answer) {
            setProperty(camelContext, dataFormat, "fragment", Boolean.TRUE);
        } else { // the default value is false
            setProperty(camelContext, dataFormat, "fragment", Boolean.FALSE);
        }

        setProperty(camelContext, dataFormat, "contextPath", contextPath);
        if (partClass != null) {
            setProperty(camelContext, dataFormat, "partClass", partClass);
        }
        if (partNamespace != null) {
            setProperty(camelContext, dataFormat, "partNamespace", QName.valueOf(partNamespace));
        }
        if (encoding != null) {
            setProperty(camelContext, dataFormat, "encoding", encoding);
        }
        if (namespacePrefixRef != null) {
            setProperty(camelContext, dataFormat, "namespacePrefixRef", namespacePrefixRef);
        }
        if (schema != null) {
            setProperty(camelContext, dataFormat, "schema", schema);
        }
        if (xmlStreamWriterWrapper != null) {
            setProperty(camelContext, dataFormat, "xmlStreamWriterWrapper", xmlStreamWriterWrapper);
        }
        if (schemaLocation != null) {
            setProperty(camelContext, dataFormat, "schemaLocation", schemaLocation);
        }
        if (noNamespaceSchemaLocation != null) {
            setProperty(camelContext, dataFormat, "noNamespaceSchemaLocation", noNamespaceSchemaLocation);
        }
        if (jaxbProviderProperties != null) {
            Map map = CamelContextHelper.mandatoryLookup(camelContext, jaxbProviderProperties, Map.class);
            setProperty(camelContext, dataFormat, "jaxbProviderProperties", map);
        }
    }
}
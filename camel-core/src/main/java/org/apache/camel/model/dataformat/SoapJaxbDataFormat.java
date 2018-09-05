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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContext;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.Metadata;

/**
 * SOAP is a data format which uses JAXB2 and JAX-WS annotations to marshal and unmarshal SOAP payloads.
 */
@Metadata(firstVersion = "2.3.0", label = "dataformat,transformation,xml", title = "SOAP")
@XmlRootElement(name = "soapjaxb")
@XmlAccessorType(XmlAccessType.FIELD)
public class SoapJaxbDataFormat extends DataFormatDefinition {
    @XmlAttribute(required = true)
    private String contextPath;
    @XmlAttribute
    private String encoding;
    @XmlAttribute
    private String elementNameStrategyRef;
    @XmlTransient
    private Object elementNameStrategy;
    @XmlAttribute @Metadata(defaultValue = "1.1")
    private String version;
    @XmlAttribute
    private String namespacePrefixRef;
    @XmlAttribute
    private String schema;

    public SoapJaxbDataFormat() {
        super("soapjaxb");
    }
    
    public SoapJaxbDataFormat(String contextPath) {
        this();
        setContextPath(contextPath);
    }
    
    public SoapJaxbDataFormat(String contextPath, String elementNameStrategyRef) {
        this();
        setContextPath(contextPath);
        setElementNameStrategyRef(elementNameStrategyRef);
    }
    
    public SoapJaxbDataFormat(String contextPath, Object elementNameStrategy) {
        this();
        setContextPath(contextPath);
        setElementNameStrategy(elementNameStrategy);
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
     * An element name strategy is used for two purposes. The first is to find a xml element name for a given object
     * and soap action when marshaling the object into a SOAP message. The second is to find an Exception class for a given soap fault name.
     * <p/>
     * The following three element strategy class name is provided out of the box.
     * QNameStrategy - Uses a fixed qName that is configured on instantiation. Exception lookup is not supported
     * TypeNameStrategy - Uses the name and namespace from the @XMLType annotation of the given type. If no namespace is set then package-info is used. Exception lookup is not supported
     * ServiceInterfaceStrategy - Uses information from a webservice interface to determine the type name and to find the exception class for a SOAP fault
     * <p/>
     * All three classes is located in the package name org.apache.camel.dataformat.soap.name
     * <p/>
     * If you have generated the web service stub code with cxf-codegen or a similar tool then you probably
     * will want to use the ServiceInterfaceStrategy. In the case you have no annotated service interface you should use QNameStrategy or TypeNameStrategy.
     */
    public void setElementNameStrategyRef(String elementNameStrategyRef) {
        this.elementNameStrategyRef = elementNameStrategyRef;
    }

    public String getElementNameStrategyRef() {
        return elementNameStrategyRef;
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
     * An element name strategy is used for two purposes. The first is to find a xml element name for a given object
     * and soap action when marshaling the object into a SOAP message. The second is to find an Exception class for a given soap fault name.
     * <p/>
     * The following three element strategy class name is provided out of the box.
     * QNameStrategy - Uses a fixed qName that is configured on instantiation. Exception lookup is not supported
     * TypeNameStrategy - Uses the name and namespace from the @XMLType annotation of the given type. If no namespace is set then package-info is used. Exception lookup is not supported
     * ServiceInterfaceStrategy - Uses information from a webservice interface to determine the type name and to find the exception class for a SOAP fault
     * <p/>
     * All three classes is located in the package name org.apache.camel.dataformat.soap.name
     * <p/>
     * If you have generated the web service stub code with cxf-codegen or a similar tool then you probably
     * will want to use the ServiceInterfaceStrategy. In the case you have no annotated service interface you should use QNameStrategy or TypeNameStrategy.
     */
    public void setElementNameStrategy(Object elementNameStrategy) {
        this.elementNameStrategy = elementNameStrategy;
    }

    public Object getElementNameStrategy() {
        return elementNameStrategy;
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

    @Override
    protected void configureDataFormat(DataFormat dataFormat, CamelContext camelContext) {
        if (elementNameStrategy != null) {
            setProperty(camelContext, dataFormat, "elementNameStrategy", elementNameStrategy);
        }
        if (elementNameStrategyRef != null) {
            setProperty(camelContext, dataFormat, "elementNameStrategyRef", elementNameStrategyRef);
        }
        if (encoding != null) {
            setProperty(camelContext, dataFormat, "encoding", encoding);
        }
        if (version != null) {
            setProperty(camelContext, dataFormat, "version", version);
        }
        if (namespacePrefixRef != null) {
            setProperty(camelContext, dataFormat, "namespacePrefixRef", namespacePrefixRef);
        }
        if (schema != null) {
            setProperty(camelContext, dataFormat, "schema", schema);
        }
        setProperty(camelContext, dataFormat, "contextPath", contextPath);
    }

}

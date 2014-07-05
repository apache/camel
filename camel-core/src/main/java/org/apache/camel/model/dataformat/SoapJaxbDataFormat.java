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
    @XmlAttribute
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

    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    public String getContextPath() {
        return contextPath;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setElementNameStrategyRef(String elementNameStrategyRef) {
        this.elementNameStrategyRef = elementNameStrategyRef;
    }

    public String getElementNameStrategyRef() {
        return elementNameStrategyRef;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void setElementNameStrategy(Object elementNameStrategy) {
        this.elementNameStrategy = elementNameStrategy;
    }

    public Object getElementNameStrategy() {
        return elementNameStrategy;
    }

    public String getNamespacePrefixRef() {
        return namespacePrefixRef;
    }

    public void setNamespacePrefixRef(String namespacePrefixRef) {
        this.namespacePrefixRef = namespacePrefixRef;
    }
    
    public String getSchema() {
        return schema;
    }
    
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

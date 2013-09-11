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
import javax.xml.namespace.QName;

import org.apache.camel.CamelContext;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;

/**
 * Represents the JAXB2 XML {@link DataFormat}
 *
 * @version 
 */
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
    private Boolean ignoreJAXBElement;
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
    @XmlAttribute
    private String xmlStreamWriterWrapper;

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

    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public Boolean getPrettyPrint() {
        return prettyPrint;
    }

    public void setPrettyPrint(Boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    public Boolean getIgnoreJAXBElement() {
        return ignoreJAXBElement;
    }

    public void setIgnoreJAXBElement(Boolean ignoreJAXBElement) {
        this.ignoreJAXBElement = ignoreJAXBElement;
    }
    
    public void setFragment(Boolean fragment) {
        this.fragment = fragment;
    }
    
    public Boolean getFragment() {
        return fragment;
    }

    public Boolean getFilterNonXmlChars() {
        return filterNonXmlChars;
    }

    public void setFilterNonXmlChars(Boolean filterNonXmlChars) {
        this.filterNonXmlChars = filterNonXmlChars;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public String getPartClass() {
        return partClass;
    }

    public void setPartClass(String partClass) {
        this.partClass = partClass;
    }

    public String getPartNamespace() {
        return partNamespace;
    }

    public void setPartNamespace(String partNamespace) {
        this.partNamespace = partNamespace;
    }

    public String getNamespacePrefixRef() {
        return namespacePrefixRef;
    }

    public void setNamespacePrefixRef(String namespacePrefixRef) {
        this.namespacePrefixRef = namespacePrefixRef;
    }

    public String getXmlStreamWriterWrapper() {
        return xmlStreamWriterWrapper;
    }

    public void setXmlStreamWriterWrapper(String xmlStreamWriterWrapperRef) {
        this.xmlStreamWriterWrapper = xmlStreamWriterWrapperRef;
    }

    @Override
    protected void configureDataFormat(DataFormat dataFormat, CamelContext camelContext) {
        Boolean answer = ObjectHelper.toBoolean(getPrettyPrint());
        if (answer != null && !answer) {
            setProperty(camelContext, dataFormat, "prettyPrint", Boolean.FALSE);
        } else { // the default value is true
            setProperty(camelContext, dataFormat, "prettyPrint", Boolean.TRUE);
        }
        answer = ObjectHelper.toBoolean(getIgnoreJAXBElement());
        if (answer != null && !answer) {
            setProperty(camelContext, dataFormat, "ignoreJAXBElement", Boolean.FALSE);
        } else { // the default value is true
            setProperty(camelContext, dataFormat, "ignoreJAXBElement", Boolean.TRUE);
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
        setProperty(camelContext, dataFormat, "contextPath", contextPath);
        if (schema != null) {
            setProperty(camelContext, dataFormat, "schema", schema);
        }
        if (xmlStreamWriterWrapper != null) {
            setProperty(camelContext, dataFormat, "xmlStreamWriterWrapper", xmlStreamWriterWrapper);
        }
    }
}
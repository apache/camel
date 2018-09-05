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

import org.w3c.dom.Node;

import org.apache.camel.CamelContext;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ObjectHelper;

/**
 * TidyMarkup data format is used for parsing HTML and return it as pretty well-formed HTML.
 */
@Metadata(firstVersion = "2.0.0", label = "dataformat,transformation", title = "TidyMarkup")
@XmlRootElement(name = "tidyMarkup")
@XmlAccessorType(XmlAccessType.FIELD)
public class TidyMarkupDataFormat extends DataFormatDefinition {
    @XmlAttribute(name = "dataObjectType") @Metadata(defaultValue = "org.w3c.dom.Node")
    private String dataObjectTypeName;
    @XmlAttribute
    private Boolean omitXmlDeclaration;
    @XmlTransient
    private Class<?> dataObjectType;

    public TidyMarkupDataFormat() {
        super("tidyMarkup");
        this.setDataObjectType(Node.class);
    }

    public TidyMarkupDataFormat(Class<?> dataObjectType) {
        this();
        if (!dataObjectType.isAssignableFrom(String.class) && !dataObjectType.isAssignableFrom(Node.class)) {
            throw new IllegalArgumentException("TidyMarkupDataFormat only supports returning a String or a org.w3c.dom.Node object");
        }
        this.setDataObjectType(dataObjectType);
    }

    /**
     * What data type to unmarshal as, can either be org.w3c.dom.Node or java.lang.String.
     * <p/>
     * Is by default org.w3c.dom.Node
     */
    public void setDataObjectType(Class<?> dataObjectType) {
        this.dataObjectType = dataObjectType;
    }

    public Class<?> getDataObjectType() {
        return dataObjectType;
    }

    public String getDataObjectTypeName() {
        return dataObjectTypeName;
    }

    /**
     * What data type to unmarshal as, can either be org.w3c.dom.Node or java.lang.String.
     * <p/>
     * Is by default org.w3c.dom.Node
     */
    public void setDataObjectTypeName(String dataObjectTypeName) {
        this.dataObjectTypeName = dataObjectTypeName;
    }

    public Boolean getOmitXmlDeclaration() {
        return omitXmlDeclaration;
    }

    /**
     * When returning a String, do we omit the XML declaration in the top.
     */
    public void setOmitXmlDeclaration(Boolean omitXmlDeclaration) {
        this.omitXmlDeclaration = omitXmlDeclaration;
    }

    @Override
    protected DataFormat createDataFormat(RouteContext routeContext) {
        if (dataObjectType == null && dataObjectTypeName != null) {
            try {
                dataObjectType = routeContext.getCamelContext().getClassResolver().resolveMandatoryClass(dataObjectTypeName);
            } catch (ClassNotFoundException e) {
                throw ObjectHelper.wrapRuntimeCamelException(e);
            }
        }

        return super.createDataFormat(routeContext);
    }

    @Override
    protected void configureDataFormat(DataFormat dataFormat, CamelContext camelContext) {
        if (dataObjectType != null) {
            setProperty(camelContext, dataFormat, "dataObjectType", dataObjectType);
        }
        if (omitXmlDeclaration != null) {
            setProperty(camelContext, dataFormat, "omitXmlDeclaration", omitXmlDeclaration);
        }
    }

}
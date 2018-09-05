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
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ObjectHelper;

/**
 * JiBX data format is used for unmarshal a XML payload to POJO or to marshal POJO back to XML payload.
 */
@Metadata(firstVersion = "2.6.0", label = "dataformat,transformation,xml", title = "JiBX")
@XmlRootElement(name = "jibx")
@XmlAccessorType(XmlAccessType.NONE)
public class JibxDataFormat extends DataFormatDefinition {
    @XmlAttribute(name = "unmarshallClass")
    private String unmarshallTypeName;
    @XmlAttribute
    private String bindingName;
    @XmlTransient
    private Class<?> unmarshallClass;

    public JibxDataFormat() {
        super("jibx");
    }

    public JibxDataFormat(Class<?> unmarshallClass) {
        this();
        setUnmarshallClass(unmarshallClass);
    }

    public Class<?> getUnmarshallClass() {
        return unmarshallClass;
    }

    /**
     * Class use when unmarshalling from XML to Java.
     */
    public void setUnmarshallClass(Class<?> unmarshallClass) {
        this.unmarshallClass = unmarshallClass;
    }

    public String getUnmarshallTypeName() {
        return unmarshallTypeName;
    }

    /**
     * Class name to use when unmarshalling from XML to Java.
     */
    public void setUnmarshallTypeName(String unmarshallTypeName) {
        this.unmarshallTypeName = unmarshallTypeName;
    }

    public String getBindingName() {
        return bindingName;
    }

    /**
     * To use a custom binding factory
     */
    public void setBindingName(String bindingName) {
        this.bindingName = bindingName;
    }

    @Override
    protected DataFormat createDataFormat(RouteContext routeContext) {
        if (unmarshallClass == null && unmarshallTypeName != null) {
            try {
                unmarshallClass = routeContext.getCamelContext().getClassResolver().resolveMandatoryClass(unmarshallTypeName);
            } catch (ClassNotFoundException e) {
                throw ObjectHelper.wrapRuntimeCamelException(e);
            }
        }

        return super.createDataFormat(routeContext);
    }

    @Override
    protected void configureDataFormat(DataFormat dataFormat, CamelContext camelContext) {
        if (unmarshallClass != null) {
            setProperty(camelContext, dataFormat, "unmarshallClass", unmarshallClass);
        }
        if (bindingName != null) {
            setProperty(camelContext, dataFormat, "bindingName", bindingName);
        }
    }

}
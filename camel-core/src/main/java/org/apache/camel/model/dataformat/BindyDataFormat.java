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
 * The Bindy data format is used for working with flat payloads (such as CSV, delimited, fixed length formats, or FIX messages).
 *
 * @version 
 */
@Metadata(firstVersion = "2.0.0", label = "dataformat,transformation,csv", title = "Bindy")
@XmlRootElement(name = "bindy")
@XmlAccessorType(XmlAccessType.FIELD)
public class BindyDataFormat extends DataFormatDefinition {
    @XmlAttribute(required = true)
    private BindyType type;
    @XmlAttribute
    private String classType;
    @XmlAttribute
    private String locale;
    @XmlTransient
    private Class<?> clazz;

    public BindyDataFormat() {
        super("bindy");
    }

    public BindyType getType() {
        return type;
    }

    /**
     * Whether to use csv, fixed or key value pairs mode.
     */
    public void setType(BindyType type) {
        this.type = type;
    }

    public String getClassType() {
        return classType;
    }

    /**
     * Name of model class to use.
     */
    public void setClassType(String classType) {
        this.classType = classType;
    }

    /**
     * Type of model class to use.
     */
    public void setClassType(Class<?> classType) {
        this.clazz = classType;
    }

    public String getLocale() {
        return locale;
    }

    /**
     * To configure a default locale to use, such as <tt>us</tt> for united states.
     * <p/>
     * To use the JVM platform default locale then use the name <tt>default</tt>
     */
    public void setLocale(String locale) {
        this.locale = locale;
    }

    protected DataFormat createDataFormat(RouteContext routeContext) {
        if (classType == null && clazz == null) {
            throw new IllegalArgumentException("Either packages or classType must be specified");
        }

        if (type == BindyType.Csv) {
            setDataFormatName("bindy-csv");
        } else if (type == BindyType.Fixed) {
            setDataFormatName("bindy-fixed");
        } else {
            setDataFormatName("bindy-kvp");
        }

        if (clazz == null && classType != null) {
            try {
                clazz = routeContext.getCamelContext().getClassResolver().resolveMandatoryClass(classType);
            } catch (ClassNotFoundException e) {
                throw ObjectHelper.wrapRuntimeCamelException(e);
            }
        }
        return super.createDataFormat(routeContext);
    }

    @Override
    protected void configureDataFormat(DataFormat dataFormat, CamelContext camelContext) {
        setProperty(camelContext, dataFormat, "locale", locale);
        setProperty(camelContext, dataFormat, "classType", clazz);
    }

}
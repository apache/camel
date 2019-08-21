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
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.Metadata;

/**
 * The Bindy data format is used for working with flat payloads (such as CSV,
 * delimited, fixed length formats, or FIX messages).
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
    @XmlAttribute
    @Metadata(defaultValue = "true")
    private Boolean unwrapSingleInstance;
    @XmlTransient
    private Class<?> clazz;

    public BindyDataFormat() {
        super("bindy");
    }

    public BindyType getType() {
        return type;
    }

    /**
     * Whether to use Csv, Fixed, or KeyValue.
     */
    public void setType(BindyType type) {
        this.type = type;
    }

    public String getClassTypeAsString() {
        return classType;
    }

    /**
     * Name of model class to use.
     */
    public void setClassTypeAsString(String classType) {
        this.classType = classType;
    }

    /**
     * Name of model class to use.
     */
    public void setClassType(Class<?> classType) {
        this.clazz = classType;
    }

    public Class<?> getClassType() {
        return clazz;
    }

    public String getLocale() {
        return locale;
    }

    /**
     * To configure a default locale to use, such as <tt>us</tt> for united
     * states.
     * <p/>
     * To use the JVM platform default locale then use the name <tt>default</tt>
     */
    public void setLocale(String locale) {
        this.locale = locale;
    }

    public Boolean getUnwrapSingleInstance() {
        return unwrapSingleInstance;
    }

    /**
     * When unmarshalling should a single instance be unwrapped and returned
     * instead of wrapped in a <tt>java.util.List</tt>.
     */
    public void setUnwrapSingleInstance(Boolean unwrapSingleInstance) {
        this.unwrapSingleInstance = unwrapSingleInstance;
    }

}

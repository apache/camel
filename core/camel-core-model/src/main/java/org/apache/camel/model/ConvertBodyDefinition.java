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
package org.apache.camel.model;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.spi.Metadata;

/**
 * Converts the message body to another type
 */
@Metadata(label = "eip,transformation")
@XmlRootElement(name = "convertBodyTo")
@XmlAccessorType(XmlAccessType.FIELD)
public class ConvertBodyDefinition extends NoOutputDefinition<ConvertBodyDefinition> {

    @XmlTransient
    private Class<?> typeClass;

    @XmlAttribute(required = true)
    private String type;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean", defaultValue = "true")
    private String mandatory;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String charset;

    public ConvertBodyDefinition() {
    }

    public ConvertBodyDefinition(String type) {
        setType(type);
    }

    public ConvertBodyDefinition(Class<?> typeClass) {
        setTypeClass(typeClass);
        setType(typeClass.getCanonicalName());
    }

    public ConvertBodyDefinition(Class<?> typeClass, boolean mandatory) {
        setTypeClass(typeClass);
        setType(typeClass.getCanonicalName());
        setMandatory(mandatory ? "true" : "false");
    }

    public ConvertBodyDefinition(Class<?> typeClass, String charset) {
        setTypeClass(typeClass);
        setType(typeClass.getCanonicalName());
        setCharset(charset);
    }

    @Override
    public String toString() {
        return "ConvertBodyTo[" + getType() + "]";
    }

    @Override
    public String getShortName() {
        return "convertBodyTo";
    }

    @Override
    public String getLabel() {
        return "convertBodyTo[" + getType() + "]";
    }

    public String getType() {
        return type;
    }

    /**
     * The java type to convert to
     */
    public void setType(String type) {
        this.type = type;
    }

    public Class<?> getTypeClass() {
        return typeClass;
    }

    public void setTypeClass(Class<?> typeClass) {
        this.typeClass = typeClass;
    }

    public String getCharset() {
        return charset;
    }

    /**
     * To use a specific charset when converting
     */
    public void setCharset(String charset) {
        this.charset = charset;
    }

    public String getMandatory() {
        return mandatory;
    }

    /**
     * When mandatory then the conversion must return a value (cannot be null), if this is not possible then
     * NoTypeConversionAvailableException is thrown. Setting this to false could mean conversion is not possible and the
     * value is null.
     */
    public void setMandatory(String mandatory) {
        this.mandatory = mandatory;
    }
}

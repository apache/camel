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
package org.apache.camel.model.app;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

import org.apache.camel.spi.Metadata;

/**
 * Bean property
 */
@Metadata(label = "configuration")
@XmlType
@XmlAccessorType(XmlAccessType.FIELD)
public class BeanPropertyDefinition {

    @XmlAttribute
    private String key;
    @XmlAttribute
    private String value;
    @XmlElement(name = "properties")
    private BeanPropertiesDefinition properties;

    public String getKey() {
        return key;
    }

    /**
     * The name of the property
     */
    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    /**
     * The property value
     */
    public void setValue(String value) {
        this.value = value;
    }

    public BeanPropertiesDefinition getProperties() {
        return properties;
    }

    /**
     * Optional nested properties.
     */
    public void setProperties(BeanPropertiesDefinition properties) {
        this.properties = properties;
    }

}

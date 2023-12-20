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

import org.apache.camel.spi.Metadata;

/**
 * A key value pair where the value is a literal value
 *
 * @see PropertyExpressionDefinition
 */
@Metadata(label = "configuration")
@XmlRootElement(name = "property")
@XmlAccessorType(XmlAccessType.FIELD)
public class PropertyDefinition {

    @XmlAttribute(required = true)
    private String key;
    @XmlAttribute(required = true)
    private String value;

    public PropertyDefinition() {
    }

    public PropertyDefinition(String key, String value) {
        this.key = key;
        this.value = value;
    }

    /**
     * The name of the property
     */
    public void setKey(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    /**
     * The property value.
     */
    public void setValue(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}

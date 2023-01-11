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
 * A route template parameter
 */
@Metadata(label = "configuration")
@XmlRootElement(name = "templateParameter")
@XmlAccessorType(XmlAccessType.FIELD)
public class RouteTemplateParameterDefinition {

    @XmlAttribute(required = true)
    String name;
    @XmlAttribute
    Boolean required;
    @XmlAttribute
    String defaultValue;
    @XmlAttribute
    String description;

    public RouteTemplateParameterDefinition() {
    }

    public RouteTemplateParameterDefinition(String name, String defaultValue, String description) {
        this.name = name;
        this.description = description;
        this.defaultValue = defaultValue;
    }

    public boolean isRequired() {
        // assumed to be required if not set explicit to false
        return required == null || required;
    }

    public String getName() {
        return name;
    }

    /**
     * The name of the parameter
     */
    public void setName(String name) {
        this.name = name;
    }

    public Boolean getRequired() {
        return required;
    }

    /**
     * Whether the parameter is required or not. A parameter is required unless this option is set to false or a default
     * value has been configured.
     */
    public void setRequired(Boolean required) {
        this.required = required;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    /**
     * Default value of the parameter. If a default value is provided then the parameter is implied not to be required.
     */
    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Description of the parameter
     */
    public void setDescription(String description) {
        this.description = description;
    }
}

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
@Metadata(label = "configuration",
          description = "Defines a parameter for a route template, with an optional default value and description")
@XmlRootElement(name = "templateParameter")
@XmlAccessorType(XmlAccessType.FIELD)
public class RouteTemplateParameterDefinition {

    @XmlAttribute(required = true)
    @Metadata(description = "The name of the template parameter.")
    String name;
    @XmlAttribute
    @Metadata(description = "Whether this template parameter is required. A required parameter must have a value provided when creating a route from the template.",
              javaType = "java.lang.Boolean")
    String required;
    @XmlAttribute
    @Metadata(description = "The default value of the template parameter. Used when no explicit value is provided when creating a route from the template.")
    String defaultValue;
    @XmlAttribute
    @Metadata(description = "Description of the template parameter for documentation purposes.")
    String description;

    public RouteTemplateParameterDefinition() {
    }

    public RouteTemplateParameterDefinition(String name, String defaultValue, String description) {
        this.name = name;
        this.description = description;
        this.defaultValue = defaultValue;
    }

    /**
     * Whether this template parameter is required, assuming required unless explicitly set to false.
     * <p>
     * Note that this does not resolve property placeholders; use
     * {@code CamelContextHelper.parseBoolean(camelContext, getRequired())} where a
     * {@link org.apache.camel.CamelContext} is available.
     */
    public boolean isRequired() {
        // assumed to be required if not set explicit to false
        return required == null || !"false".equalsIgnoreCase(required);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRequired() {
        return required;
    }

    public void setRequired(String required) {
        this.required = required;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

}

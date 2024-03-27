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
package org.apache.camel.model.rest;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.model.OptionalIdentifiedDefinition;
import org.apache.camel.spi.Metadata;

/**
 * To use OpenApi as contract-first with Camel Rest DSL.
 */
@Metadata(label = "rest")
@XmlRootElement(name = "openApi")
@XmlAccessorType(XmlAccessType.FIELD)
public class OpenApiDefinition extends OptionalIdentifiedDefinition<OpenApiDefinition> {

    @XmlTransient
    private RestDefinition rest;

    @XmlAttribute(required = true)
    private String specification;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
    private String disabled;

    @Override
    public String getShortName() {
        return "openApi";
    }

    @Override
    public String getLabel() {
        return "openApi";
    }

    public String getSpecification() {
        return specification;
    }

    public void setSpecification(String specification) {
        this.specification = specification;
    }

    public String getDisabled() {
        return disabled;
    }

    /**
     * Whether to disable all the REST services from the OpenAPI contract from the route during build time. Once an REST
     * service has been disabled then it cannot be enabled later at runtime.
     */
    public void setDisabled(String disabled) {
        this.disabled = disabled;
    }

    // Fluent API
    // -------------------------------------------------------------------------

    public RestDefinition specification(String specification) {
        this.specification = specification;
        return rest;
    }

}

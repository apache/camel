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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.spi.Metadata;

/**
 * To specify the rest security definitions using Swagger.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public abstract class RestSecurityDefinition {

    @XmlTransient
    RestDefinition rest;

    @XmlAttribute(required = true)
    @Metadata(required = true)
    private String key;

    @XmlAttribute
    private String description;

    public RestSecurityDefinition() {
    }

    public RestSecurityDefinition(RestDefinition rest) {
        this.rest = rest;
    }

    /**
     * Ends the configuration of this security
     */
    public RestDefinition endSecurityDefinition() {
        rest.getSecurityDefinitions().getSecurityDefinitions().add(this);
        return rest;
    }

    public String getKey() {
        return key;
    }

    /**
     * Key used to refer to this security definition
     */
    public void setKey(String key) {
        this.key = key;
    }

    public String getDescription() {
        return description;
    }

    /**
     * A short description for security scheme.
     */
    public void setDescription(String description) {
        this.description = description;
    }

}

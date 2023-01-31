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
 * An input parameter of a route template.
 */
@Metadata(label = "configuration")
@XmlRootElement(name = "templatedRouteParameter")
@XmlAccessorType(XmlAccessType.FIELD)
public class TemplatedRouteParameterDefinition {

    @XmlAttribute(required = true)
    private String name;
    @XmlAttribute(required = true)
    private String value;

    public TemplatedRouteParameterDefinition() {
    }

    public TemplatedRouteParameterDefinition(String name, String value) {
        this.name = name;
        this.value = value;
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

    public String getValue() {
        return value;
    }

    /**
     * The value of the parameter.
     */
    public void setValue(String value) {
        this.value = value;
    }

}

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
package org.apache.camel.model.rest;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.spi.Metadata;

// TODO: rename to Definition as this is what this is
// TODO: Do not set default values, but infer those
// TODO: add javadoc on the setter methods
// TODO: add @Metadata to define the default values
// TODO: add required=true if its required (such as name and paramType I would assume)

@Metadata(label = "rest")
@XmlRootElement(name = "param")
@XmlAccessorType(XmlAccessType.FIELD)
public class RestOperationParam {
    @XmlAttribute
    RestParamType paramType = RestParamType.query;

    @XmlAttribute
    String name;

    @XmlAttribute
    String description = "";

    @XmlAttribute
    String defaultValue = "";

    @XmlAttribute
    Boolean required = true;

    @XmlAttribute
    Boolean allowMultiple = false;

    @XmlAttribute
    String dataType = "string";

    @XmlElementWrapper(name = "allowableValues")
    @XmlElement(name = "value")
    List<String> allowableValues = new ArrayList<String>();

    @XmlAttribute
    String paramAccess;

    public RestOperationParam() {
    }

    public RestParamType getParamType() {
        return paramType;
    }

    public void setParamType(RestParamType paramType) {
        this.paramType = paramType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    public Boolean getAllowMultiple() {
        return allowMultiple;
    }

    public void setAllowMultiple(Boolean allowMultiple) {
        this.allowMultiple = allowMultiple;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public List<String> getAllowableValues() {
        return allowableValues;
    }

    public void setAllowableValues(List<String> allowableValues) {
        this.allowableValues = allowableValues;
    }

    public String getParamAccess() {
        return paramAccess;
    }

    public void setParamAccess(String paramAccess) {
        this.paramAccess = paramAccess;
    }
}

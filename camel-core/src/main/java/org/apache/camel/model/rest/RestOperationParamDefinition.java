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
import javax.xml.bind.annotation.XmlTransient;


import org.apache.camel.spi.Metadata;

/**
 * This maps to the Swagger Parameter Object.
 * see com.wordnik.swagger.model.Parameter
 * and https://github.com/swagger-api/swagger-spec/blob/master/versions/1.2.md#524-parameter-object.
 */
@Metadata(label = "rest")
@XmlRootElement(name = "param")
@XmlAccessorType(XmlAccessType.FIELD)
public class RestOperationParamDefinition {

    @XmlTransient
    private VerbDefinition verb;

    @XmlAttribute
    @Metadata(required = "true")
    RestParamType paramType;

    @XmlAttribute
    @Metadata(required = "true")
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
    @Metadata(defaultValue = "string")
    String dataType;

    @XmlElementWrapper(name = "allowableValues")
    @XmlElement(name = "value")
    List<String> allowableValues;

    @XmlAttribute
    String paramAccess;


    public RestOperationParamDefinition(VerbDefinition verb) {
        this.verb = verb;
    }

    public RestOperationParamDefinition() {
    }

    public RestParamType getParamType() {
        if (paramType != null)
            return paramType;
        return RestParamType.path;
    }
    /**
     * Sets the Swagger Parameter type.
     */
    public void setParamType(RestParamType paramType) {
        this.paramType = paramType;
    }

    public String getName() {
        return name;
    }
    /**
     * Sets the Swagger Parameter name.
     */
    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Sets the Swagger Parameter description.
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Sets the Swagger Parameter default value.
     */
    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public Boolean getRequired() {
        return required;
    }

    /**
     * Sets the Swagger Parameter required flag.
     */
    public void setRequired(Boolean required) {
        this.required = required;
    }

    public Boolean getAllowMultiple() {
        return allowMultiple;
    }

    /**
     * Sets the Swagger Parameter allowMultiple flag.
     */
    public void setAllowMultiple(Boolean allowMultiple) {
        this.allowMultiple = allowMultiple;
    }

    public String getDataType() {
        if(dataType!=null) {
            return dataType;
        }
        return "string";
    }

    /**
     * Sets the Swagger Parameter data type.
     */
    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public List<String> getAllowableValues() {
        if (allowableValues != null) {
            return allowableValues;
        }

        return new ArrayList<String>();
    }

    /**
     * Sets the Swagger Parameter alist of allowable values.
     */
    public void setAllowableValues(List<String> allowableValues) {
        this.allowableValues = allowableValues;
    }

    public String getParamAccess() {
        return paramAccess;
    }

    /**
     * Sets the Swagger Parameter paramAccess flag.
     */
    public void setParamAccess(String paramAccess) {
        this.paramAccess = paramAccess;
    }

    public RestOperationParamDefinition name(String name) {
        setName(name);
        return this;
    }

    public RestOperationParamDefinition description(String name) {
        setDescription(name);
        return this;
    }

    public RestOperationParamDefinition defaultValue(String name) {
        setDefaultValue(name);
        return this;
    }

    public RestOperationParamDefinition required(Boolean required) {
        setRequired(required);
        return this;
    }

    public RestOperationParamDefinition allowMultiple(Boolean allowMultiple) {
        setAllowMultiple(allowMultiple);
        return this;
    }

    public RestOperationParamDefinition dataType(String type) {
        setDataType(type);
        return this;
    }

    public RestOperationParamDefinition allowableValues(List<String> allowableValues) {
        setAllowableValues(allowableValues);
        return this;
    }

    public RestOperationParamDefinition type(RestParamType type) {
        setParamType(type);
        return this;
    }

    public RestOperationParamDefinition paramAccess(String paramAccess) {
        setParamAccess(paramAccess);
        return this;
    }

    public RestDefinition endParam() {
        verb.getParams().add(this);
        return verb.getRest();
    }

}

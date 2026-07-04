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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.model.ValueDefinition;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DslProperty;
import org.apache.camel.util.StringHelper;

/**
 * To specify the rest operation parameters.
 */
@Metadata(label = "rest",
          description = "Defines a parameter for a REST operation, specifying its type, name, data type, and whether it is required")
@XmlRootElement(name = "param")
@XmlAccessorType(XmlAccessType.FIELD)
public class ParamDefinition {

    @XmlTransient
    private VerbDefinition verb;

    @XmlAttribute(required = true)
    @Metadata(description = "The parameter name.", required = true)
    private String name;
    @XmlAttribute(required = true)
    @Metadata(description = "Sets the parameter type such as body, form, header, path, or query.",
              defaultValue = "path")
    private RestParamType type;
    @XmlAttribute
    @Metadata(description = "Description of the parameter.")
    private String description;
    @XmlAttribute
    @Metadata(description = "The default value of the parameter.")
    private String defaultValue;
    @XmlAttribute
    @Metadata(description = "Sets the parameter required flag.",
              defaultValue = "true")
    private Boolean required;
    @XmlAttribute
    @Metadata(description = "Sets the parameter collection format.",
              defaultValue = "csv")
    private CollectionFormat collectionFormat;
    @XmlAttribute
    @Metadata(description = "Sets the parameter array type. Required if data type is array. Describes the type of items in the array.",
              defaultValue = "string")
    private String arrayType;
    @XmlAttribute
    @Metadata(description = "Sets the parameter data type such as string, integer, or boolean.",
              defaultValue = "string")
    private String dataType;
    @XmlAttribute
    @Metadata(description = "The data format of the parameter such as binary, date, date-time, or password.")
    private String dataFormat;
    @XmlElementWrapper(name = "allowableValues")
    @XmlElement(name = "value") // name = value due to camel-spring-xml
    @DslProperty(name = "allowableValues") // yaml-dsl
    @Metadata(description = "List of allowable values for the parameter (enum).")
    private List<ValueDefinition> allowableValues;
    @XmlElement(name = "examples")
    @Metadata(description = "Examples of the parameter.")
    private List<RestPropertyDefinition> examples;

    public ParamDefinition() {
    }

    public ParamDefinition(VerbDefinition verb) {
        this.verb = verb;
    }

    public RestParamType getType() {
        return type != null ? type : RestParamType.path;
    }

    public void setType(RestParamType type) {
        this.type = type;
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
        return required != null ? required : true;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    public CollectionFormat getCollectionFormat() {
        return collectionFormat;
    }

    public void setCollectionFormat(CollectionFormat collectionFormat) {
        this.collectionFormat = collectionFormat;
    }

    public String getArrayType() {
        return arrayType;
    }

    public void setArrayType(String arrayType) {
        this.arrayType = arrayType;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getDataFormat() {
        return dataFormat;
    }

    public void setDataFormat(String dataFormat) {
        this.dataFormat = dataFormat;
    }

    public List<ValueDefinition> getAllowableValues() {
        return allowableValues;
    }

    public void setAllowableValues(List<ValueDefinition> allowableValues) {
        this.allowableValues = allowableValues;
    }

    public List<RestPropertyDefinition> getExamples() {
        return examples;
    }

    public void setExamples(List<RestPropertyDefinition> examples) {
        this.examples = examples;
    }

    /**
     * Name of the parameter.
     * <p/>
     * This option is mandatory.
     */
    public ParamDefinition name(String name) {
        setName(name);
        return this;
    }

    /**
     * Description of the parameter.
     */
    public ParamDefinition description(String name) {
        setDescription(name);
        return this;
    }

    /**
     * The default value of the parameter.
     */
    public ParamDefinition defaultValue(String name) {
        setDefaultValue(name);
        return this;
    }

    /**
     * Whether the parameter is required
     */
    public ParamDefinition required(Boolean required) {
        setRequired(required);
        return this;
    }

    /**
     * Sets the collection format.
     */
    public ParamDefinition collectionFormat(CollectionFormat collectionFormat) {
        setCollectionFormat(collectionFormat);
        return this;
    }

    /**
     * The data type of the array data type
     */
    public ParamDefinition arrayType(String arrayType) {
        setArrayType(arrayType);
        return this;
    }

    /**
     * The data type of the parameter such as <tt>string</tt>, <tt>integer</tt>, <tt>boolean</tt>
     */
    public ParamDefinition dataType(String type) {
        setDataType(type);
        return this;
    }

    /**
     * The data format of the parameter such as <tt>binary</tt>, <tt>date</tt>, <tt>date-time</tt>, <tt>password</tt>.
     * The format is usually derived from the dataType alone. However you can set this option for more fine grained
     * control of the format in use.
     */
    public ParamDefinition dataFormat(String type) {
        setDataFormat(type);
        return this;
    }

    /**
     * Allowed values of the parameter when its an enum type
     */
    public ParamDefinition allowableValues(List<String> allowableValues) {
        List<ValueDefinition> list = new ArrayList<>();
        for (String av : allowableValues) {
            list.add(new ValueDefinition(av));
        }
        setAllowableValues(list);
        return this;
    }

    /**
     * Allowed values of the parameter when its an enum type
     */
    public ParamDefinition allowableValues(String... allowableValues) {
        List<ValueDefinition> list = new ArrayList<>();
        for (String av : allowableValues) {
            list.add(new ValueDefinition(av));
        }
        setAllowableValues(list);
        return this;
    }

    /**
     * Allowed values of the parameter when its an enum type
     */
    public ParamDefinition allowableValues(String allowableValues) {
        List<ValueDefinition> list = new ArrayList<>();
        for (String av : allowableValues.split(",")) {
            list.add(new ValueDefinition(av));
        }
        setAllowableValues(list);
        return this;
    }

    /**
     * The parameter type such as body, form, header, path, query
     */
    public ParamDefinition type(RestParamType type) {
        setType(type);
        return this;
    }

    /**
     * Adds a body example with the given content-type
     */
    public ParamDefinition example(String contentType, String example) {
        if (examples == null) {
            examples = new ArrayList<>();
        }
        examples.add(new RestPropertyDefinition(contentType, example));
        return this;
    }

    /**
     * Adds a single example
     */
    public ParamDefinition example(String example) {
        if (examples == null) {
            examples = new ArrayList<>();
        }
        examples.add(new RestPropertyDefinition("", example));
        return this;
    }

    /**
     * Ends the configuration of this parameter
     */
    public RestDefinition endParam() {
        // name is mandatory
        StringHelper.notEmpty(name, "name");
        verb.getParams().add(this);
        return verb.getRest();
    }

    public List<String> getAllowableValuesAsStringList() {
        if (allowableValues == null) {
            return Collections.emptyList();
        } else {
            List<String> answer = new ArrayList<>();
            for (ValueDefinition v : allowableValues) {
                answer.add(v.getValue());
            }
            return answer;
        }
    }

    public String getAllowableValuesAsCommaString() {
        return String.join(",", getAllowableValuesAsStringList());
    }

}

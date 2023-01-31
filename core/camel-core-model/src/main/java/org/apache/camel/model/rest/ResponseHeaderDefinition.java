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
 * To specify the rest operation response headers.
 */
@Metadata(label = "rest")
@XmlRootElement(name = "responseHeader")
@XmlAccessorType(XmlAccessType.FIELD)
public class ResponseHeaderDefinition {

    @XmlTransient
    private ResponseMessageDefinition response;

    @XmlAttribute(required = true)
    private String name;
    @XmlAttribute
    private String description;
    @XmlAttribute
    @Metadata(defaultValue = "csv")
    private CollectionFormat collectionFormat;
    @XmlAttribute
    @Metadata(defaultValue = "string")
    private String arrayType;
    @XmlAttribute
    @Metadata(defaultValue = "string")
    private String dataType;
    @XmlAttribute
    private String dataFormat;
    @XmlElementWrapper(name = "allowableValues")
    @XmlElement(name = "value") // name = value due to camel-spring-xml
    @DslProperty(name = "allowableValues") // yaml-dsl
    private List<ValueDefinition> allowableValues;
    @XmlAttribute
    private String example;

    public ResponseHeaderDefinition(ResponseMessageDefinition response) {
        this();
        this.response = response;
    }

    public ResponseHeaderDefinition() {
        this.collectionFormat = CollectionFormat.csv;
        this.arrayType = "string";
        this.dataType = "string";
    }

    /**
     * Ends the configuration of this response message
     */
    public ResponseMessageDefinition endResponseHeader() {
        return response;
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

    public CollectionFormat getCollectionFormat() {
        return collectionFormat;
    }

    /**
     * Sets the parameter collection format.
     */
    public void setCollectionFormat(CollectionFormat collectionFormat) {
        this.collectionFormat = collectionFormat;
    }

    public String getArrayType() {
        return arrayType;
    }

    /**
     * Sets the parameter array type. Required if data type is "array". Describes the type of items in the array.
     */
    public void setArrayType(String arrayType) {
        this.arrayType = arrayType;
    }

    public String getDataType() {
        return dataType;
    }

    /**
     * Sets the header data type.
     */
    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getDataFormat() {
        return dataFormat;
    }

    /**
     * Sets the parameter data format.
     */
    public void setDataFormat(String dataFormat) {
        this.dataFormat = dataFormat;
    }

    public List<ValueDefinition> getAllowableValues() {
        return allowableValues;
    }

    public String getExample() {
        return example;
    }

    /**
     * Sets the example
     */
    public void setExample(String example) {
        this.example = example;
    }

    /**
     * Sets the parameter list of allowable values.
     */
    public void setAllowableValues(List<ValueDefinition> allowableValues) {
        this.allowableValues = allowableValues;
    }

    /**
     * Name of the parameter.
     * <p>
     * This option is mandatory.
     */
    public ResponseHeaderDefinition name(String name) {
        setName(name);
        return this;
    }

    /**
     * Description of the parameter.
     */
    public ResponseHeaderDefinition description(String name) {
        setDescription(name);
        return this;
    }

    /**
     * Sets the collection format.
     */
    public ResponseHeaderDefinition collectionFormat(CollectionFormat collectionFormat) {
        setCollectionFormat(collectionFormat);
        return this;
    }

    /**
     * The data type of the array data type
     */
    public ResponseHeaderDefinition arrayType(String arrayType) {
        setArrayType(arrayType);
        return this;
    }

    /**
     * The data type of the header such as <tt>string</tt>, <tt>integer</tt>, <tt>boolean</tt>
     */
    public ResponseHeaderDefinition dataType(String type) {
        setDataType(type);
        return this;
    }

    /**
     * The data format of the parameter such as <tt>binary</tt>, <tt>date</tt>, <tt>date-time</tt>, <tt>password</tt>.
     * The format is usually derived from the dataType alone. However you can set this option for more fine grained
     * control of the format in use.
     */
    public ResponseHeaderDefinition dataFormat(String type) {
        setDataFormat(type);
        return this;
    }

    /**
     * Allowed values of the header when its an enum type
     */
    public ResponseHeaderDefinition allowableValues(List<String> allowableValues) {
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
    public ResponseHeaderDefinition allowableValues(String... allowableValues) {
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
    public ResponseHeaderDefinition allowableValues(String allowableValues) {
        List<ValueDefinition> list = new ArrayList<>();
        for (String av : allowableValues.split(",")) {
            list.add(new ValueDefinition(av));
        }
        setAllowableValues(list);
        return this;
    }

    /**
     * Sets an example of this header.
     */
    public ResponseHeaderDefinition example(String example) {
        setExample(example);
        return this;
    }

    /**
     * Ends the configuration of this header
     */
    public ResponseMessageDefinition endHeader() {
        // name and type is mandatory
        StringHelper.notEmpty(name, "name");
        StringHelper.notEmpty(dataType, "dataType");
        return response;
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

}

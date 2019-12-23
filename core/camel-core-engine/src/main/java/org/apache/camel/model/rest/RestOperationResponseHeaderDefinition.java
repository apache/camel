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
import java.util.Arrays;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.spi.Metadata;
import org.apache.camel.util.StringHelper;

/**
 * To specify the rest operation response headers using Swagger.
 * <p>
 * This maps to the Swagger Response Header Object.
 */
@Metadata(label = "rest")
@XmlRootElement(name = "responseHeader")
@XmlAccessorType(XmlAccessType.FIELD)
public class RestOperationResponseHeaderDefinition {

    @XmlTransient
    private RestOperationResponseMsgDefinition response;

    @XmlAttribute(required = true)
    private String name;

    @XmlAttribute
    @Metadata(defaultValue = "")
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
    @XmlElement(name = "value")
    private List<String> allowableValues;

    @XmlAttribute
    private String example;

    public RestOperationResponseHeaderDefinition(RestOperationResponseMsgDefinition response) {
        this();
        this.response = response;
    }

    public RestOperationResponseHeaderDefinition() {
        this.collectionFormat = CollectionFormat.csv;
        this.arrayType = "string";
        this.dataType = "string";
    }

    /**
     * Ends the configuration of this response message
     */
    public RestOperationResponseMsgDefinition endResponseHeader() {
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
     * Sets the Swagger Parameter collection format.
     */
    public void setCollectionFormat(CollectionFormat collectionFormat) {
        this.collectionFormat = collectionFormat;
    }

    public String getArrayType() {
        return arrayType;
    }

    /**
     * Sets the Swagger Parameter array type. Required if data type is "array".
     * Describes the type of items in the array.
     */
    public void setArrayType(String arrayType) {
        this.arrayType = arrayType;
    }

    public String getDataType() {
        return dataType;
    }

    /**
     * Sets the Swagger header data type.
     */
    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getDataFormat() {
        return dataFormat;
    }

    /**
     * Sets the Swagger Parameter data format.
     */
    public void setDataFormat(String dataFormat) {
        this.dataFormat = dataFormat;
    }

    public List<String> getAllowableValues() {
        if (allowableValues != null) {
            return allowableValues;
        }

        return new ArrayList<>();
    }

    public String getExample() {
        return example;
    }

    /**
     * Sets the Swagger example
     */
    public void setExample(String example) {
        this.example = example;
    }

    /**
     * Sets the Swagger Parameter list of allowable values.
     */
    public void setAllowableValues(List<String> allowableValues) {
        this.allowableValues = allowableValues;
    }

    /**
     * Name of the parameter.
     * <p>
     * This option is mandatory.
     */
    public RestOperationResponseHeaderDefinition name(String name) {
        setName(name);
        return this;
    }

    /**
     * Description of the parameter.
     */
    public RestOperationResponseHeaderDefinition description(String name) {
        setDescription(name);
        return this;
    }

    /**
     * Sets the collection format.
     */
    public RestOperationResponseHeaderDefinition collectionFormat(CollectionFormat collectionFormat) {
        setCollectionFormat(collectionFormat);
        return this;
    }

    /**
     * The data type of the array data type
     */
    public RestOperationResponseHeaderDefinition arrayType(String arrayType) {
        setArrayType(arrayType);
        return this;
    }

    /**
     * The data type of the header such as <tt>string</tt>, <tt>integer</tt>,
     * <tt>boolean</tt>
     */
    public RestOperationResponseHeaderDefinition dataType(String type) {
        setDataType(type);
        return this;
    }

    /**
     * The data format of the parameter such as <tt>binary</tt>, <tt>date</tt>,
     * <tt>date-time</tt>, <tt>password</tt>. The format is usually derived from
     * the dataType alone. However you can set this option for more fine grained
     * control of the format in use.
     */
    public RestOperationResponseHeaderDefinition dataFormat(String type) {
        setDataFormat(type);
        return this;
    }

    /**
     * Allowed values of the header when its an enum type
     */
    public RestOperationResponseHeaderDefinition allowableValues(List<String> allowableValues) {
        setAllowableValues(allowableValues);
        return this;
    }

    /**
     * Allowed values of the parameter when its an enum type
     */
    public RestOperationResponseHeaderDefinition allowableValues(String... allowableValues) {
        setAllowableValues(Arrays.asList(allowableValues));
        return this;
    }

    /**
     * Sets an example of this header.
     */
    public RestOperationResponseHeaderDefinition example(String example) {
        setExample(example);
        return this;
    }

    /**
     * Ends the configuration of this header
     */
    public RestOperationResponseMsgDefinition endHeader() {
        // name and type is mandatory
        StringHelper.notEmpty(name, "name");
        StringHelper.notEmpty(dataType, "dataType");
        return response;
    }

}
